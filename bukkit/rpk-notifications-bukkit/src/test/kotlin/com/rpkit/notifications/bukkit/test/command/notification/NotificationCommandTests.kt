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
import com.rpkit.core.command.result.NoPermissionFailure
import com.rpkit.core.command.sender.RPKCommandSender
import com.rpkit.notifications.bukkit.RPKNotificationsBukkit
import com.rpkit.notifications.bukkit.command.notification.NotificationCommand
import com.rpkit.notifications.bukkit.messages.NotificationsMessages
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

private const val DISMISS_PERMISSION = "rpkit.notifications.command.notification.dismiss"
private const val LIST_PERMISSION = "rpkit.notifications.command.notification.list"
private const val VIEW_PERMISSION_FOR_DISPATCH = "rpkit.notifications.command.notification.view"

/**
 * Which subcommand a set of arguments reaches is observed through the permission node the
 * subcommand consults first, so the sentinel messages here only need to be distinguishable.
 */
private const val NOTIFICATION_USAGE = "usage: notification"
private const val VIEW_USAGE_FOR_DISPATCH = "usage: view"

private fun dispatchPlugin(): RPKNotificationsBukkit {
    val messages = mockk<NotificationsMessages>()
    every { messages.notificationUsage } returns NOTIFICATION_USAGE
    every { messages.notificationViewUsage } returns VIEW_USAGE_FOR_DISPATCH
    every { messages.notificationDismissUsage } returns "usage: dismiss"
    every { messages.noPermissionNotificationDismiss } returns "no permission to dismiss"
    every { messages.noPermissionNotificationList } returns "no permission to list"
    every { messages.noPermissionNotificationView } returns "no permission to view"
    val plugin = mockk<RPKNotificationsBukkit>()
    every { plugin.messages } returns messages
    return plugin
}

/**
 * A sender that holds no subcommand permission, so every subcommand stops at its own permission
 * check and reports the node it consulted.
 */
private fun unprivilegedSender(): RPKCommandSender {
    val sender = mockk<RPKCommandSender>()
    every { sender.hasPermission(any()) } returns false
    every { sender.sendMessage(any<String>()) } just runs
    return sender
}

class NotificationCommandTests : WordSpec({

    "the notification command" should {
        "send the usage when no subcommand is given" {
            val sender = unprivilegedSender()
            val plugin = dispatchPlugin()

            val result = NotificationCommand(plugin).onCommand(sender, emptyArray()).join()

            result.shouldBeInstanceOf<IncorrectUsageFailure>()
            verify { sender.sendMessage(NOTIFICATION_USAGE) }
        }

        "send the usage when the subcommand is not recognised" {
            val sender = unprivilegedSender()
            val plugin = dispatchPlugin()

            val result = NotificationCommand(plugin).onCommand(sender, arrayOf("bogus")).join()

            result.shouldBeInstanceOf<IncorrectUsageFailure>()
            verify { sender.sendMessage(NOTIFICATION_USAGE) }
        }

        "dispatch dismiss to the dismiss subcommand" {
            val sender = unprivilegedSender()
            val plugin = dispatchPlugin()

            val result = NotificationCommand(plugin).onCommand(sender, arrayOf("dismiss", "1")).join()

            result.shouldBeInstanceOf<NoPermissionFailure>().permission shouldBe DISMISS_PERMISSION
        }

        "dispatch list to the list subcommand" {
            val sender = unprivilegedSender()
            val plugin = dispatchPlugin()

            val result = NotificationCommand(plugin).onCommand(sender, arrayOf("list")).join()

            result.shouldBeInstanceOf<NoPermissionFailure>().permission shouldBe LIST_PERMISSION
        }

        "dispatch view to the view subcommand" {
            val sender = unprivilegedSender()
            val plugin = dispatchPlugin()

            val result = NotificationCommand(plugin).onCommand(sender, arrayOf("view", "1")).join()

            result.shouldBeInstanceOf<NoPermissionFailure>().permission shouldBe VIEW_PERMISSION_FOR_DISPATCH
        }

        "match the subcommand regardless of case" {
            val sender = unprivilegedSender()
            val plugin = dispatchPlugin()

            val result = NotificationCommand(plugin).onCommand(sender, arrayOf("LiSt")).join()

            result.shouldBeInstanceOf<NoPermissionFailure>().permission shouldBe LIST_PERMISSION
        }

        "pass the arguments after the subcommand on to it" {
            val sender = mockk<RPKCommandSender>()
            every { sender.hasPermission(VIEW_PERMISSION_FOR_DISPATCH) } returns true
            every { sender.sendMessage(any<String>()) } just runs
            val plugin = dispatchPlugin()

            // The view subcommand only reports incorrect usage when it receives no arguments of
            // its own, so this fails unless the "view" element itself has been dropped.
            val result = NotificationCommand(plugin).onCommand(sender, arrayOf("view")).join()

            result.shouldBeInstanceOf<IncorrectUsageFailure>()
            verify { sender.sendMessage(VIEW_USAGE_FOR_DISPATCH) }
        }
    }

})
