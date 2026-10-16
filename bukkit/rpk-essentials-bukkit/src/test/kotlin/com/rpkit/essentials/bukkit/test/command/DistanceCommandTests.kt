/*
 * Copyright 2021 Ren Binden
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rpkit.essentials.bukkit.test.command

import com.rpkit.characters.bukkit.character.RPKCharacterService
import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.essentials.bukkit.RPKEssentialsBukkit
import com.rpkit.essentials.bukkit.command.DistanceCommand
import com.rpkit.essentials.bukkit.messages.EssentialsMessages
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfile
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfileService
import com.rpkit.tracking.bukkit.tracking.RPKTrackingService
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.entity.Player

private const val DISTANCE_PERMISSION = "rpkit.essentials.command.distance"
private const val TARGET_NAME = "target"

/**
 * Every message resolves to its own key, so the assertions name the exact `messages.yml` key the
 * command looked up. A key missing from `messages.yml` would therefore show up as a wrong key here
 * rather than being hidden behind a stubbed string.
 */
private fun plugin(server: Server): RPKEssentialsBukkit {
    val messages = mockk<EssentialsMessages>()
    every { messages[any()] } answers { firstArg() }
    val plugin = mockk<RPKEssentialsBukkit>()
    every { plugin.messages } returns messages
    every { plugin.server } returns server
    return plugin
}

/**
 * A player sender that records every line sent to it, so assertions can be made about the exact
 * lines and their order rather than about individual `sendMessage` calls.
 */
private class RecordingPlayer(world: World) {
    val sentLines = mutableListOf<String>()
    val player = mockk<Player>()

    init {
        every { player.hasPermission(DISTANCE_PERMISSION) } returns true
        every { player.world } returns world
        every { player.sendMessage(any<String>()) } answers {
            sentLines += firstArg<String>()
        }
    }
}

private fun server(target: Player?): Server {
    val server = mockk<Server>()
    every { server.getPlayer(TARGET_NAME) } returns target
    return server
}

private fun target(world: World): Player {
    val target = mockk<Player>()
    every { target.world } returns world
    return target
}

private fun runDistanceCommand(plugin: RPKEssentialsBukkit, sender: Player): Boolean =
    DistanceCommand(plugin).onCommand(sender, mockk<Command>(), "distance", arrayOf(TARGET_NAME))

class DistanceCommandTests : WordSpec({

    "the distance command" should {
        "report a target that is not online with the declared invalid-player message" {
            val world = mockk<World>()
            val recording = RecordingPlayer(world)
            val plugin = plugin(server(target = null))

            runDistanceCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf("distance-invalid-player")
        }

        "report a target with no active character with the declared other-player message" {
            val world = mockk<World>()
            val recording = RecordingPlayer(world)
            val target = target(world)
            val plugin = plugin(server(target))
            val minecraftProfile = mockk<RPKMinecraftProfile>()
            val minecraftProfileService = mockk<RPKMinecraftProfileService>()
            every { minecraftProfileService.getPreloadedMinecraftProfile(target) } returns minecraftProfile
            val characterService = mockk<RPKCharacterService>()
            every { characterService.getPreloadedActiveCharacter(minecraftProfile) } returns null
            val servicesDelegate = mockk<ServicesDelegate>()
            every { servicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
            every { servicesDelegate[RPKCharacterService::class.java] } returns characterService
            every { servicesDelegate[RPKTrackingService::class.java] } returns mockk()
            Services.delegate = servicesDelegate

            runDistanceCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf("no-character-other")
        }
    }

})
