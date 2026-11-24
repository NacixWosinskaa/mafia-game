package pl.mafia.engine

// A public statement "I investigated [targetId] and they are / are not mafia".
// The engine does not check truthfulness: mafia can lie, and bots have to deal with that.
data class Claim(
    val speakerId: PlayerId,
    val day: Int,
    val targetId: PlayerId,
    val claimsMafia: Boolean,
)

data class DayVotes(
    val day: Int,
    val votes: Map<PlayerId, PlayerId?>,
    val executedId: PlayerId?,
)
