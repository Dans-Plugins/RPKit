/*
 * Copyright 2022 Ren Binden
 *
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

package com.rpkit.professions.bukkit.test.command.profession.experience

import com.rpkit.characters.bukkit.character.RPKCharacterService
import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfile
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfileService
import com.rpkit.professions.bukkit.RPKProfessionsBukkit
import com.rpkit.professions.bukkit.command.profession.experience.ProfessionExperienceAddCommand
import com.rpkit.professions.bukkit.command.profession.experience.ProfessionExperienceRemoveCommand
import com.rpkit.professions.bukkit.command.profession.experience.ProfessionExperienceSetCommand
import com.rpkit.professions.bukkit.messages.ProfessionsMessages
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.Server
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.entity.Player

private const val TARGET_NAME = "TargetPlayer"

/**
 * Resolves message keys the way BukkitMessages does: a read with no variables returns the template
 * as written, so an unfilled `${player}` token stays visible in the sent line.
 */
private fun messages(): ProfessionsMessages {
    val templates = mapOf(
        "no-minecraft-profile-other" to "\${player} does not have a Minecraft profile.",
        "no-character-other" to "\${player} does not currently have an active character."
    )
    val messages = mockk<ProfessionsMessages>()
    every { messages[any()] } answers { templates.getValue(firstArg()) }
    every { messages[any(), any()] } answers {
        secondArg<Map<String, String>>().entries.fold(templates.getValue(firstArg())) { line, (key, value) ->
            line.replace("\${$key}", value)
        }
    }
    return messages
}

private fun plugin(target: Player): RPKProfessionsBukkit {
    val server = mockk<Server>()
    every { server.getPlayer(TARGET_NAME) } returns target
    val plugin = mockk<RPKProfessionsBukkit>()
    every { plugin.messages } returns messages()
    every { plugin.server } returns server
    return plugin
}

private fun target(): Player {
    val target = mockk<Player>()
    every { target.name } returns TARGET_NAME
    return target
}

/**
 * A sender allowed to target other players, recording every line sent to it.
 */
private class RecordingSender {
    val sentLines = mutableListOf<String>()
    val player = mockk<Player>()

    init {
        every { player.hasPermission(any<String>()) } returns true
        every { player.sendMessage(any<String>()) } answers {
            sentLines += firstArg<String>()
        }
    }
}

private fun installServices(
    minecraftProfileService: RPKMinecraftProfileService,
    characterService: RPKCharacterService = mockk()
) {
    val servicesDelegate = mockk<ServicesDelegate>()
    every { servicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
    every { servicesDelegate[RPKCharacterService::class.java] } returns characterService
    Services.delegate = servicesDelegate
}

class ProfessionExperienceCommandsTests : WordSpec({

    val commands: List<Pair<String, (RPKProfessionsBukkit) -> CommandExecutor>> = listOf(
        "add" to ::ProfessionExperienceAddCommand,
        "remove" to ::ProfessionExperienceRemoveCommand,
        "set" to ::ProfessionExperienceSetCommand
    )

    commands.forEach { (name, createCommand) ->
        "the profession experience $name command" should {
            "name the target player when the target has no Minecraft profile" {
                val sender = RecordingSender()
                val target = target()
                val minecraftProfileService = mockk<RPKMinecraftProfileService>()
                every { minecraftProfileService.getPreloadedMinecraftProfile(target) } returns null
                installServices(minecraftProfileService)

                createCommand(plugin(target)).onCommand(
                    sender.player,
                    mockk<Command>(),
                    "experience",
                    arrayOf(TARGET_NAME, "mining", "5")
                )

                sender.sentLines shouldBe listOf("$TARGET_NAME does not have a Minecraft profile.")
            }

            "name the target player when the target has no active character" {
                val sender = RecordingSender()
                val target = target()
                val minecraftProfile = mockk<RPKMinecraftProfile>()
                val minecraftProfileService = mockk<RPKMinecraftProfileService>()
                every { minecraftProfileService.getPreloadedMinecraftProfile(target) } returns minecraftProfile
                val characterService = mockk<RPKCharacterService>()
                every { characterService.getPreloadedActiveCharacter(minecraftProfile) } returns null
                installServices(minecraftProfileService, characterService)

                createCommand(plugin(target)).onCommand(
                    sender.player,
                    mockk<Command>(),
                    "experience",
                    arrayOf(TARGET_NAME, "mining", "5")
                )

                sender.sentLines shouldBe listOf("$TARGET_NAME does not currently have an active character.")
            }
        }
    }

})
