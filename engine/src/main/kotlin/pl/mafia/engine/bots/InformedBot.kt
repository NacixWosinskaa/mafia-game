package pl.mafia.engine.bots

import pl.mafia.engine.Faction
import pl.mafia.engine.PlayerId
import pl.mafia.engine.PlayerView
import pl.mafia.engine.RoleId
import pl.mafia.engine.Roles
import kotlin.random.Random

private const val SHOOT_THRESHOLD = 6.0

// Uses public claims and vote history instead of guessing:
//  - the detective announces every result,
//  - mafia bluff (frame a random player, or counter an accuser) and hunt claimed detectives,
//  - the doctor protects the most credible accuser,
//  - everyone on the town side votes for the most suspicious player.
class InformedBot(
    private val random: Random,
    private val bluffChance: Double = 0.5,
    // How much town believes unproven claims (0..1) and how much random wobble their choices get.
    private val trust: Double = 1.0,
    private val noise: Double = 0.0,
    // Chance per day that the detective and the lookout share what they know; 1 = always.
    private val candor: Double = 1.0,
    // Chance that a bluffing mafioso also vouches for a teammate ("not mafia").
    private val cunning: Double = 0.0,
) : Bot {

    override fun chooseNightTarget(view: PlayerView): PlayerId? {
        val options = view.legalNightTargets
        if (options.isEmpty()) return null
        return when (view.role) {
            RoleId.MAFIOSO, RoleId.GODFATHER ->
                view.teammateKillChoice?.takeIf { it in options }
                    ?: accusersOfMafia(view).filter { it in options }.randomOrNull(random)
                    ?: options.random(random)

            RoleId.BLACKMAILER, RoleId.POISONER ->
                accusersOfMafia(view).filter { it in options }.randomOrNull(random) ?: options.random(random)

            RoleId.DETECTIVE, RoleId.SHERIFF -> {
                val checked = (view.investigations.map { it.targetId } + view.roleChecks.map { it.targetId }).toSet()
                val pool = options.filter { it !in checked }.ifEmpty { options }
                pickHighest(assess(view, trust).suspicion, pool)
            }

            RoleId.DOCTOR, RoleId.BODYGUARD, RoleId.LOOKOUT -> {
                if (view.poisoned && view.selfId in options) return view.selfId
                val credibility = assess(view, trust).credibility
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
        return pickHighest(assess(view, trust).suspicion, options)
    }

    override fun chooseClaims(view: PlayerView): List<ClaimIntent> = when (view.role) {
        RoleId.DETECTIVE -> announceResults(view)
        RoleId.SHERIFF -> announceRoleChecks(view)
        RoleId.MAFIOSO, RoleId.GODFATHER, RoleId.BLACKMAILER, RoleId.POISONER -> bluff(view)
        RoleId.LOOKOUT -> accuseVisitors(view)
        RoleId.JOKER -> provoke(view)
        else -> emptyList()
    }

    // Shoots only on strong evidence: a proven liar, a known mafioso or several credible accusers.
    override fun chooseShot(view: PlayerView): PlayerId? {
        val options = view.legalShotTargets
        if (options.isEmpty()) return null
        val scores = assess(view, trust).suspicion
        val best = options.maxOf { scores[it] ?: 0.0 }
        return if (best >= SHOOT_THRESHOLD) pickHighest(scores, options) else null
    }

    // The joker wants to be executed, so they make accusations that tend to get exposed.
    private fun provoke(view: PlayerView): List<ClaimIntent> {
        if (random.nextDouble() >= bluffChance) return emptyList()
        val already = claimedBy(view, view.selfId)
        val target = view.alive.filter { it != view.selfId && it !in already }.randomOrNull(random)
            ?: return emptyList()
        return listOf(ClaimIntent(target, true))
    }

    private fun silentToday() = candor < 1.0 && random.nextDouble() >= candor

    private fun announceRoleChecks(view: PlayerView): List<ClaimIntent> {
        if (silentToday()) return emptyList()
        val already = claimedBy(view, view.selfId)
        return view.roleChecks
            .filter { it.targetId !in already }
            .map { ClaimIntent(it.targetId, Roles.of(it.role).faction == Faction.MAFIA) }
    }

    private fun announceResults(view: PlayerView): List<ClaimIntent> {
        if (silentToday()) return emptyList()
        val already = claimedBy(view, view.selfId)
        return view.investigations
            .filter { it.targetId !in already }
            .distinctBy { it.targetId }
            .map { ClaimIntent(it.targetId, it.isMafia) }
    }

    // Whoever visited a player who was killed that night is probably the killer.
    private fun accuseVisitors(view: PlayerView): List<ClaimIntent> {
        if (silentToday()) return emptyList()
        val already = claimedBy(view, view.selfId)
        return view.sightings
            .filter { it.targetId in view.nightDeaths[it.night].orEmpty() }
            .flatMap { it.visitorIds }
            .distinct()
            .filter { it !in already && it in view.alive }
            .map { ClaimIntent(it, true) }
    }

    private fun bluff(view: PlayerView): List<ClaimIntent> {
        if (random.nextDouble() >= bluffChance) return emptyList()
        val already = claimedBy(view, view.selfId)
        val team = view.mafiaTeammates + view.selfId
        val accuser = accusersOfMafia(view).firstOrNull { it in view.alive && it !in already }
        val target = accuser
            ?: view.alive.filter { it !in team && it !in already }.randomOrNull(random)
            ?: return emptyList()
        val vouch = if (cunning > 0.0 && random.nextDouble() < cunning) {
            view.mafiaTeammates.filter { it in view.alive && it !in already }.randomOrNull(random)
                ?.let { ClaimIntent(it, false) }
        } else {
            null
        }
        return listOfNotNull(ClaimIntent(target, true), vouch)
    }

    private fun accusersOfMafia(view: PlayerView): List<PlayerId> {
        val team = view.mafiaTeammates + view.selfId
        return view.claims.filter { it.claimsMafia && it.targetId in team }.map { it.speakerId }.distinct()
    }

    private fun claimedBy(view: PlayerView, speaker: PlayerId): Set<PlayerId> =
        view.claims.filter { it.speakerId == speaker }.map { it.targetId }.toSet()

    private fun pickHighest(scores: Map<PlayerId, Double>, pool: List<PlayerId>): PlayerId {
        val shown = pool.associateWith { (scores[it] ?: 0.0) + if (noise > 0.0) noise * (random.nextDouble() * 2 - 1) else 0.0 }
        val max = shown.values.max()
        return pool.filter { shown.getValue(it) == max }.random(random)
    }
}
