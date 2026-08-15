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

package com.rpkit.chat.bukkit.test.command.listchatchannels

import com.rpkit.chat.bukkit.RPKChatBukkit
import com.rpkit.chat.bukkit.chatchannel.RPKChatChannel
import com.rpkit.chat.bukkit.chatchannel.RPKChatChannelName
import com.rpkit.chat.bukkit.chatchannel.RPKChatChannelService
import com.rpkit.chat.bukkit.command.listchatchannels.ListChatChannelsCommand
import com.rpkit.chat.bukkit.messages.ChatMessages
import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfile
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfileId
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfileService
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.md_5.bungee.api.chat.BaseComponent
import org.bukkit.command.Command
import org.bukkit.entity.Player
import java.awt.Color
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The template is deliberately free of colour codes: these tests are about which line is sent
 * when, not about how the colour and format codes inside a line are turned into components.
 */
private const val ITEM_TEMPLATE = "- \${channel} (\${mute})"

private fun chatChannel(name: String, listeners: CompletableFuture<List<RPKMinecraftProfile>>): RPKChatChannel {
    val chatChannel = mockk<RPKChatChannel>()
    every { chatChannel.name } returns RPKChatChannelName(name)
    every { chatChannel.color } returns Color.WHITE
    every { chatChannel.listeners } returns listeners
    return chatChannel
}

