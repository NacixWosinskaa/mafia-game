package pl.mafia.engine.bots

import pl.mafia.engine.Faction
import pl.mafia.engine.PlayerId
import pl.mafia.engine.PlayerView
import pl.mafia.engine.Roles

internal data class Assessment(
    val suspicion: Map<PlayerId, Double>,
    // How much a speaker's claims count. 0 for proven liars.
    val credibility: Map<PlayerId, Double>,
)

private const val CERTAIN = 100.0
private const val CLAIM_WEIGHT = 3.0
private const val VOTE_WEIGHT = 1.0

// Town-side reasoning. Town players never lie, so a claim that contradicts what the viewer
// knows (own faction, own investigations, revealed roles) proves the speaker is mafia,
// and every claim from a player later revealed as town is trusted fully.
internal fun assess(view: PlayerView): Assessment {
    val suspicion = view.alive.filter { it != view.selfId }.associateWith { 0.0 }.toMutableMap()
    fun add(id: PlayerId, delta: Double) {
        if (id in suspicion) suspicion[id] = suspicion.getValue(id) + delta
    }

    fun revealedMafia(id: PlayerId): Boolean? =
        view.revealedRoles[id]?.let { Roles.of(it).faction == Faction.MAFIA }

    val ownResults = view.investigations.associate { it.targetId to it.isMafia }

    fun knownMafia(id: PlayerId): Boolean? = when {
        id == view.selfId -> Roles.of(view.role).faction == Faction.MAFIA
        id in view.mafiaTeammates -> true
        id in ownResults -> ownResults.getValue(id)
        else -> revealedMafia(id)
    }

    ownResults.forEach { (target, isMafia) -> add(target, if (isMafia) CERTAIN else -CERTAIN) }

    val correct = mutableMapOf<PlayerId, Int>()
    val liars = mutableSetOf<PlayerId>()
    for (claim in view.claims) {
        val truth = knownMafia(claim.targetId) ?: continue
        if (truth == claim.claimsMafia) correct.merge(claim.speakerId, 1, Int::plus) else liars += claim.speakerId
    }
    liars.forEach { add(it, CERTAIN) }

    fun revealedTown(id: PlayerId) = view.revealedRoles[id]?.let { Roles.of(it).faction == Faction.TOWN } == true

    val credibility = view.claims.map { it.speakerId }.distinct().associateWith { speaker ->
        if (speaker in liars || revealedMafia(speaker) == true) 0.0 else 1.0 + (correct[speaker] ?: 0)
    }

    for (claim in view.claims) {
        if (knownMafia(claim.targetId) != null) continue
        val sign = if (claim.claimsMafia) 1.0 else -1.0
        val delta = if (revealedTown(claim.speakerId)) CERTAIN else CLAIM_WEIGHT * credibility.getValue(claim.speakerId)
        add(claim.targetId, sign * delta)
    }

    for (day in view.voteHistory) {
        val executed = day.executedId ?: continue
        val wasMafia = revealedMafia(executed) ?: continue
        for ((voter, target) in day.votes) {
            if (target == executed) add(voter, if (wasMafia) -VOTE_WEIGHT else VOTE_WEIGHT)
        }
    }

    return Assessment(suspicion, credibility)
}
