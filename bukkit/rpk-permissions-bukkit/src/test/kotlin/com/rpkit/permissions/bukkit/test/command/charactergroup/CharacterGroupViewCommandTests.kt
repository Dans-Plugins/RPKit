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

package com.rpkit.permissions.bukkit.test.command.charactergroup

import com.rpkit.permissions.bukkit.RPKPermissionsBukkit
import com.rpkit.permissions.bukkit.command.charactergroup.CharacterGroupViewCommand
import com.rpkit.permissions.bukkit.messages.PermissionsMessages
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private const val VIEW_PERMISSION = "rpkit.permissions.command.charactergroup.view"

private fun plugin(): RPKPermissionsBukkit {
    val messages = mockk<PermissionsMessages>()
    every { messages.notFromConsole } returns "not from console"
    every { messages.noPermissionCharacterGroupView } returns "no permission"
    every { messages.characterGroupViewUsage } returns "usage"
    val plugin = mockk<RPKPermissionsBukkit>()
    every { plugin.messages } returns messages
    return plugin
}

/**
 * A player holding exactly the given permission nodes, recording every line sent to them.
 * Any other node is refused, so a check against a node that is not in [permissions] is observable.
 */
private fun player(sentMessages: MutableList<String>, vararg permissions: String): Player {
    val player = mockk<Player>()
    every { player.hasPermission(any<String>()) } answers { firstArg<String>() in permissions }
    every { player.sendMessage(any<String>()) } answers { sentMessages += firstArg<String>() }
    return player
}

class CharacterGroupViewCommandTests : WordSpec({

    "the charactergroup view command" should {
        "accept a player holding the charactergroup view permission declared in plugin.yml" {
            val sentMessages = mutableListOf<String>()
            val sender = player(sentMessages, VIEW_PERMISSION)

            val characterGroupViewCommand = CharacterGroupViewCommand(plugin())
            characterGroupViewCommand.onCommand(sender, mockk<Command>(), "charactergroup", emptyArray()) shouldBe true

            // With no arguments the command gets as far as the usage check, which is only
            // reached once the permission check has passed.
            sentMessages shouldBe listOf("usage")
        }

        "refuse a player without the charactergroup view permission" {
            val sentMessages = mutableListOf<String>()
            val sender = player(sentMessages)

            val characterGroupViewCommand = CharacterGroupViewCommand(plugin())
            characterGroupViewCommand.onCommand(sender, mockk<Command>(), "charactergroup", emptyArray()) shouldBe true

            sentMessages shouldBe listOf("no permission")
        }

        "refuse a sender that is not a player" {
            val sentMessages = mutableListOf<String>()
            val sender = mockk<CommandSender>()
            every { sender.sendMessage(any<String>()) } answers { sentMessages += firstArg<String>() }

            val characterGroupViewCommand = CharacterGroupViewCommand(plugin())
            characterGroupViewCommand.onCommand(sender, mockk<Command>(), "charactergroup", emptyArray()) shouldBe true

            sentMessages shouldBe listOf("not from console")
        }
    }

})