class ListChatChannelsCommandTests : WordSpec({

    "the listchatchannels command" should {
        "send the chat channels in chat channel service order regardless of the order the listener queries complete in" {
            val messages = mockk<ChatMessages>()
            every { messages["listchatchannels-title"] } returns "chat channels:"
            every { messages["listchatchannels-item", any()] } returns ITEM_TEMPLATE
            val plugin = mockk<RPKChatBukkit>()
            every { plugin.messages } returns messages

            val alphaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val betaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val gammaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val chatChannelService = mockk<RPKChatChannelService>()
            every { chatChannelService.chatChannels } returns listOf(
                chatChannel("alpha", alphaListeners),
                chatChannel("beta", betaListeners),
                chatChannel("gamma", gammaListeners)
            )

            val minecraftProfile = mockk<RPKMinecraftProfile>()
            every { minecraftProfile.id } returns RPKMinecraftProfileId(1)
            val sender = mockk<Player>()
            val senderSpigot = mockk<Player.Spigot>()
            val sentLines = mutableListOf<String>()
            every { sender.hasPermission("rpkit.chat.command.listchatchannels") } returns true
            every { sender.sendMessage(any<String>()) } just runs
            every { sender.spigot() } returns senderSpigot
            every { senderSpigot.sendMessage(*anyVararg<BaseComponent>()) } answers {
                sentLines += plainText(call.invocation.args)
            }
            val minecraftProfileService = mockk<RPKMinecraftProfileService>()
            every { minecraftProfileService.getPreloadedMinecraftProfile(sender) } returns minecraftProfile
            val testServicesDelegate = mockk<ServicesDelegate>()
            every { testServicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
            every { testServicesDelegate[RPKChatChannelService::class.java] } returns chatChannelService
            Services.delegate = testServicesDelegate

            val listChatChannelsCommand = ListChatChannelsCommand(plugin)
            listChatChannelsCommand.onCommand(sender, mockk<Command>(), "listchatchannels", emptyArray()) shouldBe true

            // Nothing may be sent until every listener query has completed, otherwise the order
            // of the lines is the order the queries finished in.
            sentLines shouldBe emptyList<String>()

            gammaListeners.complete(emptyList())
            betaListeners.complete(emptyList())
            alphaListeners.complete(emptyList())

            sentLines shouldBe listOf("- alpha (Unmute)", "- beta (Unmute)", "- gamma (Unmute)")
        }

        "pair each chat channel with its own listeners when the listener queries complete out of order" {
            val messages = mockk<ChatMessages>()
            every { messages["listchatchannels-title"] } returns "chat channels:"
            every { messages["listchatchannels-item", any()] } returns ITEM_TEMPLATE
            val plugin = mockk<RPKChatBukkit>()
            every { plugin.messages } returns messages

            val minecraftProfile = mockk<RPKMinecraftProfile>()
            every { minecraftProfile.id } returns RPKMinecraftProfileId(1)
            // The sender listens to alpha only, so alpha offers to mute and beta to unmute.
            val alphaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val betaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val chatChannelService = mockk<RPKChatChannelService>()
            every { chatChannelService.chatChannels } returns listOf(
                chatChannel("alpha", alphaListeners),
                chatChannel("beta", betaListeners)
            )

            val sender = mockk<Player>()
            val senderSpigot = mockk<Player.Spigot>()
            val sentLines = mutableListOf<String>()
            every { sender.hasPermission("rpkit.chat.command.listchatchannels") } returns true
            every { sender.sendMessage(any<String>()) } just runs
            every { sender.spigot() } returns senderSpigot
            every { senderSpigot.sendMessage(*anyVararg<BaseComponent>()) } answers {
                sentLines += plainText(call.invocation.args)
            }
            val minecraftProfileService = mockk<RPKMinecraftProfileService>()
            every { minecraftProfileService.getPreloadedMinecraftProfile(sender) } returns minecraftProfile
            val testServicesDelegate = mockk<ServicesDelegate>()
            every { testServicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
            every { testServicesDelegate[RPKChatChannelService::class.java] } returns chatChannelService
            Services.delegate = testServicesDelegate

            val listChatChannelsCommand = ListChatChannelsCommand(plugin)
            listChatChannelsCommand.onCommand(sender, mockk<Command>(), "listchatchannels", emptyArray()) shouldBe true

            betaListeners.complete(emptyList())
            alphaListeners.complete(listOf(minecraftProfile))

            sentLines shouldBe listOf("- alpha (Mute)", "- beta (Unmute)")
        }

        "send nothing but the title when there are no chat channels" {
            val messages = mockk<ChatMessages>()
            every { messages["listchatchannels-title"] } returns "chat channels:"
            val plugin = mockk<RPKChatBukkit>()
            every { plugin.messages } returns messages

            val chatChannelService = mockk<RPKChatChannelService>()
            every { chatChannelService.chatChannels } returns emptyList()

            val sender = mockk<Player>()
            val senderSpigot = mockk<Player.Spigot>()
            val sentLines = mutableListOf<String>()
            every { sender.hasPermission("rpkit.chat.command.listchatchannels") } returns true
            every { sender.sendMessage(any<String>()) } just runs
            every { sender.spigot() } returns senderSpigot
            every { senderSpigot.sendMessage(*anyVararg<BaseComponent>()) } answers {
                sentLines += plainText(call.invocation.args)
            }
            val minecraftProfileService = mockk<RPKMinecraftProfileService>()
            every { minecraftProfileService.getPreloadedMinecraftProfile(sender) } returns mockk()
            val testServicesDelegate = mockk<ServicesDelegate>()
            every { testServicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
            every { testServicesDelegate[RPKChatChannelService::class.java] } returns chatChannelService
            Services.delegate = testServicesDelegate

            val listChatChannelsCommand = ListChatChannelsCommand(plugin)
            listChatChannelsCommand.onCommand(sender, mockk<Command>(), "listchatchannels", emptyArray()) shouldBe true

            sentLines shouldBe emptyList<String>()
        }

        "skip a chat channel whose listeners query fails and still send the rest in order" {
            val messages = mockk<ChatMessages>()
            every { messages["listchatchannels-title"] } returns "chat channels:"
            every { messages["listchatchannels-item", any()] } returns ITEM_TEMPLATE
            val logger = mockk<Logger>(relaxed = true)
            val plugin = mockk<RPKChatBukkit>()
            every { plugin.messages } returns messages
            every { plugin.logger } returns logger

            val alphaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val betaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val gammaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val chatChannelService = mockk<RPKChatChannelService>()
            every { chatChannelService.chatChannels } returns listOf(
                chatChannel("alpha", alphaListeners),
                chatChannel("beta", betaListeners),
                chatChannel("gamma", gammaListeners)
            )

            val minecraftProfile = mockk<RPKMinecraftProfile>()
            every { minecraftProfile.id } returns RPKMinecraftProfileId(1)
            val sender = mockk<Player>()
            val senderSpigot = mockk<Player.Spigot>()
            val sentLines = mutableListOf<String>()
            every { sender.hasPermission("rpkit.chat.command.listchatchannels") } returns true
            every { sender.sendMessage(any<String>()) } just runs
            every { sender.spigot() } returns senderSpigot
            every { senderSpigot.sendMessage(*anyVararg<BaseComponent>()) } answers {
                sentLines += plainText(call.invocation.args)
            }
            val minecraftProfileService = mockk<RPKMinecraftProfileService>()
            every { minecraftProfileService.getPreloadedMinecraftProfile(sender) } returns minecraftProfile
            val testServicesDelegate = mockk<ServicesDelegate>()
            every { testServicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
            every { testServicesDelegate[RPKChatChannelService::class.java] } returns chatChannelService
            Services.delegate = testServicesDelegate

            val listChatChannelsCommand = ListChatChannelsCommand(plugin)
            listChatChannelsCommand.onCommand(sender, mockk<Command>(), "listchatchannels", emptyArray()) shouldBe true

            val betaFailure = RuntimeException("listeners query failed")
            gammaListeners.complete(emptyList())
            betaListeners.completeExceptionally(betaFailure)
            alphaListeners.complete(emptyList())

            // Before this fix a single failing query completed the combined future
            // exceptionally, so none of these lines was sent at all.
            sentLines shouldBe listOf("- alpha (Unmute)", "- gamma (Unmute)")
            verify {
                logger.log(Level.SEVERE, "Failed to get listeners for chat channel beta", any<Throwable>())
            }
        }

        "send nothing but the title when every listeners query fails" {
            val messages = mockk<ChatMessages>()
            every { messages["listchatchannels-title"] } returns "chat channels:"
            every { messages["listchatchannels-item", any()] } returns ITEM_TEMPLATE
            val plugin = mockk<RPKChatBukkit>()
            every { plugin.messages } returns messages
            every { plugin.logger } returns mockk(relaxed = true)

            val alphaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val betaListeners = CompletableFuture<List<RPKMinecraftProfile>>()
            val chatChannelService = mockk<RPKChatChannelService>()
            every { chatChannelService.chatChannels } returns listOf(
                chatChannel("alpha", alphaListeners),
                chatChannel("beta", betaListeners)
            )

            val sender = mockk<Player>()
            val senderSpigot = mockk<Player.Spigot>()
            val sentLines = mutableListOf<String>()
            every { sender.hasPermission("rpkit.chat.command.listchatchannels") } returns true
            every { sender.sendMessage(any<String>()) } just runs
            every { sender.spigot() } returns senderSpigot
            every { senderSpigot.sendMessage(*anyVararg<BaseComponent>()) } answers {
                sentLines += plainText(call.invocation.args)
            }
            val minecraftProfileService = mockk<RPKMinecraftProfileService>()
            every { minecraftProfileService.getPreloadedMinecraftProfile(sender) } returns mockk()
            val testServicesDelegate = mockk<ServicesDelegate>()
            every { testServicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
            every { testServicesDelegate[RPKChatChannelService::class.java] } returns chatChannelService
            Services.delegate = testServicesDelegate

            val listChatChannelsCommand = ListChatChannelsCommand(plugin)
            listChatChannelsCommand.onCommand(sender, mockk<Command>(), "listchatchannels", emptyArray()) shouldBe true

            alphaListeners.completeExceptionally(RuntimeException("listeners query failed"))
            betaListeners.completeExceptionally(RuntimeException("listeners query failed"))

            sentLines shouldBe emptyList<String>()
        }
    }

})

/**
 * Flattens the arguments of a captured vararg `sendMessage` call into the text the player sees.
 * MockK may hand the varargs over either as a single array argument or as separate arguments,
 * so both shapes are accepted.
 */
private fun plainText(args: List<Any?>): String = args
    .flatMap { arg -> if (arg is Array<*>) arg.asList() else listOf(arg) }
    .filterIsInstance<BaseComponent>()
    .joinToString("") { component -> component.toPlainText() }
