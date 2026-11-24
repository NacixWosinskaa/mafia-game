package pl.mafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaimsTest {

    // 0 mafioso, 1 doctor, 2 detective, 3-5 citizens
    private val roles = listOf(
        RoleId.MAFIOSO, RoleId.DOCTOR, RoleId.DETECTIVE,
        RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN,
    )

    private fun dayGame(): Game {
        val g = Game.withRoles(roles)
        g.resolveNight()
        return g
    }

    @Test
    fun `claims are public and recorded as events`() {
        val g = dayGame()
        g.submitClaim(2, 0, true)
        assertEquals(listOf(Claim(2, 1, 0, true)), g.viewFor(3).claims)
        assertEquals(listOf(Claim(2, 1, 0, true)), g.viewFor(0).claims)
        assertTrue(g.events.any { it == GameEvent.ClaimMade(2, 0, true) })
    }

    @Test
    fun `claims are only allowed during the day`() {
        val g = Game.withRoles(roles)
        assertFailsWith<IllegalStateException> { g.submitClaim(2, 0, true) }
    }

    @Test
    fun `players cannot claim about themselves or repeat a claim`() {
        val g = dayGame()
        assertFailsWith<IllegalArgumentException> { g.submitClaim(2, 2, true) }
        g.submitClaim(2, 0, true)
        assertFailsWith<IllegalArgumentException> { g.submitClaim(2, 0, false) }
    }

    @Test
    fun `dead players cannot make claims`() {
        val g = Game.withRoles(roles, GameConfig(killOnFirstNight = true))
        g.submitNightAction(0, 3)
        g.resolveNight()
        assertFailsWith<IllegalArgumentException> { g.submitClaim(3, 0, true) }
    }

    @Test
    fun `vote history records who voted for whom and who was executed`() {
        val g = dayGame()
        g.alivePlayers().forEach { g.submitVote(it.id, if (it.id == 3) 0 else 3) }
        g.resolveDay()
        val history = g.viewFor(1).voteHistory.single()
        assertEquals(1, history.day)
        assertEquals(3, history.executedId)
        assertEquals(0, history.votes[3])
        assertEquals(3, history.votes[0])
    }
}
