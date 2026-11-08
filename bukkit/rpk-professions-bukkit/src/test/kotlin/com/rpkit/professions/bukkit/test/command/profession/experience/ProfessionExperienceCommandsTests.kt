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

package com.rpkit.professions.bukkit.test.command.profession.experience

import com.rpkit.characters.bukkit.character.RPKCharacter
import com.rpkit.characters.bukkit.character.RPKCharacterService
import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfile
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfileService
import com.rpkit.professions.bukkit.RPKProfessionsBukkit
import com.rpkit.professions.bukkit.command.profession.experience.ProfessionExperienceAddCommand
import com.rpkit.professions.bukkit.command.profession.experience.ProfessionExperienceRemoveCommand
import com.rpkit.professions.bukkit.command.profession.experience.ProfessionExperienceSetCommand
import com.rpkit.professions.bukkit.messages.ProfessionsMessages
import com.rpkit.professions.bukkit.profession.RPKProfession
import com.rpkit.professions.bukkit.profession.RPKProfessionName
import com.rpkit.professions.bukkit.profession.RPKProfessionService
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.Server
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

private const val TARGET_NAME = "TargetPlayer"

/**
 * Resolves message keys the way BukkitMessages does: a read with no variables returns the template
 * as written, so an unfilled `${player}` token stays visible in the sent line.
 */
private fun messages(): ProfessionsMessages {
    val templates = mapOf(
        "no-minecraft-profile-other" to "\${player} does not have a Minecraft profile.",
        "no-character-other" to "\${player} does not currently have an active character.",
        "profession-experience-add-valid" to
            "\${player}/\${character} received \${received_experience} in \${profession} (total: \${total_experience})",
        "profession-experience-remove-valid" to
            "\${player}/\${character} lost \${removed_experience} in \${profession} (total: \${total_experience})",
        "profession-experience-set-valid" to
            "\${player}/\${character} had their experience in \${profession} set to \${total_experience}."
    )
    val messages = mockk<ProfessionsMessages>()
    every { messages[any()] } answers { templates.getValue(firstArg()) }
    every { messages[any(), any()] } answers {
        secondArg<Map<String, String>>().entries.fold(templates.getValue(firstArg())) { line, (key, value) ->
            line.replace("\${$key}", value)
        }
    }
    return messages
}

private fun plugin(target: Player): RPKProfessionsBukkit {
    val server = mockk<Server>()
    every { server.getPlayer(TARGET_NAME) } returns target
    val plugin = mockk<RPKProfessionsBukkit>()
    every { plugin.messages } returns messages()
    every { plugin.server } returns server
    return plugin
}

private fun target(): Player {
    val target = mockk<Player>()
    every { target.name } returns TARGET_NAME
    return target
}

/**
 * A sender allowed to target other players, recording every line sent to it.
 */
private class RecordingSender {
    val sentLines = mutableListOf<String>()
    val player = mockk<Player>()

    init {
        every { player.hasPermission(any<String>()) } returns true
        every { player.sendMessage(any<String>()) } answers {
            sentLines += firstArg<String>()
        }
    }
}

