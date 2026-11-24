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
    fun `every setup has exactly one detective and one doctor`() {
        for ((n, roles) in allSetups()) {
            assertEquals(1, roles.count { it == RoleId.DETECTIVE }, "detectives for $n")
            assertEquals(1, roles.count { it == RoleId.DOCTOR }, "doctors for $n")
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
            assertTrue(roles.count { it == RoleId.MAFIOSO } * 2 < n, "mafia minority for $n")
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
        val counts = (RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS).map { RoleSetups.combinations(it).first().size }
        assertEquals(counts.sorted(), counts)
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
