package pl.mafia.engine

enum class NoExecutionReason { NO_VOTES, NOBODY_CHOSEN, TIE }

/**
 * Silnik mówi tylko, CO się stało. To, jak to zostanie opisane (lore, motyw,
 * ton), robi osobna warstwa narratora, która konsumuje te zdarzenia.
 * To, kto widzi dane zdarzenie (np. wynik śledztwa), też jest decyzją warstwy wyżej.
 */
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

    /** Klucz null oznacza głosy "na nikogo". */
    data class VotesTallied(val tally: Map<PlayerId?, Int>) : GameEvent
    data class PlayerExecuted(val playerId: PlayerId, val role: RoleId) : GameEvent
    data class NoExecution(val reason: NoExecutionReason) : GameEvent
    data class GameOver(val winner: Faction) : GameEvent
}
