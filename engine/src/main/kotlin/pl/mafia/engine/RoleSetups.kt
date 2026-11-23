package pl.mafia.engine

/**
 * Tabela składów ról zależna od liczby graczy. To szkic, który ma dostroić
 * symulator, dlatego jest zapisany jako tabela, a nie wzór.
 */
object RoleSetups {
    const val MIN_PLAYERS = 6
    const val MAX_PLAYERS = 12

    /** liczba graczy -> liczba mafiosów */
    private val mafiaCount: Map<Int, Int> = mapOf(
        6 to 1,
        7 to 2,
        8 to 2,
        9 to 2,
        10 to 3,
        11 to 3,
        12 to 3,
    )

    /** Role specjalne po stronie miasta: zawsze po jednej. */
    private val townSpecials = listOf(RoleId.DETECTIVE, RoleId.DOCTOR)

    fun forPlayers(playerCount: Int): List<RoleId> {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "Liczba graczy musi być w zakresie $MIN_PLAYERS..$MAX_PLAYERS, a jest $playerCount"
        }
        val mafia = mafiaCount.getValue(playerCount)
        val citizens = playerCount - mafia - townSpecials.size
        return List(mafia) { RoleId.MAFIOSO } + townSpecials + List(citizens) { RoleId.CITIZEN }
    }
}
