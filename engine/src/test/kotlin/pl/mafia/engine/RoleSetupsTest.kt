package pl.mafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoleSetupsTest {

    @Test
    fun `sklad ma dokladnie tylu graczy ile trzeba`() {
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            assertEquals(n, RoleSetups.forPlayers(n).size, "liczba ról dla $n graczy")
        }
    }

    @Test
    fun `kazdy sklad ma po jednym detektywie i lekarzu oraz mafie mniejsza niz polowa`() {
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            val roles = RoleSetups.forPlayers(n)
            assertEquals(1, roles.count { it == RoleId.DETECTIVE }, "detektyw dla $n")
            assertEquals(1, roles.count { it == RoleId.DOCTOR }, "lekarz dla $n")
            val mafia = roles.count { it == RoleId.MAFIOSO }
            assertTrue(mafia >= 1, "co najmniej jeden mafioso dla $n")
            assertTrue(mafia * 2 < n, "mafia musi być mniejszością dla $n")
        }
    }

    @Test
    fun `liczba graczy poza zakresem jest odrzucana`() {
        assertFailsWith<IllegalArgumentException> { RoleSetups.forPlayers(5) }
        assertFailsWith<IllegalArgumentException> { RoleSetups.forPlayers(13) }
    }
}
