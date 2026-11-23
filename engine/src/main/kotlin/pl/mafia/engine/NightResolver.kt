package pl.mafia.engine

data class Investigation(
    val detectiveId: PlayerId,
    val targetId: PlayerId,
    val isMafia: Boolean,
)

data class NightResult(
    val attackedId: PlayerId?,
    val deaths: List<PlayerId>,
    val preventedAttackOn: PlayerId?,
    val investigations: List<Investigation>,
)

/**
 * Czysta funkcja rozstrzygająca noc. Kolejność (priorytety):
 *  1. ochrona, 2. zabójstwo, 3. śledztwa.
 * Nowe role z akcjami nocnymi wpinamy tutaj, ustalając ich miejsce w kolejności.
 *
 * Zakłada, że akcje zostały już zwalidowane przez [Game].
 */
object NightResolver {

    fun resolve(
        players: List<Player>,
        actions: Map<PlayerId, PlayerId>,
        killAllowed: Boolean,
    ): NightResult {
        val byId = players.associateBy { it.id }

        fun actionsOf(type: NightActionType): List<Pair<PlayerId, PlayerId>> =
            actions.filter { (actorId, _) ->
                val actor = byId[actorId]
                actor != null && actor.alive && actor.definition.nightAction == type
            }.toList()

        // 1. Ochrona
        val protectedIds = actionsOf(NightActionType.PROTECT).map { it.second }.toSet()

        // 2. Zabójstwo: mafia wybiera cel większością; remis rozstrzyga najniższe id
        val attacked = if (killAllowed) {
            pluralityTarget(actionsOf(NightActionType.KILL).map { it.second })
        } else {
            null
        }
        val prevented = attacked?.takeIf { it in protectedIds }
        val deaths = if (attacked != null && prevented == null) listOf(attacked) else emptyList()

        // 3. Śledztwa: wynik zależy od frakcji celu, także gdy cel lub detektyw zginął tej nocy
        val investigations = actionsOf(NightActionType.INVESTIGATE)
            .map { (detectiveId, targetId) ->
                Investigation(detectiveId, targetId, byId.getValue(targetId).faction == Faction.MAFIA)
            }
            .sortedBy { it.detectiveId }

        return NightResult(attacked, deaths, prevented, investigations)
    }

    private fun pluralityTarget(targets: List<PlayerId>): PlayerId? {
        if (targets.isEmpty()) return null
        val counts = targets.groupingBy { it }.eachCount()
        val max = counts.values.max()
        return counts.filterValues { it == max }.keys.min()
    }
}
