package pl.mafia.engine

import kotlin.random.Random

// A setup = mafia + detective + doctor + a few special roles + citizens.
// Special roles are drawn without replacement from a pool, so none repeats within a game.
// Counts below are a draft; use the simulator's `setups` report to tune them.
object RoleSetups {
    const val MIN_PLAYERS = 6
    const val MAX_PLAYERS = 14

    private val mafiaCount: Map<Int, Int> = mapOf(
        6 to 1, 7 to 2, 8 to 2, 9 to 2, 10 to 3, 11 to 3, 12 to 3, 13 to 4, 14 to 4,
    )

    private val specialSlots: Map<Int, Int> = mapOf(
        6 to 0, 7 to 0, 8 to 1, 9 to 1, 10 to 2, 11 to 2, 12 to 3, 13 to 3, 14 to 3,
    )

    private val core = listOf(RoleId.DETECTIVE, RoleId.DOCTOR)

    private val specialPool = listOf(RoleId.DAY_SHOOTER, RoleId.AVENGER, RoleId.JOKER)

    // Special-role combinations dropped after looking at simulator results.
    private val rejected: Map<Int, Set<Set<RoleId>>> = emptyMap()

    // Small games stay readable: fewer neutral and "bait" roles.
    private fun limit(playerCount: Int) = if (playerCount >= 12) 2 else 1

    private fun isValid(playerCount: Int, specials: List<RoleId>): Boolean {
        val definitions = specials.map { Roles.of(it) }
        return definitions.count { it.faction == Faction.NEUTRAL } <= limit(playerCount) &&
            definitions.count { it.bait } <= limit(playerCount) &&
            specials.toSet() !in rejected[playerCount].orEmpty()
    }

    private fun subsets(pool: List<RoleId>, size: Int): List<List<RoleId>> = when {
        size == 0 -> listOf(emptyList())
        pool.size < size -> emptyList()
        else -> subsets(pool.drop(1), size - 1).map { listOf(pool.first()) + it } + subsets(pool.drop(1), size)
    }

    // Every allowed set of special roles for this player count.
    fun combinations(playerCount: Int): List<List<RoleId>> {
        requireSupported(playerCount)
        val size = minOf(specialSlots.getValue(playerCount), specialPool.size)
        return subsets(specialPool, size).filter { isValid(playerCount, it) }
    }

    fun build(playerCount: Int, specials: List<RoleId>): List<RoleId> {
        requireSupported(playerCount)
        val mafia = mafiaCount.getValue(playerCount)
        val citizens = playerCount - mafia - core.size - specials.size
        require(citizens >= 1) { "Too many special roles for $playerCount players" }
        return List(mafia) { RoleId.MAFIOSO } + core + specials + List(citizens) { RoleId.CITIZEN }
    }

    fun forPlayers(playerCount: Int, random: Random = Random.Default): List<RoleId> =
        build(playerCount, combinations(playerCount).random(random))

    private fun requireSupported(playerCount: Int) {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "Player count must be in $MIN_PLAYERS..$MAX_PLAYERS, got $playerCount"
        }
    }
}
