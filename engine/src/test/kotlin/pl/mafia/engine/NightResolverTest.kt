package pl.mafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NightResolverTest {

    // id: 0 mafioso, 1 lekarz, 2 detektyw, 3-5 obywatele
    private val players = listOf(
        Player(0, "A", RoleId.MAFIOSO),
        Player(1, "B", RoleId.DOCTOR),
        Player(2, "C", RoleId.DETECTIVE),
        Player(3, "D", RoleId.CITIZEN),
        Player(4, "E", RoleId.CITIZEN),
        Player(5, "F", RoleId.CITIZEN),
    )

    @Test
    fun `nieochroniony cel ginie`() {
        val result = NightResolver.resolve(players, mapOf(0 to 3), killAllowed = true)
        assertEquals(listOf(3), result.deaths)
        assertNull(result.preventedAttackOn)
    }

    @Test
    fun `ochrona lekarza blokuje zabojstwo`() {
        val result = NightResolver.resolve(players, mapOf(0 to 3, 1 to 3), killAllowed = true)
        assertEquals(emptyList(), result.deaths)
        assertEquals(3, result.preventedAttackOn)
    }

    @Test
    fun `ochrona innej osoby nie ratuje celu`() {
        val result = NightResolver.resolve(players, mapOf(0 to 3, 1 to 4), killAllowed = true)
        assertEquals(listOf(3), result.deaths)
    }

    @Test
    fun `zakaz zabojstw w pierwszej nocy`() {
        val result = NightResolver.resolve(players, mapOf(0 to 3), killAllowed = false)
        assertEquals(emptyList(), result.deaths)
        assertNull(result.attackedId)
    }

    @Test
    fun `detektyw widzi frakcje celu`() {
        val onMafia = NightResolver.resolve(players, mapOf(2 to 0), killAllowed = true)
        val onCitizen = NightResolver.resolve(players, mapOf(2 to 3), killAllowed = true)
        assertEquals(listOf(Investigation(2, 0, true)), onMafia.investigations)
        assertEquals(listOf(Investigation(2, 3, false)), onCitizen.investigations)
    }

    @Test
    fun `remis wsrod mafiosow rozstrzyga najnizsze id celu`() {
        val twoMafia = players.toMutableList().also { it[5] = Player(5, "F", RoleId.MAFIOSO) }
        val result = NightResolver.resolve(twoMafia, mapOf(0 to 4, 5 to 3), killAllowed = true)
        assertEquals(listOf(3), result.deaths)
    }
}
