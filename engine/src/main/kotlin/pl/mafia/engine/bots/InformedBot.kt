package pl.mafia.engine.bots

import pl.mafia.engine.Faction
import pl.mafia.engine.PlayerId
import pl.mafia.engine.PlayerView
import pl.mafia.engine.RoleId
import pl.mafia.engine.Roles
import kotlin.random.Random

// Uses public claims and vote history instead of guessing:
//  - the detective announces every result,
//  - mafia bluff (frame a random player, or counter an accuser) and hunt claimed detectives,
//  - the doctor protects the most credible accuser,
//  - everyone on the town side votes for the most suspicious player.
class InformedBot(
    private val random: Random,
    private val bluffChance: Double = 0.5,
) : Bot {

    override fun chooseNightTarget(view: PlayerView): PlayerId? {
        val options = view.legalNightTargets
        if (options.isEmpty()) return null
        return when (view.role) {
            RoleId.MAFIOSO ->
                view.teammateKillChoice?.takeIf { it in options }
                    ?: accusersOfMafia(view).filter { it in options }.randomOrNull(random)
                    ?: options.random(random)

            RoleId.DETECTIVE -> {
                val checked = view.investigations.map { it.targetId }.toSet()
                val pool = options.filter { it !in checked }.ifEmpty { options }
                pickHighest(assess(view).suspicion, pool)
            }

            RoleId.DOCTOR -> {
                val credibility = assess(view).credibility
                val accusers = view.claims.filter { it.claimsMafia && it.speakerId in options }.map { it.speakerId }
                val best = accusers.maxOfOrNull { credibility[it] ?: 0.0 }
                accusers.filter { credibility[it] == best && best != 0.0 }.randomOrNull(random)
                    ?: options.random(random)
            }

            else -> options.random(random)
        }
    }

    override fun chooseVote(view: PlayerView): PlayerId? {
        val options = view.legalVoteTargets
        if (options.isEmpty()) return null

        if (Roles.of(view.role).faction == Faction.MAFIA) {
            return accusersOfMafia(view).filter { it in options }.randomOrNull(random)
                ?: options.filter { it !in view.mafiaTeammates }.randomOrNull(random)
                ?: options.random(random)
        }
        return pickHighest(assess(view).suspicion, options)
    }

    override fun chooseClaims(view: PlayerView): List<ClaimIntent> = when (view.role) {
        RoleId.DETECTIVE -> announceResults(view)
        RoleId.MAFIOSO -> bluff(view)
        else -> emptyList()
    }

    private fun announceResults(view: PlayerView): List<ClaimIntent> {
        val already = claimedBy(view, view.selfId)
        return view.investigations
            .filter { it.targetId !in already }
            .map { ClaimIntent(it.targetId, it.isMafia) }
    }

    private fun bluff(view: PlayerView): List<ClaimIntent> {
        if (random.nextDouble() >= bluffChance) return emptyList()
        val already = claimedBy(view, view.selfId)
        val team = view.mafiaTeammates + view.selfId
        val accuser = accusersOfMafia(view).firstOrNull { it in view.alive && it !in already }
        val target = accuser
            ?: view.alive.filter { it !in team && it !in already }.randomOrNull(random)
            ?: return emptyList()
        return listOf(ClaimIntent(target, true))
    }

    private fun accusersOfMafia(view: PlayerView): List<PlayerId> {
        val team = view.mafiaTeammates + view.selfId
        return view.claims.filter { it.claimsMafia && it.targetId in team }.map { it.speakerId }.distinct()
    }

    private fun claimedBy(view: PlayerView, speaker: PlayerId): Set<PlayerId> =
        view.claims.filter { it.speakerId == speaker }.map { it.targetId }.toSet()

    private fun pickHighest(scores: Map<PlayerId, Double>, pool: List<PlayerId>): PlayerId {
        val max = pool.maxOf { scores[it] ?: 0.0 }
        return pool.filter { (scores[it] ?: 0.0) == max }.random(random)
    }
}
