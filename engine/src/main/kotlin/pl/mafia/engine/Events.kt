package pl.mafia.engine

enum class NoExecutionReason { NO_VOTES, NOBODY_CHOSEN, TIE }

// The engine only records what happened; narration and visibility are decided by higher layers.
sealed interface GameEvent {
    data class GameStarted(val playerCount: Int) : GameEvent
    data class NightBegan(val day: Int) : GameEvent
    data class AttackPrevented(val targetId: PlayerId) : GameEvent
    data class PlayerKilled(val playerId: PlayerId, val role: RoleId) : GameEvent
    data class Investigated(
        val detectiveId: PlayerId,
        val targetId: PlayerId,
        val isMafia: Boolean,
    ) : GameEvent

    data class DayBegan(val day: Int) : GameEvent

    // A null key counts votes for "nobody".
    data class VotesTallied(val tally: Map<PlayerId?, Int>) : GameEvent
    data class PlayerExecuted(val playerId: PlayerId, val role: RoleId) : GameEvent
    data class NoExecution(val reason: NoExecutionReason) : GameEvent
    data class GameOver(val winner: Faction) : GameEvent
}
