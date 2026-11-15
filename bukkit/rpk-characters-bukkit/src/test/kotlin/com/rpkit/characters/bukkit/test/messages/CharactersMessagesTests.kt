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

package com.rpkit.characters.bukkit.test.messages

import com.rpkit.characters.bukkit.RPKCharactersBukkit
import com.rpkit.characters.bukkit.messages.CharactersMessages
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
private fun bundledMessagesYml() = CharactersMessagesTests::class.java.classLoader
    .getResources("messages.yml")
    .toList()
    .single { it.path.contains("rpk-characters-bukkit/") }

/**
 * Messages backed by the bundled messages.yml, with an empty server-side override file so every
 * value comes from the bundled defaults.
 */
private fun messages(): CharactersMessages {
    val dataFolder = Files.createTempDirectory("rpk-characters-messages").toFile()
    dataFolder.deleteOnExit()
    val overrideFile = File(dataFolder, "messages.yml")
    overrideFile.createNewFile()
    overrideFile.deleteOnExit()
    val plugin = mockk<RPKCharactersBukkit>()
    every { plugin.dataFolder } returns dataFolder
    every { plugin.getResource("messages.yml") } answers { bundledMessagesYml().openStream() }
    return CharactersMessages(plugin)
}

class CharactersMessagesTests : WordSpec({

    "the characters messages" should {
        "read the no-unit-service line from its key in messages.yml" {
            messages().noUnitService shouldBe "§cThere is no unit service available."
        }
    }

})
