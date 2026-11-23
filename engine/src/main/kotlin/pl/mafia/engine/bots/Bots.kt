package pl.mafia.engine.bots

import pl.mafia.engine.Faction
import pl.mafia.engine.PlayerId
import pl.mafia.engine.PlayerView
import pl.mafia.engine.RoleId
import pl.mafia.engine.Roles
import kotlin.random.Random

/** Bot widzi tylko [PlayerView], tak samo jak człowiek. */
interface Bot {
    /** Cel akcji nocnej albo null, jeśli bot nie ma akcji / rezygnuje. */
    fun chooseNightTarget(view: PlayerView): PlayerId?

    /** Cel głosowania; null = głos "na nikogo". */
    fun chooseVote(view: PlayerView): PlayerId?
}

/** Wybiera losowo spośród dozwolonych celów. Dolna granica jakości do testów balansu. */
class RandomBot(private val random: Random) : Bot {
    override fun chooseNightTarget(view: PlayerView): PlayerId? =
        view.legalNightTargets.randomOrNull(random)

    override fun chooseVote(view: PlayerView): PlayerId? =
        view.legalVoteTargets.randomOrNull(random)
}

/**
 * Prosta heurystyka, bez rozmów (symulator nie modeluje dyskusji):
 *  - mafia koordynuje cel nocny i nie głosuje na swoich,
 *  - detektyw bada jeszcze niesprawdzone osoby i głosuje na znaną mafię,
 *  - pozostali działają losowo.
 */
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
