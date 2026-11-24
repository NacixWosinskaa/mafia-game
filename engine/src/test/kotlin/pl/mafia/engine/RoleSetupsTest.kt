package pl.mafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoleSetupsTest {

    @Test
    fun `setup size matches player count`() {
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            assertEquals(n, RoleSetups.forPlayers(n).size, "roles for $n players")
        }
    }

    @Test
    fun `every setup has one detective, one doctor and a mafia minority`() {
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            val roles = RoleSetups.forPlayers(n)
            assertEquals(1, roles.count { it == RoleId.DETECTIVE }, "detectives for $n")
            assertEquals(1, roles.count { it == RoleId.DOCTOR }, "doctors for $n")
            val mafia = roles.count { it == RoleId.MAFIOSO }
            assertTrue(mafia >= 1, "at least one mafioso for $n")
            assertTrue(mafia * 2 < n, "mafia must be a minority for $n")
        }
    }

    @Test
    fun `player count outside the range is rejected`() {
        assertFailsWith<IllegalArgumentException> { RoleSetups.forPlayers(5) }
        assertFailsWith<IllegalArgumentException> { RoleSetups.forPlayers(13) }
    }
}
