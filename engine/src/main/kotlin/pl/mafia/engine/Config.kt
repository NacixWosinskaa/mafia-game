package pl.mafia.engine

enum class TieRule {
    /** Remis w głosowaniu = nikt nie ginie (domyślnie). */
    NO_EXECUTION,

    /** Remis rozstrzyga losowanie spośród remisujących. */
    RANDOM_AMONG_TIED,
}

/**
 * Wszystkie reguły, co do których nie ma pewności, są tutaj, żeby dało się je
 * zmienić jedną linijką i sprawdzić w symulatorze.
 */
data class GameConfig(
    /** Czy mafia może zabić już pierwszej nocy. */
    val killOnFirstNight: Boolean = false,
    val tieRule: TieRule = TieRule.NO_EXECUTION,
    val doctorCanSelfProtect: Boolean = true,
    /** Czy lekarz może chronić tę samą osobę dwie noce z rzędu. */
    val doctorCanRepeatTarget: Boolean = false,
)
