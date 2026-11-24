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

    data class ActionBlocked(val blockerId: PlayerId, val targetId: PlayerId) : GameEvent
    data class BodyguardSacrificed(val bodyguardId: PlayerId, val targetId: PlayerId) : GameEvent
    data class Watched(val lookoutId: PlayerId, val targetId: PlayerId, val visitorIds: List<PlayerId>) : GameEvent

    data class Examined(val sheriffId: PlayerId, val targetId: PlayerId, val role: RoleId) : GameEvent
    data class PlayerPoisoned(val poisonerId: PlayerId, val targetId: PlayerId) : GameEvent
    data class PoisonCured(val targetId: PlayerId) : GameEvent
    data class PoisonKilled(val playerId: PlayerId) : GameEvent

    data class DayBegan(val day: Int) : GameEvent

    data class ClaimMade(val speakerId: PlayerId, val targetId: PlayerId, val claimsMafia: Boolean) : GameEvent

    // A null value (votes) or key (tally) means "nobody".
    data class VotesTallied(val votes: Map<PlayerId, PlayerId?>, val tally: Map<PlayerId?, Int>) : GameEvent
    data class RevengeTriggered(val avengerId: PlayerId, val targetId: PlayerId) : GameEvent
    data class ShooterMissed(val shooterId: PlayerId, val role: RoleId) : GameEvent
    data class PlayerShot(val shooterId: PlayerId, val targetId: PlayerId, val role: RoleId) : GameEvent
    data class PlayerExecuted(val playerId: PlayerId, val role: RoleId) : GameEvent
    data class NoExecution(val reason: NoExecutionReason) : GameEvent
    data class GameOver(val winner: Faction) : GameEvent
}
