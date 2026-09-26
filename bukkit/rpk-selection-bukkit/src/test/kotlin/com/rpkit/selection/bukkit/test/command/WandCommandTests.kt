/*
 * Copyright 2020 Ren Binden
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

package com.rpkit.selection.bukkit.test.command

import com.rpkit.selection.bukkit.RPKSelectionBukkit
import com.rpkit.selection.bukkit.command.WandCommand
import com.rpkit.selection.bukkit.messages.SelectionMessages
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.bukkit.command.Command
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory

private const val WAND_PERMISSION = "rpkit.selection.command.wand"

private const val NO_PERMISSION_WAND = "no permission to obtain a wand"
private const val NOT_FROM_CONSOLE = "not from console"
private const val WAND_VALID = "here's a wand"
private const val NO_WAND_ITEM = "no wand item configured"
private const val WAND_INVENTORY_FULL = "inventory full"

/**
 * The stubbed messages are deliberately free of colour codes: these tests are about which line is
 * sent on which branch, not about how colour codes are rendered. The wand item is whatever the
 * config returns, so the assertions can check the exact instance handed to the inventory.
 */
private fun plugin(wandItem: ItemStack? = mockk()): RPKSelectionBukkit {
    val messages = mockk<SelectionMessages>()
    every { messages["no-permission-wand"] } returns NO_PERMISSION_WAND
    every { messages["not-from-console"] } returns NOT_FROM_CONSOLE
    every { messages["wand-valid"] } returns WAND_VALID
    every { messages["no-wand-item"] } returns NO_WAND_ITEM
    every { messages["wand-inventory-full"] } returns WAND_INVENTORY_FULL
    val config = mockk<FileConfiguration>()
    every { config.getItemStack("wand-item") } returns wandItem
    val plugin = mockk<RPKSelectionBukkit>()
    every { plugin.messages } returns messages
    every { plugin.config } returns config
    return plugin
}

/**
 * A player sender that records every line sent to it and exposes its inventory, so assertions can
 * be made about both the exact lines sent and whether an item was added.
 */
private class RecordingPlayer {
    val sentLines = mutableListOf<String>()
    val inventory = mockk<PlayerInventory>()
    val player = mockk<Player>()
}

private fun recordingPlayer(hasPermission: Boolean = true): RecordingPlayer {
    val recording = RecordingPlayer()
    every { recording.player.hasPermission(WAND_PERMISSION) } returns hasPermission
    every { recording.player.inventory } returns recording.inventory
    every { recording.inventory.addItem(any()) } returns hashMapOf()
    every { recording.player.sendMessage(any<String>()) } answers {
        recording.sentLines += firstArg<String>()
    }
    return recording
}

private fun runWandCommand(plugin: RPKSelectionBukkit, sender: Player): Boolean =
    WandCommand(plugin).onCommand(sender, mockk<Command>(), "wand", emptyArray())

class WandCommandTests : WordSpec({

    "the wand command" should {
        "reject a sender without the wand permission" {
            val recording = recordingPlayer(hasPermission = false)
            val plugin = plugin()

            runWandCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(NO_PERMISSION_WAND)
            verify(exactly = 0) { recording.inventory.addItem(any()) }
        }

        "reject a sender that is not a player" {
            val sender = mockk<ConsoleCommandSender>()
            every { sender.hasPermission(WAND_PERMISSION) } returns true
            every { sender.sendMessage(any<String>()) } just runs
            val plugin = plugin()

            WandCommand(plugin).onCommand(sender, mockk<Command>(), "wand", emptyArray()) shouldBe true

            verify(exactly = 1) { sender.sendMessage(NOT_FROM_CONSOLE) }
        }

        "give the configured wand item to the sender and confirm it" {
            val recording = recordingPlayer()
            val wandItem = mockk<ItemStack>()
            val plugin = plugin(wandItem = wandItem)

            runWandCommand(plugin, recording.player) shouldBe true

            verify(exactly = 1) { recording.inventory.addItem(wandItem) }
            recording.sentLines shouldBe listOf(WAND_VALID)
        }

        "tell the sender the wand item is missing when the config has none" {
            val recording = recordingPlayer()
            val plugin = plugin(wandItem = null)

            runWandCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(NO_WAND_ITEM)
            verify(exactly = 0) { recording.inventory.addItem(any()) }
        }

        "tell the sender their inventory is full when the wand does not fit" {
            val recording = recordingPlayer()
            val wandItem = mockk<ItemStack>()
            every { recording.inventory.addItem(wandItem) } returns hashMapOf(0 to wandItem)
            val plugin = plugin(wandItem = wandItem)

            runWandCommand(plugin, recording.player) shouldBe true

            verify(exactly = 1) { recording.inventory.addItem(wandItem) }
            recording.sentLines shouldBe listOf(WAND_INVENTORY_FULL)
        }
    }

})
