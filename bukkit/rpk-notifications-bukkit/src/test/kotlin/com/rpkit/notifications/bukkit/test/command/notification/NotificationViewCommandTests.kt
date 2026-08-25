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

package com.rpkit.notifications.bukkit.test.command.notification

import com.rpkit.core.command.result.IncorrectUsageFailure
import com.rpkit.core.command.result.MissingServiceFailure
import com.rpkit.core.command.result.NoPermissionFailure
import com.rpkit.core.command.sender.RPKCommandSender
import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.notifications.bukkit.RPKNotificationsBukkit
import com.rpkit.notifications.bukkit.command.notification.NotificationDismissCommand
import com.rpkit.notifications.bukkit.command.notification.NotificationViewCommand
import com.rpkit.notifications.bukkit.messages.NotificationsMessages
import com.rpkit.notifications.bukkit.notification.RPKNotificationService
import com.rpkit.players.bukkit.command.result.NoProfileSelfFailure
import com.rpkit.players.bukkit.command.result.NotAPlayerFailure
import com.rpkit.players.bukkit.profile.RPKProfile
import com.rpkit.players.bukkit.profile.RPKThinProfile
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfile
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

private const val VIEW_PERMISSION = "rpkit.notifications.command.notification.view"

/**
 * The messages are deliberately distinct sentinels rather than the real templates: these tests are
 * about which line a branch sends, not about the wording or the colour codes inside a line.
 */
private const val NO_PERMISSION_VIEW = "no permission to view"
private const val VIEW_USAGE = "usage: view"
private const val DISMISS_USAGE = "usage: dismiss"
private const val NOT_FROM_CONSOLE = "not from console"
private const val NO_PROFILE_SELF = "no profile"
private const val ID_NOT_A_NUMBER = "id is not a number"
private const val NO_NOTIFICATION_SERVICE = "no notification service"

private fun messages(): NotificationsMessages {
    val messages = mockk<NotificationsMessages>()
    every { messages.noPermissionNotificationView } returns NO_PERMISSION_VIEW
    every { messages.notificationViewUsage } returns VIEW_USAGE
    every { messages.notificationDismissUsage } returns DISMISS_USAGE
    every { messages.notFromConsole } returns NOT_FROM_CONSOLE
    every { messages.noProfileSelf } returns NO_PROFILE_SELF
    every { messages.notificationDismissInvalidNotificationIdNotANumber } returns ID_NOT_A_NUMBER
    every { messages.noNotificationService } returns NO_NOTIFICATION_SERVICE
    return messages
}

private fun plugin(): RPKNotificationsBukkit {
    val plugin = mockk<RPKNotificationsBukkit>()
    every { plugin.messages } returns messages()
    return plugin
}

private fun sender(hasPermission: Boolean): RPKCommandSender {
    val sender = mockk<RPKCommandSender>()
    every { sender.hasPermission(VIEW_PERMISSION) } returns hasPermission
    every { sender.sendMessage(any<String>()) } just runs
    return sender
}

private fun minecraftProfile(profile: RPKThinProfile): RPKMinecraftProfile {
    val minecraftProfile = mockk<RPKMinecraftProfile>()
    every { minecraftProfile.hasPermission(VIEW_PERMISSION) } returns true
    every { minecraftProfile.sendMessage(any<String>()) } just runs
    every { minecraftProfile.profile } returns profile
    return minecraftProfile
}

private fun servicesDelegate(notificationService: RPKNotificationService?): ServicesDelegate {
    val delegate = mockk<ServicesDelegate>()
    every { delegate[RPKNotificationService::class.java] } returns notificationService
    return delegate
}

class NotificationViewCommandTests : WordSpec({

    "the notification view command" should {
        "reject a sender without the view permission" {
            val sender = sender(hasPermission = false)
            val plugin = plugin()

            val result = NotificationViewCommand(plugin).onCommand(sender, arrayOf("1")).join()

            result.shouldBeInstanceOf<NoPermissionFailure>().permission shouldBe VIEW_PERMISSION
            verify { sender.sendMessage(NO_PERMISSION_VIEW) }
        }

        // The usage line has to name the subcommand the player actually typed; before this was
        // fixed the view command sent the dismiss command's usage line instead.
        "send the view usage when no notification id is given" {
            val sender = sender(hasPermission = true)
            val plugin = plugin()

            val result = NotificationViewCommand(plugin).onCommand(sender, emptyArray()).join()

            result.shouldBeInstanceOf<IncorrectUsageFailure>()
            verify { sender.sendMessage(VIEW_USAGE) }
            verify(exactly = 0) { sender.sendMessage(DISMISS_USAGE) }
        }

        "reject a sender that is not a Minecraft profile" {
            val sender = sender(hasPermission = true)
            val plugin = plugin()

            val result = NotificationViewCommand(plugin).onCommand(sender, arrayOf("1")).join()

            result.shouldBeInstanceOf<NotAPlayerFailure>()
            verify { sender.sendMessage(NOT_FROM_CONSOLE) }
        }

        "reject a Minecraft profile with no full profile" {
            val sender = minecraftProfile(mockk<RPKThinProfile>())
            val plugin = plugin()

            val result = NotificationViewCommand(plugin).onCommand(sender, arrayOf("1")).join()

            result.shouldBeInstanceOf<NoProfileSelfFailure>()
            verify { sender.sendMessage(NO_PROFILE_SELF) }
        }

        "reject a notification id that is not a number" {
            val sender = minecraftProfile(mockk<RPKProfile>())
            val plugin = plugin()

            val result = NotificationViewCommand(plugin).onCommand(sender, arrayOf("not-a-number")).join()

            result.shouldBeInstanceOf<NotificationDismissCommand.InvalidNotificationIdNotANumberFailure>()
            verify { sender.sendMessage(ID_NOT_A_NUMBER) }
        }

        "report a missing notification service" {
            val sender = minecraftProfile(mockk<RPKProfile>())
            val plugin = plugin()
            Services.delegate = servicesDelegate(notificationService = null)

            val result = NotificationViewCommand(plugin).onCommand(sender, arrayOf("1")).join()

            result.shouldBeInstanceOf<MissingServiceFailure>().service shouldBe RPKNotificationService::class.java
            verify { sender.sendMessage(NO_NOTIFICATION_SERVICE) }
        }
    }

})