private fun installServices(
    minecraftProfileService: RPKMinecraftProfileService,
    characterService: RPKCharacterService = mockk(),
    professionService: RPKProfessionService = mockk()
) {
    val servicesDelegate = mockk<ServicesDelegate>()
    every { servicesDelegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
    every { servicesDelegate[RPKCharacterService::class.java] } returns characterService
    every { servicesDelegate[RPKProfessionService::class.java] } returns professionService
    Services.delegate = servicesDelegate
}

/**
 * Services for a target with a Minecraft profile, an active character and a "mining" profession
 * holding [currentExperience]. Every experience write completes with [setResult].
 */
private class SuccessfulTarget(
    currentExperience: Int,
    setResult: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
) {
    val target = target()
    val character = mockk<RPKCharacter>()
    val profession = mockk<RPKProfession>()
    val professionService = mockk<RPKProfessionService>()

    init {
        val minecraftProfile = mockk<RPKMinecraftProfile>()
        every { minecraftProfile.name } returns TARGET_NAME
        val minecraftProfileService = mockk<RPKMinecraftProfileService>()
        every { minecraftProfileService.getPreloadedMinecraftProfile(target) } returns minecraftProfile
        every { character.name } returns "Aria"
        every { character.isNameHidden } returns false
        val characterService = mockk<RPKCharacterService>()
        every { characterService.getPreloadedActiveCharacter(minecraftProfile) } returns character
        every { profession.name } returns RPKProfessionName("mining")
        every { professionService.getProfession(RPKProfessionName("mining")) } returns profession
        every { professionService.getProfessionExperience(character, profession) } returns
            CompletableFuture.completedFuture(currentExperience)
        every { professionService.setProfessionExperience(character, profession, any()) } returns setResult
        installServices(minecraftProfileService, characterService, professionService)
    }
}

class ProfessionExperienceCommandsTests : WordSpec({

    val commands: List<Pair<String, (RPKProfessionsBukkit) -> CommandExecutor>> = listOf(
        "add" to ::ProfessionExperienceAddCommand,
        "remove" to ::ProfessionExperienceRemoveCommand,
        "set" to ::ProfessionExperienceSetCommand
    )

    commands.forEach { (name, createCommand) ->
        "the profession experience $name command" should {
            "name the target player when the target has no Minecraft profile" {
                val sender = RecordingSender()
                val target = target()
                val minecraftProfileService = mockk<RPKMinecraftProfileService>()
                every { minecraftProfileService.getPreloadedMinecraftProfile(target) } returns null
                installServices(minecraftProfileService)

                createCommand(plugin(target)).onCommand(
                    sender.player,
                    mockk<Command>(),
                    "experience",
                    arrayOf(TARGET_NAME, "mining", "5")
                )

                sender.sentLines shouldBe listOf("$TARGET_NAME does not have a Minecraft profile.")
            }

            "name the target player when the target has no active character" {
                val sender = RecordingSender()
                val target = target()
                val minecraftProfile = mockk<RPKMinecraftProfile>()
                val minecraftProfileService = mockk<RPKMinecraftProfileService>()
                every { minecraftProfileService.getPreloadedMinecraftProfile(target) } returns minecraftProfile
                val characterService = mockk<RPKCharacterService>()
                every { characterService.getPreloadedActiveCharacter(minecraftProfile) } returns null
                installServices(minecraftProfileService, characterService)

                createCommand(plugin(target)).onCommand(
                    sender.player,
                    mockk<Command>(),
                    "experience",
                    arrayOf(TARGET_NAME, "mining", "5")
                )

                sender.sentLines shouldBe listOf("$TARGET_NAME does not currently have an active character.")
            }
        }
    }

    "the profession experience add command" should {
        "show the new total experience after adding" {
            val sender = RecordingSender()
            val successfulTarget = SuccessfulTarget(currentExperience = 30)

            ProfessionExperienceAddCommand(plugin(successfulTarget.target)).onCommand(
                sender.player,
                mockk<Command>(),
                "experience",
                arrayOf(TARGET_NAME, "mining", "12")
            )

            verify { successfulTarget.professionService.setProfessionExperience(successfulTarget.character, successfulTarget.profession, 42) }
            sender.sentLines shouldBe listOf("$TARGET_NAME/Aria received 12 in mining (total: 42)")
        }
    }

    "the profession experience remove command" should {
        "show the new total experience after removing" {
            val sender = RecordingSender()
            val successfulTarget = SuccessfulTarget(currentExperience = 30)

            ProfessionExperienceRemoveCommand(plugin(successfulTarget.target)).onCommand(
                sender.player,
                mockk<Command>(),
                "experience",
                arrayOf(TARGET_NAME, "mining", "12")
            )

            verify { successfulTarget.professionService.setProfessionExperience(successfulTarget.character, successfulTarget.profession, 18) }
            sender.sentLines shouldBe listOf("$TARGET_NAME/Aria lost 12 in mining (total: 18)")
        }
    }

    "the profession experience set command" should {
        "show the experience that was set as the total" {
            val sender = RecordingSender()
            val successfulTarget = SuccessfulTarget(currentExperience = 30)

            ProfessionExperienceSetCommand(plugin(successfulTarget.target)).onCommand(
                sender.player,
                mockk<Command>(),
                "experience",
                arrayOf(TARGET_NAME, "mining", "12")
            )

            verify { successfulTarget.professionService.setProfessionExperience(successfulTarget.character, successfulTarget.profession, 12) }
            sender.sentLines shouldBe listOf("$TARGET_NAME/Aria had their experience in mining set to 12.")
        }

        "only confirm once the experience has been saved" {
            val sender = RecordingSender()
            val pendingSet = CompletableFuture<Void>()
            val successfulTarget = SuccessfulTarget(currentExperience = 30, setResult = pendingSet)

            ProfessionExperienceSetCommand(plugin(successfulTarget.target)).onCommand(
                sender.player,
                mockk<Command>(),
                "experience",
                arrayOf(TARGET_NAME, "mining", "12")
            )

            sender.sentLines shouldBe emptyList()
            pendingSet.complete(null)
            sender.sentLines shouldBe listOf("$TARGET_NAME/Aria had their experience in mining set to 12.")
        }
    }

})
