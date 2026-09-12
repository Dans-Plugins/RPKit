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

package com.rpkit.stats.bukkit.test.command.stats

import com.rpkit.characters.bukkit.character.RPKCharacter
import com.rpkit.characters.bukkit.character.RPKCharacterService
import com.rpkit.core.service.Services
import com.rpkit.core.service.ServicesDelegate
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfile
import com.rpkit.players.bukkit.profile.minecraft.RPKMinecraftProfileService
import com.rpkit.stats.bukkit.RPKStatsBukkit
import com.rpkit.stats.bukkit.command.stats.StatsCommand
import com.rpkit.stats.bukkit.messages.StatsMessages
import com.rpkit.stats.bukkit.stat.RPKStat
import com.rpkit.stats.bukkit.stat.RPKStatName
import com.rpkit.stats.bukkit.stat.RPKStatService
import com.rpkit.stats.bukkit.stat.RPKStatVariable
import com.rpkit.stats.bukkit.stat.RPKStatVariableService
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.bukkit.command.Command
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player

private const val STATS_PERMISSION = "rpkit.stats.command.stats"

private const val NO_PERMISSION_STATS = "no permission to view stats"
private const val NOT_FROM_CONSOLE = "not from console"
private const val NO_MINECRAFT_PROFILE_SERVICE = "no minecraft profile service"
private const val NO_CHARACTER_SERVICE = "no character service"
private const val NO_MINECRAFT_PROFILE = "no minecraft profile"
private const val NO_CHARACTER = "no character"
private const val NO_STATS_SERVICE = "no stats service"
private const val NO_STAT_VARIABLE_SERVICE = "no stat variable service"
private const val LIST_TITLE = "stats:"

/**
 * The stubbed messages are deliberately free of colour codes: these tests are about which line is
 * sent on which branch, not about how colour codes are rendered. The item line is rebuilt from
 * the parameters the command passes, so the assertions see the stat name and value it supplied.
 */
private fun plugin(): RPKStatsBukkit {
    val messages = mockk<StatsMessages>()
    every { messages["no-permission-stats"] } returns NO_PERMISSION_STATS
    every { messages["not-from-console"] } returns NOT_FROM_CONSOLE
    every { messages["no-minecraft-profile-service"] } returns NO_MINECRAFT_PROFILE_SERVICE
    every { messages["no-character-service"] } returns NO_CHARACTER_SERVICE
    every { messages["no-minecraft-profile"] } returns NO_MINECRAFT_PROFILE
    every { messages["no-character"] } returns NO_CHARACTER
    every { messages["no-stats-service"] } returns NO_STATS_SERVICE
    every { messages["no-stat-variable-service"] } returns NO_STAT_VARIABLE_SERVICE
    every { messages["stats-list-title"] } returns LIST_TITLE
    every { messages["stats-list-item", any()] } answers {
        val vars = secondArg<Map<String, String>>()
        "${vars["stat"]}: ${vars["value"]}"
    }
    val plugin = mockk<RPKStatsBukkit>()
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
}

private fun recordingPlayer(hasPermission: Boolean = true): RecordingPlayer {
    val recording = RecordingPlayer()
    every { recording.player.hasPermission(STATS_PERMISSION) } returns hasPermission
    every { recording.player.sendMessage(any<String>()) } answers {
        recording.sentLines += firstArg<String>()
    }
    return recording
}

private fun servicesDelegate(
    minecraftProfileService: RPKMinecraftProfileService? = null,
    characterService: RPKCharacterService? = null,
    statService: RPKStatService? = null,
    statVariableService: RPKStatVariableService? = null
): ServicesDelegate {
    val delegate = mockk<ServicesDelegate>()
    every { delegate[RPKMinecraftProfileService::class.java] } returns minecraftProfileService
    every { delegate[RPKCharacterService::class.java] } returns characterService
    every { delegate[RPKStatService::class.java] } returns statService
    every { delegate[RPKStatVariableService::class.java] } returns statVariableService
    return delegate
}

private fun minecraftProfileService(player: Player, minecraftProfile: RPKMinecraftProfile?): RPKMinecraftProfileService {
    val minecraftProfileService = mockk<RPKMinecraftProfileService>()
    every { minecraftProfileService.getPreloadedMinecraftProfile(player) } returns minecraftProfile
    return minecraftProfileService
}

private fun characterService(minecraftProfile: RPKMinecraftProfile, character: RPKCharacter?): RPKCharacterService {
    val characterService = mockk<RPKCharacterService>()
    every { characterService.getPreloadedActiveCharacter(minecraftProfile) } returns character
    return characterService
}

private fun stat(name: String, value: Int, character: RPKCharacter, statVariables: List<RPKStatVariable>): RPKStat {
    val stat = mockk<RPKStat>()
    every { stat.name } returns RPKStatName(name)
    every { stat.get(character, statVariables) } returns value
    return stat
}

