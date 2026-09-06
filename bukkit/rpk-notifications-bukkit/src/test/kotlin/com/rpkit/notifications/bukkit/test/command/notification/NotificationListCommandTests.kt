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

import com.rpkit.core.command.result.CommandSuccess
import com.rpkit.core.command.result.MissingServiceFailure
import com.rpkit.core.command.result.NoPermissionFailure
import com.rpkit.core.command.sender.RPKCommandSender
import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.notifications.bukkit.RPKNotificationsBukkit
import com.rpkit.notifications.bukkit.command.notification.NotificationListCommand
import com.rpkit.notifications.bukkit.messages.NotificationsMessages
import com.rpkit.notifications.bukkit.messages.NotificationsMessages.InvalidPageMessage
import com.rpkit.notifications.bukkit.messages.NotificationsMessages.NotificationListItemMessage
import com.rpkit.notifications.bukkit.messages.NotificationsMessages.PageMessage
import com.rpkit.notifications.bukkit.notification.RPKNotification
import com.rpkit.notifications.bukkit.notification.RPKNotificationId
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
import net.md_5.bungee.api.chat.BaseComponent
import java.time.Instant
import java.util.concurrent.CompletableFuture.completedFuture

private const val LIST_PERMISSION = "rpkit.notifications.command.notification.list"

private const val NO_PERMISSION_LIST = "no permission to list"
private const val NOT_FROM_CONSOLE = "not from console"
private const val NO_PROFILE_SELF = "no profile"
private const val NO_NOTIFICATION_SERVICE = "no notification service"
private const val LIST_TITLE = "notifications:"
private const val LIST_ITEM = "a notification"
private const val PAGE_FOOTER = "page 1"
private const val INVALID_PAGE = "invalid page"
private const val NO_NOTIFICATIONS = "no notifications"

private fun plugin(): RPKNotificationsBukkit {
    val messages = mockk<NotificationsMessages>()
    every { messages.noPermissionNotificationList } returns NO_PERMISSION_LIST
    every { messages.notFromConsole } returns NOT_FROM_CONSOLE
    every { messages.noProfileSelf } returns NO_PROFILE_SELF
    every { messages.noNotificationService } returns NO_NOTIFICATION_SERVICE
    every { messages.notificationListTitle } returns LIST_TITLE
    every { messages.notificationListItemHover } returns "click to view"
    every { messages.notificationListNoNotifications } returns NO_NOTIFICATIONS
    every { messages.previousPage } returns "previous"
    every { messages.previousPageHover } returns "previous hover"
    every { messages.nextPage } returns "next"
    every { messages.nextPageHover } returns "next hover"
    every { messages.notificationListItem } returns mockk<NotificationListItemMessage> {
        every { withParameters(any()) } returns LIST_ITEM
    }
    every { messages.page } returns mockk<PageMessage> {
        every { withParameters(any()) } returns PAGE_FOOTER
    }
    every { messages.invalidPage } returns mockk<InvalidPageMessage> {
        every { withParameters(any()) } returns INVALID_PAGE
    }
    val plugin = mockk<RPKNotificationsBukkit>()
    every { plugin.messages } returns messages
    return plugin
}

private fun sender(hasPermission: Boolean): RPKCommandSender {
    val sender = mockk<RPKCommandSender>()
    every { sender.hasPermission(LIST_PERMISSION) } returns hasPermission
    every { sender.sendMessage(any<String>()) } just runs
    return sender
}

/**
 * Collects the lines the paginated view sends, flattened to the text the player sees, so the
 * assertions are about which lines appear in which order rather than about component structure.
 */
private class RecordingMinecraftProfile {
    val sentLines = mutableListOf<String>()
    val minecraftProfile = mockk<RPKMinecraftProfile>()
}

private fun recordingMinecraftProfile(profile: RPKThinProfile): RecordingMinecraftProfile {
    val recording = RecordingMinecraftProfile()
    val minecraftProfile = recording.minecraftProfile
    every { minecraftProfile.hasPermission(LIST_PERMISSION) } returns true
    every { minecraftProfile.sendMessage(any<String>()) } just runs
    every { minecraftProfile.profile } returns profile
    every { minecraftProfile.sendMessage(*anyVararg<BaseComponent>()) } answers {
        recording.sentLines += plainText(call.invocation.args)
    }
    return recording
}

