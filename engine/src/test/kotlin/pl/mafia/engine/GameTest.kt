package pl.mafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameTest {

    // 0 mafioso, 1 doctor, 2 detective, 3-5 citizens
    private val roles = listOf(
        RoleId.MAFIOSO, RoleId.DOCTOR, RoleId.DETECTIVE,
        RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN,
    )

    private fun game(config: GameConfig = GameConfig()) = Game.withRoles(roles, config)

    private fun Game.everyoneVotes(target: (PlayerId) -> PlayerId?) {
        alivePlayers().forEach { submitVote(it.id, target(it.id)) }
    }

    @Test
    fun `first night has no kill by default`() {
        val g = game()
        g.submitNightAction(0, 3)
        g.resolveNight()
        assertEquals(6, g.alivePlayers().size)
        assertEquals(Phase.DAY, g.phase)
    }

    @Test
    fun `mafia can kill on the first night when enabled`() {
        val g = game(GameConfig(killOnFirstNight = true))
        g.submitNightAction(0, 3)
        g.resolveNight()
        assertFalse(g.player(3).alive)
        assertTrue(g.events.any { it is GameEvent.PlayerKilled && it.playerId == 3 })
    }

    @Test
    fun `doctor saves the victim`() {
        val g = game(GameConfig(killOnFirstNight = true))
        g.submitNightAction(0, 3)
        g.submitNightAction(1, 3)
        g.resolveNight()
        assertTrue(g.player(3).alive)
        assertTrue(g.events.any { it is GameEvent.AttackPrevented && it.targetId == 3 })
    }

    @Test
    fun `mafia cannot target its own`() {
        val g = Game.withRoles(
            listOf(RoleId.MAFIOSO, RoleId.MAFIOSO, RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN),
        )
        assertFailsWith<IllegalArgumentException> { g.submitNightAction(0, 1) }
    }

    @Test
    fun `citizen has no night action`() {
        val g = game()
        assertFailsWith<IllegalArgumentException> { g.submitNightAction(3, 0) }
    }

    @Test
    fun `detective result is only in the detective's view`() {
        val g = game()
        g.submitNightAction(2, 0)
        g.resolveNight()
        assertEquals(listOf(Investigation(2, 0, true)), g.viewFor(2).investigations)
        assertEquals(emptyList(), g.viewFor(3).investigations)
    }

    @Test
    fun `citizen view does not reveal other roles`() {
        val g = game()
        val view = g.viewFor(3)
        assertEquals(RoleId.CITIZEN, view.role)
        assertEquals(emptyList(), view.mafiaTeammates)
        assertEquals(emptyMap(), view.revealedRoles)
    }

    @Test
    fun `doctor cannot protect the same player two nights in a row`() {
        val g = game()
        g.submitNightAction(1, 3)
        g.resolveNight()
        g.everyoneVotes { null }
        g.resolveDay()
        assertEquals(Phase.NIGHT, g.phase)
        val targets = g.legalNightTargets(1)
        assertFalse(3 in targets)
        assertTrue(1 in targets, "doctor can protect themselves")
        assertFailsWith<IllegalArgumentException> { g.submitNightAction(1, 3) }
    }

    @Test
    fun `doctor self-protection follows the config`() {
        val g = game(GameConfig(doctorCanSelfProtect = false))
        assertFalse(1 in g.legalNightTargets(1))
    }

    @Test
    fun `most votes executes a player`() {
        val g = game()
        g.resolveNight()
        g.everyoneVotes { id -> if (id == 3) 0 else 3 }
        g.resolveDay()
        assertFalse(g.player(3).alive)
        assertTrue(g.events.any { it is GameEvent.PlayerExecuted && it.playerId == 3 })
    }

    @Test
    fun `a tie means no execution`() {
        val g = game()
        g.resolveNight()
        g.everyoneVotes { id -> if (id in listOf(0, 2, 4)) 1 else 0 }
        g.resolveDay()
        assertEquals(6, g.alivePlayers().size)
        assertTrue(g.events.any { it == GameEvent.NoExecution(NoExecutionReason.TIE) })
    }

    @Test
    fun `a tie can be broken randomly when configured`() {
        val g = game(GameConfig(tieRule = TieRule.RANDOM_AMONG_TIED))
        g.resolveNight()
        g.everyoneVotes { id -> if (id in listOf(0, 2, 4)) 1 else 0 }
        g.resolveDay()
        assertEquals(5, g.alivePlayers().size)
    }

    @Test
    fun `votes for nobody execute nobody`() {
        val g = game()
        g.resolveNight()
        g.everyoneVotes { null }
        g.resolveDay()
        assertEquals(6, g.alivePlayers().size)
        assertTrue(g.events.any { it == GameEvent.NoExecution(NoExecutionReason.NOBODY_CHOSEN) })
    }

    @Test
    fun `town wins when the last mafioso is executed`() {
        val g = game()
        g.resolveNight()
        g.everyoneVotes { id -> if (id == 0) 1 else 0 }
        g.resolveDay()
        assertTrue(g.isOver)
        assertEquals(Faction.TOWN, g.winner)
    }

    @Test
    fun `mafia wins when it matches the rest in numbers`() {
        val g = Game.withRoles(
            listOf(RoleId.MAFIOSO, RoleId.MAFIOSO, RoleId.CITIZEN, RoleId.CITIZEN),
            GameConfig(killOnFirstNight = true),
        )
        g.submitNightAction(0, 2)
        g.resolveNight()
        assertTrue(g.isOver)
        assertEquals(Faction.MAFIA, g.winner)
    }

    @Test
    fun `nothing can be submitted after the game ends`() {
        val g = game()
        g.resolveNight()
        g.everyoneVotes { id -> if (id == 0) 1 else 0 }
        g.resolveDay()
        assertFailsWith<IllegalStateException> { g.submitVote(1, 2) }
        assertFailsWith<IllegalStateException> { g.resolveNight() }
    }
}
