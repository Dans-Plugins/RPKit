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

package com.rpkit.professions.bukkit.test.listener

import com.rpkit.characters.bukkit.character.RPKCharacterService
import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfileService
import com.rpkit.professions.bukkit.RPKProfessionsBukkit
import com.rpkit.professions.bukkit.listener.CraftItemListener
import com.rpkit.professions.bukkit.messages.ProfessionsMessages
import com.rpkit.professions.bukkit.profession.RPKProfessionService
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.inventory.CraftItemEvent

/**
 * Keyed lookups resolve to their own key and [ProfessionsMessages.noMinecraftProfileSelf] to the
 * key `messages.yml` declares for it, so the assertions name the exact key the listener sent. A key
 * missing from `messages.yml` would therefore show up as a wrong key here rather than being hidden
 * behind a stubbed string.
 */
private fun plugin(): RPKProfessionsBukkit {
    val messages = mockk<ProfessionsMessages>()
    every { messages[any()] } answers { firstArg() }
    every { messages.noMinecraftProfileSelf } returns "no-minecraft-profile-self"
    val plugin = mockk<RPKProfessionsBukkit>()
    every { plugin.messages } returns messages
    return plugin
}

/**
 * A player that records every line sent to it, so assertions can be made about the exact lines and
 * their order rather than about individual `sendMessage` calls.
 */
private class RecordingPlayer {
    val sentLines = mutableListOf<String>()
    val player = mockk<Player>()

    init {
        every { player.gameMode } returns GameMode.SURVIVAL
        every { player.sendMessage(any<String>()) } answers {
            sentLines += firstArg<String>()
        }
    }
}

class CraftItemListenerTests : WordSpec({

    "the craft item listener" should {
        "cancel crafting and send the declared message when the player has no Minecraft profile" {
            val recording = RecordingPlayer()
            val minecraftProfileService = mockk<RPKMinecraftProfileService>()
            every { minecraftProfileService.getPreloadedMinecraftProfile(recording.player) } returns null
            val servicesDelegate = mockk<ServicesDelegate>()
            every { servicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
            every { servicesDelegate[RPKCharacterService::class.java] } returns mockk()
            every { servicesDelegate[RPKProfessionService::class.java] } returns mockk()
            Services.delegate = servicesDelegate
            val event = mockk<CraftItemEvent>()
            every { event.whoClicked } returns recording.player
            justRun { event.isCancelled = true }

            CraftItemListener(plugin()).onCraftItem(event)

            verify { event.isCancelled = true }
            recording.sentLines shouldBe listOf("no-minecraft-profile-self")
        }
    }

})
