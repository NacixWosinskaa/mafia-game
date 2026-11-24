package pl.mafia.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoleSetupsTest {

    private val repeatable = setOf(RoleId.CITIZEN, RoleId.MAFIOSO)

    private fun allSetups() = (RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS).flatMap { n ->
        RoleSetups.combinations(n).map { n to RoleSetups.build(n, it) }
    }

    @Test
    fun `every combination fills the table exactly`() {
        for ((n, roles) in allSetups()) assertEquals(n, roles.size, "roles for $n players")
    }

    @Test
    fun `every setup has exactly one detective and at most one doctor`() {
        for ((n, roles) in allSetups()) {
            assertEquals(1, roles.count { it == RoleId.DETECTIVE }, "detectives for $n")
            assertTrue(roles.count { it == RoleId.DOCTOR } <= 1, "doctors for $n")
        }
    }

    @Test
    fun `the doctor is optional below 8 players and guaranteed from 8`() {
        for (n in 6..7) {
            val hasDoctor = RoleSetups.combinations(n).map { RoleId.DOCTOR in RoleSetups.build(n, it) }
            assertTrue(true in hasDoctor && false in hasDoctor, "doctor can be in or out for $n")
        }
        for (n in 8..RoleSetups.MAX_PLAYERS) {
            assertTrue(RoleSetups.combinations(n).all { RoleId.DOCTOR in RoleSetups.build(n, it) }, "doctor for $n")
        }
    }

    @Test
    fun `no role repeats except citizen and mafioso`() {
        for ((n, roles) in allSetups()) {
            val unique = roles.filter { it !in repeatable }
            assertEquals(unique.size, unique.toSet().size, "duplicates for $n")
        }
    }

    @Test
    fun `mafia is a minority and citizens exist`() {
        for ((n, roles) in allSetups()) {
            assertTrue(roles.count { Roles.of(it).faction == Faction.MAFIA } * 2 < n, "mafia minority for $n")
            assertTrue(RoleId.CITIZEN in roles, "citizen for $n")
        }
    }

    @Test
    fun `neutral and bait roles respect the size limits`() {
        for ((n, roles) in allSetups()) {
            val limit = if (n >= 12) 2 else 1
            val definitions = roles.map { Roles.of(it) }
            assertTrue(definitions.count { it.faction == Faction.NEUTRAL } <= limit, "neutrals for $n")
            assertTrue(definitions.count { it.bait } <= limit, "baits for $n")
        }
    }

    @Test
    fun `larger games never get fewer special roles`() {
        val counts = (RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS).map { RoleSetups.combinations(it).first().specials.size }
        assertEquals(counts.sorted(), counts)
    }

    @Test
    fun `mafia count is drawn only for 10 and 11 players`() {
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            val counts = RoleSetups.combinations(n).map { it.mafia }.toSet()
            assertEquals(if (n in 10..11) setOf(2, 3) else 1, if (n in 10..11) counts else counts.size, "mafia counts for $n")
        }
    }

    @Test
    fun `mafia specials need 8 players and leave a plain mafioso`() {
        for ((n, roles) in allSetups()) {
            val special = roles.count { it == RoleId.GODFATHER || it == RoleId.BLACKMAILER }
            if (n < 8) assertEquals(0, special, "mafia specials for $n")
            assertTrue(RoleId.MAFIOSO in roles, "plain mafioso for $n")
            assertTrue(special <= if (n >= 12) 2 else 1, "mafia specials for $n")
        }
        assertTrue(allSetups().any { (_, roles) -> RoleId.GODFATHER in roles })
        assertTrue(allSetups().any { (_, roles) -> RoleId.BLACKMAILER in roles })
    }

    @Test
    fun `mafia headcount matches the table whatever the specials`() {
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            for (setup in RoleSetups.combinations(n)) {
                val roles = RoleSetups.build(n, setup)
                assertEquals(setup.mafia, roles.count { Roles.of(it).faction == Faction.MAFIA }, "mafia for $n")
            }
        }
    }

    @Test
    fun `random setups come from the allowed combinations`() {
        val random = Random(5)
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            val allowed = RoleSetups.combinations(n).map { RoleSetups.build(n, it).sortedBy { r -> r.ordinal } }
            repeat(50) {
                assertTrue(RoleSetups.forPlayers(n, random).sortedBy { r -> r.ordinal } in allowed, "setup for $n")
            }
        }
    }

    @Test
    fun `player count outside the range is rejected`() {
        assertFailsWith<IllegalArgumentException> { RoleSetups.forPlayers(5) }
        assertFailsWith<IllegalArgumentException> { RoleSetups.forPlayers(15) }
    }
}
