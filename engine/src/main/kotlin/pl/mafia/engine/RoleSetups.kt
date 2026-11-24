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

    fun forPlayers(playerCount: Int): List<RoleId> {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "Player count must be in $MIN_PLAYERS..$MAX_PLAYERS, got $playerCount"
        }
        val mafia = mafiaCount.getValue(playerCount)
        val citizens = playerCount - mafia - townSpecials.size
        return List(mafia) { RoleId.MAFIOSO } + townSpecials + List(citizens) { RoleId.CITIZEN }
    }
}
