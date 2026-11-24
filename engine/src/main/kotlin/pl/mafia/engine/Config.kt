package pl.mafia.engine

enum class TieRule { NO_EXECUTION, RANDOM_AMONG_TIED }

data class GameConfig(
    val killOnFirstNight: Boolean = false,
    val tieRule: TieRule = TieRule.NO_EXECUTION,
    val doctorCanSelfProtect: Boolean = true,
    val doctorCanRepeatTarget: Boolean = false,
    // The shooter also dies when the target is not mafia. The target dies either way.
    val shooterDiesOnMiss: Boolean = true,
    // Roles that win by being executed (the joker) only win from this day on, and optionally
    // only when more than half of the living players voted for them. Otherwise they just die.
    val executionWinFromDay: Int = 3,
    val executionWinNeedsMajority: Boolean = false,
)