private fun runStatsCommand(plugin: RPKStatsBukkit, sender: Player): Boolean =
    StatsCommand(plugin).onCommand(sender, mockk<Command>(), "stats", emptyArray())

class StatsCommandTests : WordSpec({

    "the stats command" should {
        "reject a sender without the stats permission" {
            val recording = recordingPlayer(hasPermission = false)
            val plugin = plugin()

            runStatsCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(NO_PERMISSION_STATS)
        }

        "reject a sender that is not a player" {
            val sender = mockk<ConsoleCommandSender>()
            every { sender.hasPermission(STATS_PERMISSION) } returns true
            every { sender.sendMessage(any<String>()) } just runs
            val plugin = plugin()

            StatsCommand(plugin).onCommand(sender, mockk<Command>(), "stats", emptyArray()) shouldBe true

            verify(exactly = 1) { sender.sendMessage(NOT_FROM_CONSOLE) }
        }

        "report a missing Minecraft profile service" {
            val recording = recordingPlayer()
            val plugin = plugin()
            Services.delegate = servicesDelegate(minecraftProfileService = null)

            runStatsCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(NO_MINECRAFT_PROFILE_SERVICE)
        }

        "report a missing character service" {
            val recording = recordingPlayer()
            val plugin = plugin()
            Services.delegate = servicesDelegate(
                minecraftProfileService = minecraftProfileService(recording.player, mockk()),
                characterService = null
            )

            runStatsCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(NO_CHARACTER_SERVICE)
        }

        "report a sender with no Minecraft profile" {
            val recording = recordingPlayer()
            val plugin = plugin()
            Services.delegate = servicesDelegate(
                minecraftProfileService = minecraftProfileService(recording.player, null),
                characterService = mockk()
            )

            runStatsCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(NO_MINECRAFT_PROFILE)
        }

        "report a sender with no active character" {
            val recording = recordingPlayer()
            val plugin = plugin()
            val minecraftProfile = mockk<RPKMinecraftProfile>()
            Services.delegate = servicesDelegate(
                minecraftProfileService = minecraftProfileService(recording.player, minecraftProfile),
                characterService = characterService(minecraftProfile, null)
            )

            runStatsCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(NO_CHARACTER)
        }

        "report a missing stat service" {
            val recording = recordingPlayer()
            val plugin = plugin()
            val minecraftProfile = mockk<RPKMinecraftProfile>()
            Services.delegate = servicesDelegate(
                minecraftProfileService = minecraftProfileService(recording.player, minecraftProfile),
                characterService = characterService(minecraftProfile, mockk()),
                statService = null
            )

            runStatsCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(NO_STATS_SERVICE)
        }

        "report a missing stat variable service" {
            val recording = recordingPlayer()
            val plugin = plugin()
            val minecraftProfile = mockk<RPKMinecraftProfile>()
            Services.delegate = servicesDelegate(
                minecraftProfileService = minecraftProfileService(recording.player, minecraftProfile),
                characterService = characterService(minecraftProfile, mockk()),
                statService = mockk(),
                statVariableService = null
            )

            runStatsCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(NO_STAT_VARIABLE_SERVICE)
        }

        "send the title and one line per stat, in stat service order, evaluated for the active character" {
            val recording = recordingPlayer()
            val plugin = plugin()
            val minecraftProfile = mockk<RPKMinecraftProfile>()
            val character = mockk<RPKCharacter>()
            val statVariables = listOf(mockk<RPKStatVariable>())
            val statService = mockk<RPKStatService>()
            every { statService.stats } returns listOf(
                stat("strength", 7, character, statVariables),
                stat("agility", 3, character, statVariables)
            )
            val statVariableService = mockk<RPKStatVariableService>()
            every { statVariableService.statVariables } returns statVariables
            Services.delegate = servicesDelegate(
                minecraftProfileService = minecraftProfileService(recording.player, minecraftProfile),
                characterService = characterService(minecraftProfile, character),
                statService = statService,
                statVariableService = statVariableService
            )

            runStatsCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(LIST_TITLE, "strength: 7", "agility: 3")
        }

        "send only the title when there are no stats" {
            val recording = recordingPlayer()
            val plugin = plugin()
            val minecraftProfile = mockk<RPKMinecraftProfile>()
            val statService = mockk<RPKStatService>()
            every { statService.stats } returns emptyList()
            val statVariableService = mockk<RPKStatVariableService>()
            every { statVariableService.statVariables } returns emptyList()
            Services.delegate = servicesDelegate(
                minecraftProfileService = minecraftProfileService(recording.player, minecraftProfile),
                characterService = characterService(minecraftProfile, mockk()),
                statService = statService,
                statVariableService = statVariableService
            )

            runStatsCommand(plugin, recording.player) shouldBe true

            recording.sentLines shouldBe listOf(LIST_TITLE)
        }
    }

})
