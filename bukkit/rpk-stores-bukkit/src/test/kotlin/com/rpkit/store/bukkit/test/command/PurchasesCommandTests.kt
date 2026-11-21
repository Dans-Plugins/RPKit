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

package com.rpkit.store.bukkit.test.command

import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.players.bukkit.profile.RPKProfile
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfile
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfileService
import com.rpkit.store.bukkit.RPKStoresBukkit
import com.rpkit.store.bukkit.command.PurchasesCommand
import com.rpkit.store.bukkit.messages.StoresMessages
import com.rpkit.store.bukkit.purchase.RPKPurchase
import com.rpkit.store.bukkit.purchase.RPKPurchaseId
import com.rpkit.store.bukkit.purchase.RPKPurchaseService
import com.rpkit.store.bukkit.storeitem.RPKStoreItem
import com.rpkit.store.bukkit.storeitem.RPKStoreItemId
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.command.Command
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

/**
 * The config.yml bundled with this module. Other modules on the test classpath also bundle a
 * config.yml at the root of their jars, so the copy is picked out by its path rather than by the
 * first match.
 */
private fun bundledConfig(): YamlConfiguration {
    val resource = PurchasesCommandTests::class.java.classLoader
        .getResources("config.yml")
        .toList()
        .single { it.path.contains("rpk-stores-bukkit/") }
    return resource.openStream().use { YamlConfiguration.loadConfiguration(InputStreamReader(it)) }
}

/**
 * A plugin whose config is the bundled config.yml, and whose messages record the variables passed
 * for each parameterized key so the assertions can inspect exactly what the command supplied.
 */
private class RecordingPlugin {
    val variables = mutableMapOf<String, Map<String, String>>()
    val plugin = mockk<RPKStoresBukkit>()

    init {
        val messages = mockk<StoresMessages>()
        every { messages[any()] } answers { firstArg() }
        every { messages[any(), any()] } answers {
            variables[firstArg()] = secondArg()
            firstArg()
        }
        every { plugin.messages } returns messages
        every { plugin.config } returns bundledConfig()
    }
}

private fun purchase(cost: Int): RPKPurchase {
    val storeItem = mockk<RPKStoreItem>()
    every { storeItem.id } returns RPKStoreItemId(2)
    every { storeItem.plugin } returns "rpk-test-bukkit"
    every { storeItem.identifier } returns "test-item"
    every { storeItem.description } returns "A test item"
    every { storeItem.cost } returns cost
    val purchase = mockk<RPKPurchase>()
    every { purchase.id } returns RPKPurchaseId(1)
    every { purchase.purchaseDate } returns LocalDateTime.of(2022, 1, 1, 0, 0)
    every { purchase.storeItem } returns storeItem
    return purchase
}

class PurchasesCommandTests : WordSpec({

    "the purchases command" should {
        "suffix each item's cost with the payments.currency declared in config.yml" {
            val recording = RecordingPlugin()
            val sender = mockk<Player>()
            every { sender.hasPermission("rpkit.stores.command.purchases") } returns true
            every { sender.sendMessage(any<String>()) } returns Unit
            val profile = mockk<RPKProfile>()
            val minecraftProfile = mockk<RPKMinecraftProfile>()
            every { minecraftProfile.profile } returns profile
            val minecraftProfileService = mockk<RPKMinecraftProfileService>()
            every { minecraftProfileService.getPreloadedMinecraftProfile(sender) } returns minecraftProfile
            val purchaseService = mockk<RPKPurchaseService>()
            every { purchaseService.getPurchases(profile) } returns
                CompletableFuture.completedFuture(listOf(purchase(cost = 500)))
            val testServicesDelegate = mockk<ServicesDelegate>()
            every { testServicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
            every { testServicesDelegate[RPKPurchaseService::class.java] } returns purchaseService
            Services.delegate = testServicesDelegate

            PurchasesCommand(recording.plugin)
                .onCommand(sender, mockk<Command>(), "purchases", arrayOf()) shouldBe true

            recording.variables["purchases-item"]?.get("store_item_cost") shouldBe "5.00GBP"
        }
    }

})
