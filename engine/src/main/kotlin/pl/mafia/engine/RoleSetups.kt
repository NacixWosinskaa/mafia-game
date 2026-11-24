package pl.mafia.engine

// Draft role table; the simulator is meant to tune it.
object RoleSetups {
    const val MIN_PLAYERS = 6
    const val MAX_PLAYERS = 12

    private val mafiaCount: Map<Int, Int> = mapOf(
        6 to 1,
        7 to 2,
        8 to 2,
        9 to 2,
        10 to 3,
        11 to 3,
        12 to 3,
    )

    private val townSpecials = listOf(RoleId.DETECTIVE, RoleId.DOCTOR)

    private val extraRoles: Map<Int, List<RoleId>> = mapOf(
        8 to listOf(RoleId.JOKER),
        9 to listOf(RoleId.JOKER),
        10 to listOf(RoleId.JOKER, RoleId.DAY_SHOOTER),
        11 to listOf(RoleId.JOKER, RoleId.DAY_SHOOTER),
        12 to listOf(RoleId.JOKER, RoleId.DAY_SHOOTER),
    )

    fun forPlayers(playerCount: Int): List<RoleId> {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "Player count must be in $MIN_PLAYERS..$MAX_PLAYERS, got $playerCount"
        }
        val mafia = mafiaCount.getValue(playerCount)
        val extras = extraRoles[playerCount].orEmpty()
        val citizens = playerCount - mafia - townSpecials.size - extras.size
        return List(mafia) { RoleId.MAFIOSO } + townSpecials + extras + List(citizens) { RoleId.CITIZEN }
    }
}
