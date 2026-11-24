package pl.mafia.engine.bots

import pl.mafia.engine.Game
import pl.mafia.engine.GameConfig
import pl.mafia.engine.RoleId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InformedBotTest {

    // 0 mafioso, 1 doctor, 2 detective, 3-5 citizens
    private val roles = listOf(
        RoleId.MAFIOSO, RoleId.DOCTOR, RoleId.DETECTIVE,
        RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN,
    )

    private val bot = InformedBot(Random(1), bluffChance = 1.0)

    private fun dayGame(): Game {
        val g = Game.withRoles(roles)
        g.resolveNight()
        return g
    }

    @Test
    fun `detective announces what they found`() {
        val g = Game.withRoles(roles)
        g.submitNightAction(2, 0)
        g.resolveNight()
        assertEquals(listOf(ClaimIntent(0, true)), bot.chooseClaims(g.viewFor(2)))
    }

    @Test
    fun `detective does not repeat a claim`() {
        val g = Game.withRoles(roles)
        g.submitNightAction(2, 0)
        g.resolveNight()
        g.submitClaim(2, 0, true)
        assertEquals(emptyList(), bot.chooseClaims(g.viewFor(2)))
    }

    @Test
    fun `citizen votes for the player accused by a claim`() {
        val g = dayGame()
        g.submitClaim(2, 0, true)
        assertEquals(0, bot.chooseVote(g.viewFor(3)))
    }

    @Test
    fun `citizen votes for someone who falsely accuses them`() {
        val g = dayGame()
        g.submitClaim(0, 3, true)
        assertEquals(0, bot.chooseVote(g.viewFor(3)))
    }

    @Test
    fun `detective votes for a mafioso they found even without any claims`() {
        val g = Game.withRoles(roles)
        g.submitNightAction(2, 0)
        g.resolveNight()
        assertEquals(0, bot.chooseVote(g.viewFor(2)))
    }

    @Test
    fun `mafia targets the player who accused them`() {
        val g = dayGame()
        g.submitClaim(2, 0, true)
        g.alivePlayers().forEach { g.submitVote(it.id, null) }
        g.resolveDay()
        assertEquals(2, bot.chooseNightTarget(g.viewFor(0)))
    }

    @Test
    fun `mafia bluff counters an accuser`() {
        val g = dayGame()
        g.submitClaim(2, 0, true)
        assertEquals(listOf(ClaimIntent(2, true)), bot.chooseClaims(g.viewFor(0)))
    }

    @Test
    fun `mafia bluff frames a non-teammate when nobody accused them`() {
        val g = dayGame()
        val claim = bot.chooseClaims(g.viewFor(0)).single()
        assertTrue(claim.claimsMafia)
        assertTrue(claim.targetId != 0)
    }

    @Test
    fun `doctor protects the player who accused someone`() {
        val g = dayGame()
        g.submitClaim(2, 0, true)
        g.alivePlayers().forEach { g.submitVote(it.id, null) }
        g.resolveDay()
        assertEquals(2, bot.chooseNightTarget(g.viewFor(1)))
    }

    // 0-1 mafia, 2 doctor, 3 detective, 4 day shooter, 5 joker, 6-7 citizens
    private val extendedRoles = listOf(
        RoleId.MAFIOSO, RoleId.MAFIOSO, RoleId.DOCTOR, RoleId.DETECTIVE,
        RoleId.DAY_SHOOTER, RoleId.JOKER, RoleId.CITIZEN, RoleId.CITIZEN,
    )

    private fun extendedDayGame(): Game {
        val g = Game.withRoles(extendedRoles)
        g.resolveNight()
        return g
    }

    @Test
    fun `shooter shoots a proven liar`() {
        val g = extendedDayGame()
        g.submitClaim(0, 4, true)
        assertEquals(0, bot.chooseShot(g.viewFor(4)))
    }

    @Test
    fun `shooter holds fire on weak evidence`() {
        val g = extendedDayGame()
        g.submitClaim(3, 0, true)
        assertNull(bot.chooseShot(g.viewFor(4)))
    }

    @Test
    fun `non-shooters never shoot`() {
        val g = extendedDayGame()
        assertNull(bot.chooseShot(g.viewFor(6)))
    }

    @Test
    fun `joker accuses someone else`() {
        val g = extendedDayGame()
        val claim = bot.chooseClaims(g.viewFor(5)).single()
        assertTrue(claim.claimsMafia)
        assertTrue(claim.targetId != 5)
    }

    @Test
    fun `a revealed joker's claims are not trusted as town claims`() {
        val g = extendedDayGame()
        g.submitClaim(5, 6, true)
        g.alivePlayers().forEach { g.submitVote(it.id, null) }
        g.resolveDay()
        g.submitNightAction(0, 5)
        g.resolveNight()
        assertEquals(RoleId.JOKER, g.viewFor(4).revealedRoles[5])
        assertNull(bot.chooseShot(g.viewFor(4)))
    }

    @Test
    fun `claims from a revealed town player are fully trusted`() {
        val g = Game.withRoles(roles, GameConfig(killOnFirstNight = true))
        g.resolveNight()
        g.submitClaim(2, 0, true)
        g.alivePlayers().forEach { g.submitVote(it.id, if (it.id == 2) null else 2) }
        g.resolveDay()
        g.resolveNight()
        assertEquals(0, bot.chooseVote(g.viewFor(3)))
    }
}