private fun servicesDelegate(notificationService: RPKNotificationService?): ServicesDelegate {
    val delegate = mockk<ServicesDelegate>()
    every { delegate[RPKNotificationService::class.java] } returns notificationService
    return delegate
}

private fun notification(recipient: RPKProfile): RPKNotification {
    val notification = mockk<RPKNotification>()
    every { notification.id } returns RPKNotificationId(1)
    every { notification.recipient } returns recipient
    every { notification.title } returns "a title"
    every { notification.content } returns "some content"
    every { notification.time } returns Instant.EPOCH
    every { notification.read } returns false
    return notification
}

class NotificationListCommandTests : WordSpec({

    "the notification list command" should {
        "reject a sender without the list permission" {
            val sender = sender(hasPermission = false)
            val plugin = plugin()

            val result = NotificationListCommand(plugin).onCommand(sender, emptyArray()).join()

            result.shouldBeInstanceOf<NoPermissionFailure>().permission shouldBe LIST_PERMISSION
            verify { sender.sendMessage(NO_PERMISSION_LIST) }
        }

        "reject a sender that is not a Minecraft profile" {
            val sender = sender(hasPermission = true)
            val plugin = plugin()

            val result = NotificationListCommand(plugin).onCommand(sender, emptyArray()).join()

            result.shouldBeInstanceOf<NotAPlayerFailure>()
            verify { sender.sendMessage(NOT_FROM_CONSOLE) }
        }

        "reject a Minecraft profile with no full profile" {
            val recording = recordingMinecraftProfile(mockk<RPKThinProfile>())
            val plugin = plugin()

            val result = NotificationListCommand(plugin).onCommand(recording.minecraftProfile, emptyArray()).join()

            result.shouldBeInstanceOf<NoProfileSelfFailure>()
            verify { recording.minecraftProfile.sendMessage(NO_PROFILE_SELF) }
        }

        "report a missing notification service" {
            val recording = recordingMinecraftProfile(mockk<RPKProfile>())
            val plugin = plugin()
            Services.delegate = servicesDelegate(notificationService = null)

            val result = NotificationListCommand(plugin).onCommand(recording.minecraftProfile, emptyArray()).join()

            result.shouldBeInstanceOf<MissingServiceFailure>().service shouldBe RPKNotificationService::class.java
            verify { recording.minecraftProfile.sendMessage(NO_NOTIFICATION_SERVICE) }
        }

        "send the title and an item line for the sender's own notifications" {
            val profile = mockk<RPKProfile>()
            val recording = recordingMinecraftProfile(profile)
            val plugin = plugin()
            val notificationService = mockk<RPKNotificationService>()
            every { notificationService.getNotifications(profile) } returns
                    completedFuture(listOf(notification(profile)))
            Services.delegate = servicesDelegate(notificationService)

            val result = NotificationListCommand(plugin).onCommand(recording.minecraftProfile, emptyArray()).join()

            result shouldBe CommandSuccess
            recording.sentLines shouldBe listOf(LIST_TITLE, LIST_ITEM, PAGE_FOOTER)
        }

        "send the no notifications message when the sender has no notifications" {
            val profile = mockk<RPKProfile>()
            val recording = recordingMinecraftProfile(profile)
            val plugin = plugin()
            val notificationService = mockk<RPKNotificationService>()
            every { notificationService.getNotifications(profile) } returns completedFuture(emptyList())
            Services.delegate = servicesDelegate(notificationService)

            val result = NotificationListCommand(plugin).onCommand(recording.minecraftProfile, emptyArray()).join()

            result shouldBe CommandSuccess
            recording.sentLines shouldBe emptyList<String>()
            verify { recording.minecraftProfile.sendMessage(NO_NOTIFICATIONS) }
            verify(exactly = 0) { recording.minecraftProfile.sendMessage(INVALID_PAGE) }
        }

        "send the invalid page message for a page the sender's notifications do not reach" {
            val profile = mockk<RPKProfile>()
            val recording = recordingMinecraftProfile(profile)
            val plugin = plugin()
            val notificationService = mockk<RPKNotificationService>()
            every { notificationService.getNotifications(profile) } returns
                    completedFuture(listOf(notification(profile)))
            Services.delegate = servicesDelegate(notificationService)

            val result = NotificationListCommand(plugin).onCommand(recording.minecraftProfile, arrayOf("2")).join()

            result shouldBe CommandSuccess
            recording.sentLines shouldBe emptyList<String>()
            verify { recording.minecraftProfile.sendMessage(INVALID_PAGE) }
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
