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

package com.rpkit.permissions.bukkit.test.messages

import com.rpkit.permissions.bukkit.RPKPermissionsBukkit
import com.rpkit.permissions.bukkit.group.RPKGroup
import com.rpkit.permissions.bukkit.group.RPKGroupName
import com.rpkit.permissions.bukkit.messages.PermissionsMessages
import com.rpkit.players.bukkit.profile.RPKProfile
import com.rpkit.players.bukkit.profile.RPKProfileDiscriminator
import com.rpkit.players.bukkit.profile.RPKProfileName
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files

/**
 * The messages.yml bundled with this module. rpk-core-bukkit also bundles a messages.yml at the
 * root of its jar, so the copy is picked out by its path rather than by the first match.
 */
private fun bundledMessagesYml() = PermissionsMessagesTests::class.java.classLoader
    .getResources("messages.yml")
    .toList()
    .single { it.path.contains("rpk-permissions-bukkit/") }

/**
 * Messages backed by the bundled messages.yml, with an empty server-side override file so every
 * value comes from the bundled defaults.
 */
private fun messages(): PermissionsMessages {
    val dataFolder = Files.createTempDirectory("rpk-permissions-messages").toFile()
    dataFolder.deleteOnExit()
    val overrideFile = File(dataFolder, "messages.yml")
    overrideFile.createNewFile()
    overrideFile.deleteOnExit()
    val plugin = mockk<RPKPermissionsBukkit>()
    every { plugin.dataFolder } returns dataFolder
    every { plugin.getResource("messages.yml") } answers { bundledMessagesYml().openStream() }
    return PermissionsMessages(plugin)
}

class PermissionsMessagesTests : WordSpec({

    "the permissions messages" should {
        "read the charactergroup prepareswitchpriority usage from its key in messages.yml" {
            messages().characterGroupPrepareSwitchPriorityUsage shouldBe
                "§cUsage: /charactergroup prepareswitchpriority [profile] [character] [group1]"
        }

        "read the group switchpriority group-not-present line from its key in messages.yml" {
            val profile = mockk<RPKProfile>()
            every { profile.name } returns RPKProfileName("Alice")
            every { profile.discriminator } returns RPKProfileDiscriminator(1234)
            val group = mockk<RPKGroup>()
            every { group.name } returns RPKGroupName("moderator")

            messages().groupSwitchPriorityInvalidGroupNotPresent.withParameters(profile, group) shouldBe
                "§cProfile Alice#1234 does not have group moderator."
        }
    }

})
