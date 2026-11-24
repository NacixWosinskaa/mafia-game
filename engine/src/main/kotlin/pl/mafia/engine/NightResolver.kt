package pl.mafia.engine

data class Investigation(
    val detectiveId: PlayerId,
    val targetId: PlayerId,
    val isMafia: Boolean,
)

// Who acted on the target during the night. Blocked players still count as visitors.
data class Sighting(
    val lookoutId: PlayerId,
    val night: Int,
    val targetId: PlayerId,
    val visitorIds: List<PlayerId>,
)

data class RoleCheck(val sheriffId: PlayerId, val targetId: PlayerId, val role: RoleId)

data class Sacrifice(val bodyguardId: PlayerId, val targetId: PlayerId)

data class NightResult(
    val attackedId: PlayerId?,
    val deaths: List<PlayerId>,
    val preventedAttackOn: PlayerId?,
    val sacrifice: Sacrifice?,
    // blocker -> blocked player
    val blocks: Map<PlayerId, PlayerId>,
    val investigations: List<Investigation>,
    val sightings: List<Sighting>,
    val roleChecks: List<RoleCheck>,
    // poisoner -> victim; takes effect on the next night
    val poisonings: List<Pair<PlayerId, PlayerId>>,
    // Poisoned earlier and not protected tonight / protected tonight.
    val poisonDeaths: List<PlayerId>,
    val cured: List<PlayerId>,
)

// Resolution order: block, protect, kill (a guard takes the hit), poison, investigate / examine / watch.
// Actions are validated by Game beforehand.
object NightResolver {

    fun resolve(
        players: List<Player>,
        actions: Map<PlayerId, PlayerId>,
        killAllowed: Boolean,
        night: Int = 1,
        poisoned: Set<PlayerId> = emptySet(),
    ): NightResult {
        val byId = players.associateBy { it.id }

        fun actionsOf(type: NightActionType): List<Pair<PlayerId, PlayerId>> =
            actions.filter { (actorId, _) ->
                val actor = byId[actorId]
                actor != null && actor.alive && effectiveNightAction(actor, players) == type
            }.toList()

        val blocks = actionsOf(NightActionType.BLOCK).toMap()
        val blockedIds = blocks.values.toSet()
        fun effective(type: NightActionType) = actionsOf(type).filter { it.first !in blockedIds }

        val protectedIds = effective(NightActionType.PROTECT).map { it.second }.toSet()
        val guards = effective(NightActionType.GUARD)

        val attacked = if (killAllowed) {
            pluralityTarget(effective(NightActionType.KILL).map { it.second })
        } else {
            null
        }
        val prevented = attacked?.takeIf { it in protectedIds }
        val guard = if (attacked != null && prevented == null) guards.firstOrNull { it.second == attacked } else null
        val sacrifice = guard?.let { Sacrifice(it.first, attacked!!) }
        val deaths = when {
            sacrifice != null -> listOf(sacrifice.bodyguardId)
            attacked != null && prevented == null -> listOf(attacked)
            else -> emptyList()
        }

        val poisonDeaths = poisoned.filter { it !in protectedIds && byId[it]?.alive == true && it !in deaths }
        val cured = poisoned.filter { it in protectedIds && byId[it]?.alive == true }
        val poisonings = effective(NightActionType.POISON).filter { it.second !in poisoned }

        // Results depend only on roles and actions, so they survive the actor's or target's death.
        val investigations = effective(NightActionType.INVESTIGATE)
            .map { (detectiveId, targetId) ->
                val target = byId.getValue(targetId)
                Investigation(detectiveId, targetId, target.faction == Faction.MAFIA && !target.definition.appearsInnocent)
            }
            .sortedBy { it.detectiveId }

        val sightings = effective(NightActionType.WATCH)
            .map { (lookoutId, targetId) ->
                val visitors = actions.filter { (actorId, target) ->
                    target == targetId && actorId != lookoutId && byId[actorId]?.alive == true
                }.keys.sorted()
                Sighting(lookoutId, night, targetId, visitors)
            }
            .sortedBy { it.lookoutId }

        val roleChecks = effective(NightActionType.EXAMINE)
            .map { (sheriffId, targetId) -> RoleCheck(sheriffId, targetId, byId.getValue(targetId).role) }
            .sortedBy { it.sheriffId }

        return NightResult(
            attacked, deaths, prevented, sacrifice, blocks, investigations, sightings, roleChecks,
            poisonings, poisonDeaths, cured,
        )
    }

    // Ties go to the lowest player id to keep resolution deterministic.
    private fun pluralityTarget(targets: List<PlayerId>): PlayerId? {
        if (targets.isEmpty()) return null
        val counts = targets.groupingBy { it }.eachCount()
        val max = counts.values.max()
        return counts.filterValues { it == max }.keys.min()
    }
}
