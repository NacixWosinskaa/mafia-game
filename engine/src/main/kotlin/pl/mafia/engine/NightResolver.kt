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

// Resolution order: protect, kill, investigate. Actions are validated by Game beforehand.
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

        val protectedIds = actionsOf(NightActionType.PROTECT).map { it.second }.toSet()

        val attacked = if (killAllowed) {
            pluralityTarget(actionsOf(NightActionType.KILL).map { it.second })
        } else {
            null
        }
        val prevented = attacked?.takeIf { it in protectedIds }
        val deaths = if (attacked != null && prevented == null) listOf(attacked) else emptyList()

        // Results depend only on the target's faction, so they survive the detective's or target's death.
        val investigations = actionsOf(NightActionType.INVESTIGATE)
            .map { (detectiveId, targetId) ->
                Investigation(detectiveId, targetId, byId.getValue(targetId).faction == Faction.MAFIA)
            }
            .sortedBy { it.detectiveId }

        return NightResult(attacked, deaths, prevented, investigations)
    }

    // Ties go to the lowest player id to keep resolution deterministic.
    private fun pluralityTarget(targets: List<PlayerId>): PlayerId? {
        if (targets.isEmpty()) return null
        val counts = targets.groupingBy { it }.eachCount()
        val max = counts.values.max()
        return counts.filterValues { it == max }.keys.min()
    }
}
