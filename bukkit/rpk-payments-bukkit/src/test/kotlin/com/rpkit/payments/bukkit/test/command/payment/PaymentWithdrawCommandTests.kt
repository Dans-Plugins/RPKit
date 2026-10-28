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

package com.rpkit.payments.bukkit.test.command.payment

import com.rpkit.banks.bukkit.bank.RPKBankService
import com.rpkit.characters.bukkit.character.RPKCharacter
import com.rpkit.characters.bukkit.character.RPKCharacterService
import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.payments.bukkit.RPKPaymentsBukkit
import com.rpkit.payments.bukkit.command.payment.PaymentWithdrawCommand
import com.rpkit.payments.bukkit.group.RPKPaymentGroup
import com.rpkit.payments.bukkit.group.RPKPaymentGroupName
import com.rpkit.payments.bukkit.group.RPKPaymentGroupService
import com.rpkit.payments.bukkit.messages.PaymentsMessages
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfile
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfileService
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.bukkit.command.Command
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture.completedFuture

private const val WITHDRAW_PERMISSION = "rpkit.payments.command.payment.withdraw"
private const val GROUP_NAME = "missing group"

/**
 * Every message resolves to its own key, so the assertions name the exact `messages.yml` key the
 * command looked up. A key missing from `messages.yml` would therefore show up as a wrong key here
 * rather than being hidden behind a stubbed string.
 */
private fun plugin(): RPKPaymentsBukkit {
    val messages = mockk<PaymentsMessages>()
    every { messages[any()] } answers { firstArg() }
    val plugin = mockk<RPKPaymentsBukkit>()
    every { plugin.messages } returns messages
    return plugin
}

/**
 * A player sender that records every line sent to it, so assertions can be made about the exact
 * lines and their order rather than about individual `sendMessage` calls.
 */
private class RecordingPlayer {
    val sentLines = mutableListOf<String>()
    val player = mockk<Player>()

    init {
        every { player.hasPermission(WITHDRAW_PERMISSION) } returns true
        every { player.sendMessage(any<String>()) } answers {
            sentLines += firstArg<String>()
        }
    }
}

class PaymentWithdrawCommandTests : WordSpec({

    "the payment withdraw command" should {
        "report a payment group that does not exist with the declared invalid-group message" {
            val recording = RecordingPlayer()
            val minecraftProfile = mockk<RPKMinecraftProfile>()
            val minecraftProfileService = mockk<RPKMinecraftProfileService>()
            every { minecraftProfileService.getPreloadedMinecraftProfile(recording.player) } returns minecraftProfile
            val character = mockk<RPKCharacter>()
            val characterService = mockk<RPKCharacterService>()
            every { characterService.getPreloadedActiveCharacter(minecraftProfile) } returns character
            val paymentGroupService = mockk<RPKPaymentGroupService>()
            every {
                paymentGroupService.getPaymentGroup(RPKPaymentGroupName(GROUP_NAME))
            } returns completedFuture<RPKPaymentGroup?>(null)
            val servicesDelegate = mockk<ServicesDelegate>()
            every { servicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
            every { servicesDelegate[RPKCharacterService::class.java] } returns characterService
            every { servicesDelegate[RPKPaymentGroupService::class.java] } returns paymentGroupService
            every { servicesDelegate[RPKBankService::class.java] } returns mockk()
            Services.delegate = servicesDelegate

            val result = PaymentWithdrawCommand(plugin()).onCommand(
                recording.player,
                mockk<Command>(),
                "payment",
                arrayOf("missing", "group", "10")
            )

            result shouldBe true
            recording.sentLines shouldBe listOf("payment-withdraw-invalid-group")
        }
    }

})
