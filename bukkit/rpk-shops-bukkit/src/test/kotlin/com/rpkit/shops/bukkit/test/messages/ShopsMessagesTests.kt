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

package com.rpkit.shops.bukkit.test.messages

import com.rpkit.shops.bukkit.RPKShopsBukkit
import com.rpkit.shops.bukkit.messages.ShopsMessages
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files

/**
 * The messages.yml bundled with this module. Other modules on the test classpath also bundle a
 * messages.yml at the root of their jars, so the copy is picked out by its path rather than by the
 * first match.
 */
private fun bundledMessagesYml() = ShopsMessagesTests::class.java.classLoader
    .getResources("messages.yml")
    .toList()
    .single { it.path.contains("rpk-shops-bukkit/") }

/**
 * Messages backed by the bundled messages.yml, with an empty server-side override file so every
 * value comes from the bundled defaults.
 */
private fun messages(): ShopsMessages {
    val dataFolder = Files.createTempDirectory("rpk-shops-messages").toFile()
    dataFolder.deleteOnExit()
    val overrideFile = File(dataFolder, "messages.yml")
    overrideFile.createNewFile()
    overrideFile.deleteOnExit()
    val plugin = mockk<RPKShopsBukkit>()
    every { plugin.dataFolder } returns dataFolder
    every { plugin.getResource("messages.yml") } answers { bundledMessagesYml().openStream() }
    return ShopsMessages(plugin)
}

class ShopsMessagesTests : WordSpec({

    "the shops messages" should {
        "read the no-preloaded-balance line from its key in messages.yml" {
            messages().noPreloadedBalance shouldBe
                "§cYour wallet balance has not been loaded. Please try relogging, or if the problem persists, contact an administrator."
        }
    }

})
