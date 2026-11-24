package pl.mafia.engine

import pl.mafia.engine.bots.ClaimIntent
import pl.mafia.engine.bots.InformedBot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoisonAndSheriffTest {

    // 0 mafioso, 1 poisoner, 2 blackmailer, 3 doctor, 4 detective, 5 sheriff, 6 mayor, 7-9 citizens
    private val roles = listOf(
        RoleId.MAFIOSO, RoleId.POISONER, RoleId.BLACKMAILER, RoleId.DOCTOR, RoleId.DETECTIVE,
        RoleId.SHERIFF, RoleId.MAYOR, RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN,
    )

    private fun game() = Game.withRoles(roles, GameConfig(killOnFirstNight = true))

    private fun Game.nextNight() {
        alivePlayers().filter { canVote(it.id) }.forEach { submitVote(it.id, null) }
        resolveDay()
    }

    @Test
    fun `poisoned player dies at the end of the next night`() {
        val g = game()
        g.submitNightAction(1, 7)
        g.resolveNight()
        assertTrue(g.player(7).alive)
        assertTrue(g.viewFor(7).poisoned)
        assertFalse(g.viewFor(8).poisoned)
        g.nextNight()
        g.resolveNight()
        assertFalse(g.player(7).alive)
        assertTrue(g.events.any { it == GameEvent.PoisonKilled(7) })
        assertEquals(RoleId.CITIZEN, g.viewFor(8).revealedRoles[7])
        assertEquals(mapOf(1 to emptyList(), 2 to listOf(7)), g.viewFor(8).nightDeaths.let { mapOf(1 to (it[1] ?: emptyList()), 2 to (it[2] ?: emptyList())) })
    }

    @Test
    fun `doctor cures a poisoned player by protecting them the next night`() {
        val g = game()
        g.submitNightAction(1, 7)
        g.resolveNight()
        g.nextNight()
        g.submitNightAction(3, 7)
        g.resolveNight()
        assertTrue(g.player(7).alive)
        assertFalse(g.viewFor(7).poisoned)
        assertTrue(g.events.any { it == GameEvent.PoisonCured(7) })
    }

    @Test
    fun `blackmailer stops the doctor's cure`() {
        val g = game()
        g.submitNightAction(1, 7)
        g.resolveNight()
        g.nextNight()
        g.submitNightAction(3, 7)
        g.submitNightAction(2, 3)
        g.resolveNight()
        assertFalse(g.player(7).alive)
    }

    @Test
    fun `poison does nothing to a player who died another way`() {
        val g = game()
        g.submitNightAction(1, 7)
        g.submitNightAction(0, 7)
        g.resolveNight()
        assertFalse(g.player(7).alive)
        assertFalse(g.events.any { it is GameEvent.PlayerPoisoned })
        g.nextNight()
        g.resolveNight()
        assertFalse(g.events.any { it is GameEvent.PoisonKilled })
    }

    @Test
    fun `poisoner has two uses and cannot poison the same player twice`() {
        val g = game()
        g.submitNightAction(1, 7)
        g.resolveNight()
        g.nextNight()
        assertFalse(7 in g.legalNightTargets(1))
        g.submitNightAction(1, 8)
        g.resolveNight()
        g.nextNight()
        assertEquals(emptyList(), g.legalNightTargets(1))
    }

    @Test
    fun `sheriff learns the exact role and has one check`() {
        val g = game()
        g.submitNightAction(5, 1)
        g.resolveNight()
        assertEquals(listOf(RoleCheck(5, 1, RoleId.POISONER)), g.viewFor(5).roleChecks)
        assertEquals(emptyList(), g.viewFor(4).roleChecks)
        g.nextNight()
        assertEquals(emptyList(), g.legalNightTargets(5))
    }

    @Test
    fun `a blocked sheriff keeps the check`() {
        val g = game()
        g.submitNightAction(2, 5)
        g.submitNightAction(5, 1)
        g.resolveNight()
        assertEquals(emptyList(), g.viewFor(5).roleChecks)
        g.nextNight()
        assertTrue(1 in g.legalNightTargets(5))
    }

    @Test
    fun `a blackmailed player cannot vote the next day`() {
        val g = game()
        g.submitNightAction(2, 7)
        g.resolveNight()
        assertTrue(g.viewFor(7).blockedLastNight)
        assertFalse(g.canVote(7))
        assertEquals(emptyList(), g.legalVoteTargets(7))
        assertFailsWith<IllegalArgumentException> { g.submitVote(7, 8) }
        assertTrue(g.canVote(8))
        g.nextNight()
        g.resolveNight()
        assertTrue(g.canVote(7))
    }

    @Test
    fun `a lone blackmailer or poisoner takes over the kill`() {
        val g = Game.withRoles(
            listOf(RoleId.POISONER, RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN, RoleId.DETECTIVE, RoleId.CITIZEN, RoleId.CITIZEN),
            GameConfig(killOnFirstNight = true),
        )
        g.submitNightAction(0, 1)
        g.resolveNight()
        assertFalse(g.player(1).alive)
    }

    @Test
    fun `mayor's vote counts double`() {
        val g = game()
        g.resolveNight()
        g.alivePlayers().forEach { p ->
            g.submitVote(
                p.id,
                when (p.id) {
                    6, 4 -> 8
                    0, 1 -> 7
                    2 -> 4
                    3 -> 5
                    5 -> 1
                    7 -> 0
                    8 -> 2
                    else -> 3
                },
            )
        }
        g.resolveDay()
        assertFalse(g.player(8).alive)
        assertTrue(g.player(7).alive)
    }

    @Test
    fun `poisoned doctor protects themselves`() {
        val g = game()
        g.submitNightAction(1, 3)
        g.resolveNight()
        g.nextNight()
        assertEquals(3, InformedBot(Random(1)).chooseNightTarget(g.viewFor(3)))
    }

    @Test
    fun `sheriff bot announces the role check as a claim`() {
        val g = game()
        g.submitNightAction(5, 1)
        g.resolveNight()
        assertEquals(listOf(ClaimIntent(1, true)), InformedBot(Random(1)).chooseClaims(g.viewFor(5)))
    }
}
