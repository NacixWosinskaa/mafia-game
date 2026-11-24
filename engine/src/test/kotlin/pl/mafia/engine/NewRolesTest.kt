package pl.mafia.engine

import kotlin.random.Random
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

    // 0-1 mafia, 2 doctor, 3 detective, 4 avenger, 5 day shooter, 6-7 citizens
    private val avengerRoles = listOf(
        RoleId.MAFIOSO, RoleId.MAFIOSO, RoleId.DOCTOR, RoleId.DETECTIVE,
        RoleId.AVENGER, RoleId.DAY_SHOOTER, RoleId.CITIZEN, RoleId.CITIZEN,
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
        val g = dayGame(GameConfig(executionWinFromDay = 1))
        g.everyoneVotes { id -> if (id == 5) 0 else 5 }
        g.resolveDay()
        assertTrue(g.isOver)
        assertEquals(Faction.NEUTRAL, g.winner)
    }

    @Test
    fun `joker executed before day three just dies by default`() {
        val g = dayGame()
        g.everyoneVotes { id -> if (id == 5) 0 else 5 }
        g.resolveDay()
        assertFalse(g.player(5).alive)
        assertFalse(g.isOver)
        assertNull(g.winner)
    }

    @Test
    fun `joker executed on day three wins by default`() {
        val g = dayGame()
        repeat(2) {
            g.everyoneVotes { null }
            g.resolveDay()
            g.resolveNight()
        }
        g.everyoneVotes { id -> if (id == 5) 0 else 5 }
        g.resolveDay()
        assertTrue(g.isOver)
        assertEquals(Faction.NEUTRAL, g.winner)
    }

    @Test
    fun `joker needs a majority of the living when that rule is on`() {
        val config = GameConfig(executionWinFromDay = 1, executionWinNeedsMajority = true)
        val plurality = dayGame(config)
        plurality.everyoneVotes { id ->
            when (id) {
                0, 1, 2 -> 5
                3, 4 -> 6
                6, 7 -> 3
                else -> 0
            }
        }
        plurality.resolveDay()
        assertFalse(plurality.player(5).alive)
        assertNull(plurality.winner)

        val majority = dayGame(config)
        majority.everyoneVotes { id -> if (id == 5) 0 else 5 }
        majority.resolveDay()
        assertEquals(Faction.NEUTRAL, majority.winner)
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
    fun `shooter who hits mafia survives`() {
        val g = dayGame()
        g.submitDayShot(4, 0)
        assertFalse(g.player(0).alive)
        assertTrue(g.player(4).alive)
    }

    @Test
    fun `shooter who misses dies with the target and both roles are revealed`() {
        val g = dayGame()
        g.submitDayShot(4, 6)
        assertFalse(g.player(6).alive)
        assertFalse(g.player(4).alive)
        assertEquals(RoleId.CITIZEN, g.viewFor(2).revealedRoles[6])
        assertEquals(RoleId.DAY_SHOOTER, g.viewFor(2).revealedRoles[4])
        assertTrue(g.events.any { it == GameEvent.PlayerShot(4, 6, RoleId.CITIZEN) })
        assertTrue(g.events.any { it == GameEvent.ShooterMissed(4, RoleId.DAY_SHOOTER) })
    }

    @Test
    fun `recoil can be switched off`() {
        val g = dayGame(GameConfig(shooterDiesOnMiss = false))
        g.submitDayShot(4, 6)
        assertTrue(g.player(4).alive)
    }

    @Test
    fun `day shooter has a single shot`() {
        val g = dayGame(GameConfig(shooterDiesOnMiss = false))
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
    fun `avenger killed by the mafia takes one mafioso along`() {
        val g = Game.withRoles(avengerRoles, GameConfig(killOnFirstNight = true), Random(1))
        g.submitNightAction(0, 4)
        g.resolveNight()
        assertFalse(g.player(4).alive)
        assertEquals(1, listOf(0, 1).count { !g.player(it).alive })
        assertTrue(g.events.any { it is GameEvent.RevengeTriggered && it.avengerId == 4 })
    }

    @Test
    fun `no revenge when the doctor saves the avenger`() {
        val g = Game.withRoles(avengerRoles, GameConfig(killOnFirstNight = true))
        g.submitNightAction(0, 4)
        g.submitNightAction(2, 4)
        g.resolveNight()
        assertTrue(g.player(4).alive)
        assertTrue(g.player(0).alive && g.player(1).alive)
    }

    @Test
    fun `no revenge when the avenger is shot during the day`() {
        val g = Game.withRoles(avengerRoles)
        g.resolveNight()
        g.submitDayShot(5, 4)
        assertFalse(g.player(4).alive)
        assertTrue(g.player(0).alive && g.player(1).alive)
    }

    @Test
    fun `revenge can end the game for the mafia`() {
        val g = Game.withRoles(
            listOf(RoleId.MAFIOSO, RoleId.AVENGER, RoleId.CITIZEN, RoleId.CITIZEN),
            GameConfig(killOnFirstNight = true),
        )
        g.submitNightAction(0, 1)
        g.resolveNight()
        assertTrue(g.isOver)
        assertEquals(Faction.TOWN, g.winner)
    }
}
