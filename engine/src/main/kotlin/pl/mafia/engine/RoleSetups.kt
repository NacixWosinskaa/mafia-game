package pl.mafia.engine

import kotlin.random.Random

// A setup = mafia + detective + (doctor) + a few special roles + citizens.
// Special roles are drawn without replacement from a pool, so none repeats within a game.
// Counts below are a draft; use the simulator's `setups` report to tune them.
data class Setup(val mafia: Int, val specials: List<RoleId>)

object RoleSetups {
    const val MIN_PLAYERS = 6
    const val MAX_PLAYERS = 14

    // Below this size the doctor is only one of the optional roles.
    private const val DOCTOR_REQUIRED_FROM = 8

    // Several values mean the count is drawn per game (10-11 players: 2 or 3 mafiosi).
    private val mafiaOptions: Map<Int, List<Int>> = mapOf(
        6 to listOf(1), 7 to listOf(2), 8 to listOf(2), 9 to listOf(2), 10 to listOf(2, 3),
        11 to listOf(2, 3), 12 to listOf(3), 13 to listOf(3), 14 to listOf(3),
    )

    // How many special roles a game of this size may have; several values mean "one of these".
    private val specialSizes: Map<Int, List<Int>> = mapOf(
        6 to listOf(0, 1), 7 to listOf(0, 1), 8 to listOf(1), 9 to listOf(1),
        10 to listOf(2), 11 to listOf(2), 12 to listOf(3), 13 to listOf(3), 14 to listOf(3),
    )

    private val specialPool = listOf(RoleId.DAY_SHOOTER, RoleId.AVENGER, RoleId.JOKER)

    // Special-role combinations dropped after looking at simulator results.
    private val rejected: Map<Int, Set<Set<RoleId>>> = emptyMap()

    private fun coreRoles(playerCount: Int) =
        if (playerCount >= DOCTOR_REQUIRED_FROM) listOf(RoleId.DETECTIVE, RoleId.DOCTOR) else listOf(RoleId.DETECTIVE)

    private fun poolFor(playerCount: Int) =
        if (playerCount >= DOCTOR_REQUIRED_FROM) specialPool else listOf(RoleId.DOCTOR)

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

    // Every allowed setup for this player count.
    fun combinations(playerCount: Int): List<Setup> {
        requireSupported(playerCount)
        val pool = poolFor(playerCount)
        val specials = specialSizes.getValue(playerCount)
            .flatMap { subsets(pool, minOf(it, pool.size)) }
            .filter { isValid(playerCount, it) }
        return mafiaOptions.getValue(playerCount).flatMap { mafia -> specials.map { Setup(mafia, it) } }
    }

    fun build(playerCount: Int, setup: Setup): List<RoleId> {
        requireSupported(playerCount)
        val core = coreRoles(playerCount)
        val citizens = playerCount - setup.mafia - core.size - setup.specials.size
        require(citizens >= 1) { "Too many special roles for $playerCount players" }
        return List(setup.mafia) { RoleId.MAFIOSO } + core + setup.specials + List(citizens) { RoleId.CITIZEN }
    }

    fun forPlayers(playerCount: Int, random: Random = Random.Default): List<RoleId> =
        build(playerCount, combinations(playerCount).random(random))

    private fun requireSupported(playerCount: Int) {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "Player count must be in $MIN_PLAYERS..$MAX_PLAYERS, got $playerCount"
        }
    }
}
