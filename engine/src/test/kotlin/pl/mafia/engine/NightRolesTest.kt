package pl.mafia.engine

import pl.mafia.engine.bots.ClaimIntent
import pl.mafia.engine.bots.InformedBot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NightRolesTest {

    // 0 godfather, 1 blackmailer, 2 doctor, 3 detective, 4 bodyguard, 5 lookout, 6-8 citizens
    private val roles = listOf(
        RoleId.GODFATHER, RoleId.BLACKMAILER, RoleId.DOCTOR, RoleId.DETECTIVE,
        RoleId.BODYGUARD, RoleId.LOOKOUT, RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN,
    )

    private fun game(config: GameConfig = GameConfig(killOnFirstNight = true)) = Game.withRoles(roles, config)

    @Test
    fun `detective sees the godfather as not mafia`() {
        val g = game()
        g.submitNightAction(3, 0)
        g.resolveNight()
        assertEquals(listOf(Investigation(3, 0, false)), g.viewFor(3).investigations)
    }

    @Test
    fun `detective still sees the blackmailer as mafia`() {
        val g = game()
        g.submitNightAction(3, 1)
        g.resolveNight()
        assertEquals(listOf(Investigation(3, 1, true)), g.viewFor(3).investigations)
    }

    @Test
    fun `godfather kills and counts as mafia`() {
        val g = game()
        g.submitNightAction(0, 6)
        g.resolveNight()
        assertFalse(g.player(6).alive)
        assertEquals(listOf(1), g.viewFor(0).mafiaTeammates)
    }

    @Test
    fun `bodyguard dies instead of the guarded target`() {
        val g = game()
        g.submitNightAction(4, 6)
        g.submitNightAction(0, 6)
        g.resolveNight()
        assertTrue(g.player(6).alive)
        assertFalse(g.player(4).alive)
        assertTrue(g.events.any { it == GameEvent.BodyguardSacrificed(4, 6) })
        assertEquals(RoleId.BODYGUARD, g.viewFor(2).revealedRoles[4])
    }

    @Test
    fun `bodyguard does nothing when the target is not attacked`() {
        val g = game()
        g.submitNightAction(4, 7)
        g.submitNightAction(0, 6)
        g.resolveNight()
        assertFalse(g.player(6).alive)
        assertTrue(g.player(4).alive)
    }

    @Test
    fun `doctor protection beats the bodyguard`() {
        val g = game()
        g.submitNightAction(4, 6)
        g.submitNightAction(2, 6)
        g.submitNightAction(0, 6)
        g.resolveNight()
        assertTrue(g.player(6).alive)
        assertTrue(g.player(4).alive)
    }

    @Test
    fun `bodyguard cannot guard themselves`() {
        val g = game()
        assertFailsWith<IllegalArgumentException> { g.submitNightAction(4, 4) }
    }

    @Test
    fun `blackmailer cancels the doctor`() {
        val g = game()
        g.submitNightAction(1, 2)
        g.submitNightAction(2, 6)
        g.submitNightAction(0, 6)
        g.resolveNight()
        assertFalse(g.player(6).alive)
        assertTrue(g.viewFor(2).blockedLastNight)
        assertFalse(g.viewFor(7).blockedLastNight)
        assertTrue(g.events.any { it == GameEvent.ActionBlocked(1, 2) })
    }

    @Test
    fun `blackmailer cancels the detective and the bodyguard`() {
        val g = game()
        g.submitNightAction(1, 3)
        g.submitNightAction(3, 6)
        g.resolveNight()
        assertEquals(emptyList(), g.viewFor(3).investigations)

        val h = game()
        h.submitNightAction(1, 4)
        h.submitNightAction(4, 6)
        h.submitNightAction(0, 6)
        h.resolveNight()
        assertFalse(h.player(6).alive)
        assertTrue(h.player(4).alive)
    }

    @Test
    fun `blackmailer cannot target the mafia`() {
        val g = game()
        assertFailsWith<IllegalArgumentException> { g.submitNightAction(1, 0) }
    }

    @Test
    fun `a blocked doctor may protect the same target again`() {
        val g = game()
        g.submitNightAction(2, 6)
        g.resolveNight()
        g.resolveDay()
        g.submitNightAction(1, 2)
        g.submitNightAction(2, 7)
        g.resolveNight()
        g.resolveDay()
        assertTrue(6 in g.legalNightTargets(2))
    }

    @Test
    fun `blackmailer does not take part in the kill vote`() {
        val g = game()
        g.submitNightAction(1, 6)
        assertNull(g.viewFor(0).teammateKillChoice)
        g.submitNightAction(0, 7)
        g.resolveNight()
        assertFalse(g.player(7).alive)
        assertTrue(g.player(6).alive)
    }

    @Test
    fun `lookout sees everyone who visited the target`() {
        val g = game()
        g.submitNightAction(5, 6)
        g.submitNightAction(0, 6)
        g.submitNightAction(3, 6)
        g.submitNightAction(4, 7)
        g.resolveNight()
        assertEquals(listOf(Sighting(5, 1, 6, listOf(0, 3))), g.viewFor(5).sightings)
        assertEquals(emptyList(), g.viewFor(3).sightings)
        assertEquals(mapOf(1 to listOf(6)), g.viewFor(2).nightDeaths)
    }

    @Test
    fun `a blocked lookout learns nothing`() {
        val g = game()
        g.submitNightAction(1, 5)
        g.submitNightAction(5, 6)
        g.resolveNight()
        assertEquals(emptyList(), g.viewFor(5).sightings)
    }

    @Test
    fun `lookout accuses the visitors of a night victim`() {
        val g = game()
        g.submitNightAction(5, 6)
        g.submitNightAction(0, 6)
        g.resolveNight()
        val bot = InformedBot(Random(1))
        assertEquals(listOf(ClaimIntent(0, true)), bot.chooseClaims(g.viewFor(5)))
    }

    @Test
    fun `lookout stays quiet when the target survived`() {
        val g = game()
        g.submitNightAction(5, 7)
        g.submitNightAction(0, 6)
        g.resolveNight()
        assertEquals(emptyList(), InformedBot(Random(1)).chooseClaims(g.viewFor(5)))
    }
}
