package pl.mafia.engine.bots

import pl.mafia.engine.Faction
import pl.mafia.engine.PlayerId
import pl.mafia.engine.PlayerView
import pl.mafia.engine.RoleId
import pl.mafia.engine.Roles
import kotlin.random.Random

data class ClaimIntent(val targetId: PlayerId, val claimsMafia: Boolean)

interface Bot {
    fun chooseNightTarget(view: PlayerView): PlayerId?

    // null means a vote for nobody.
    fun chooseVote(view: PlayerView): PlayerId?

    fun chooseClaims(view: PlayerView): List<ClaimIntent> = emptyList()

    // null means holding fire.
    fun chooseShot(view: PlayerView): PlayerId? = null
}

class RandomBot(private val random: Random) : Bot {
    override fun chooseNightTarget(view: PlayerView): PlayerId? =
        view.legalNightTargets.randomOrNull(random)

    override fun chooseVote(view: PlayerView): PlayerId? =
        view.legalVoteTargets.randomOrNull(random)
}

// Mafia agrees on one kill target and never votes for teammates; the detective checks
// new people and votes for known mafia. Everyone else is random. There is no discussion.
class HeuristicBot(private val random: Random) : Bot {

    override fun chooseNightTarget(view: PlayerView): PlayerId? {
        val options = view.legalNightTargets
        if (options.isEmpty()) return null
        return when (Roles.of(view.role).faction) {
            Faction.MAFIA -> view.teammateKillChoice?.takeIf { it in options } ?: options.random(random)
            else -> when (view.role) {
                RoleId.DETECTIVE -> {
                    val checked = view.investigations.map { it.targetId }.toSet()
                    options.filter { it !in checked }.randomOrNull(random) ?: options.random(random)
                }
                else -> options.random(random)
            }
        }
    }

    override fun chooseVote(view: PlayerView): PlayerId? {
        val options = view.legalVoteTargets
        if (options.isEmpty()) return null

        if (view.role == RoleId.DETECTIVE) {
            val knownMafia = view.investigations
                .filter { it.isMafia && it.targetId in options }
                .map { it.targetId }
            if (knownMafia.isNotEmpty()) return knownMafia.random(random)
        }
        if (Roles.of(view.role).faction == Faction.MAFIA) {
            options.filter { it !in view.mafiaTeammates }.randomOrNull(random)?.let { return it }
        }
        return options.random(random)
    }
}
