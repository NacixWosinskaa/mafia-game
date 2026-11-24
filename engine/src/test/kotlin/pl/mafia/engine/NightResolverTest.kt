package pl.mafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NightResolverTest {

    // 0 mafioso, 1 doctor, 2 detective, 3-5 citizens
    private val players = listOf(
        Player(0, "A", RoleId.MAFIOSO),
        Player(1, "B", RoleId.DOCTOR),
        Player(2, "C", RoleId.DETECTIVE),
        Player(3, "D", RoleId.CITIZEN),
        Player(4, "E", RoleId.CITIZEN),
        Player(5, "F", RoleId.CITIZEN),
    )

    @Test
    fun `unprotected target dies`() {
        val result = NightResolver.resolve(players, mapOf(0 to 3), killAllowed = true)
        assertEquals(listOf(3), result.deaths)
        assertNull(result.preventedAttackOn)
    }

    @Test
    fun `doctor protection blocks the kill`() {
        val result = NightResolver.resolve(players, mapOf(0 to 3, 1 to 3), killAllowed = true)
        assertEquals(emptyList(), result.deaths)
        assertEquals(3, result.preventedAttackOn)
    }

    @Test
    fun `protecting someone else does not save the target`() {
        val result = NightResolver.resolve(players, mapOf(0 to 3, 1 to 4), killAllowed = true)
        assertEquals(listOf(3), result.deaths)
    }

    @Test
    fun `no kills when kills are not allowed`() {
        val result = NightResolver.resolve(players, mapOf(0 to 3), killAllowed = false)
        assertEquals(emptyList(), result.deaths)
        assertNull(result.attackedId)
    }

    @Test
    fun `detective sees the target's faction`() {
        val onMafia = NightResolver.resolve(players, mapOf(2 to 0), killAllowed = true)
        val onCitizen = NightResolver.resolve(players, mapOf(2 to 3), killAllowed = true)
        assertEquals(listOf(Investigation(2, 0, true)), onMafia.investigations)
        assertEquals(listOf(Investigation(2, 3, false)), onCitizen.investigations)
    }

    @Test
    fun `mafia vote tie goes to the lowest target id`() {
        val twoMafia = players.toMutableList().also { it[5] = Player(5, "F", RoleId.MAFIOSO) }
        val result = NightResolver.resolve(twoMafia, mapOf(0 to 4, 5 to 3), killAllowed = true)
        assertEquals(listOf(3), result.deaths)
    }
}
