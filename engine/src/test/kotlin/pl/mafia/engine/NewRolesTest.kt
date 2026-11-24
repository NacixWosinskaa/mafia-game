package pl.mafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewRolesTest {

    // 0-1 mafia, 2 doctor, 3 detective, 4 day shooter, 5 joker, 6-7 citizens
    private val roles = listOf(
        RoleId.MAFIOSO, RoleId.MAFIOSO, RoleId.DOCTOR, RoleId.DETECTIVE,
        RoleId.DAY_SHOOTER, RoleId.JOKER, RoleId.CITIZEN, RoleId.CITIZEN,
    )

    private fun dayGame(config: GameConfig = GameConfig()): Game {
        val g = Game.withRoles(roles, config)
        g.resolveNight()
        return g
    }

    private fun Game.everyoneVotes(target: (PlayerId) -> PlayerId?) {
        alivePlayers().forEach { submitVote(it.id, target(it.id)) }
    }

    @Test
    fun `joker wins when executed by vote`() {
        val g = dayGame()
        g.everyoneVotes { id -> if (id == 5) 0 else 5 }
        g.resolveDay()
        assertTrue(g.isOver)
        assertEquals(Faction.NEUTRAL, g.winner)
    }

    @Test
    fun `joker killed at night does not win`() {
        val g = Game.withRoles(roles, GameConfig(killOnFirstNight = true))
        g.submitNightAction(0, 5)
        g.resolveNight()
        assertFalse(g.player(5).alive)
        assertFalse(g.isOver)
        assertNull(g.winner)
    }

    @Test
    fun `joker shot by the day shooter does not win`() {
        val g = dayGame()
        g.submitDayShot(4, 5)
        assertFalse(g.isOver)
        assertNull(g.winner)
    }

    @Test
    fun `detective sees the joker as not mafia`() {
        val g = Game.withRoles(roles)
        g.submitNightAction(3, 5)
        g.resolveNight()
        assertEquals(listOf(Investigation(3, 5, false)), g.viewFor(3).investigations)
    }

    @Test
    fun `day shooter kills the target immediately and reveals their role`() {
        val g = dayGame()
        g.submitDayShot(4, 6)
        assertFalse(g.player(6).alive)
        assertEquals(RoleId.CITIZEN, g.viewFor(2).revealedRoles[6])
        assertTrue(g.events.any { it == GameEvent.PlayerShot(4, 6, RoleId.CITIZEN) })
    }

    @Test
    fun `day shooter has a single shot`() {
        val g = dayGame()
        g.submitDayShot(4, 6)
        assertEquals(emptyList(), g.legalShotTargets(4))
        assertFailsWith<IllegalArgumentException> { g.submitDayShot(4, 7) }
    }

    @Test
    fun `only the day shooter can shoot, and only during the day`() {
        val night = Game.withRoles(roles)
        assertFailsWith<IllegalStateException> { night.submitDayShot(4, 6) }
        val day = dayGame()
        assertFailsWith<IllegalArgumentException> { day.submitDayShot(6, 7) }
    }

    @Test
    fun `a shot can end the game`() {
        val g = Game.withRoles(
            listOf(RoleId.MAFIOSO, RoleId.DOCTOR, RoleId.DETECTIVE, RoleId.DAY_SHOOTER, RoleId.CITIZEN, RoleId.CITIZEN),
        )
        g.resolveNight()
        g.submitDayShot(3, 0)
        assertTrue(g.isOver)
        assertEquals(Faction.TOWN, g.winner)
    }

    @Test
    fun `votes by and for a shot player are dropped`() {
        val g = dayGame()
        g.submitVote(6, 0)
        g.submitVote(7, 6)
        g.submitDayShot(4, 6)
        g.everyoneVotes { null }
        g.resolveDay()
        val tallied = g.events.filterIsInstance<GameEvent.VotesTallied>().last()
        assertFalse(tallied.votes.containsKey(6))
        assertTrue(tallied.votes.values.none { it == 6 })
    }

    @Test
    fun `role setups include the new roles only from the planned sizes`() {
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            val setup = RoleSetups.forPlayers(n)
            assertEquals(n >= 8, RoleId.JOKER in setup, "joker for $n")
            assertEquals(n >= 10, RoleId.DAY_SHOOTER in setup, "shooter for $n")
            assertTrue(RoleId.CITIZEN in setup, "at least one citizen for $n")
        }
    }
}
