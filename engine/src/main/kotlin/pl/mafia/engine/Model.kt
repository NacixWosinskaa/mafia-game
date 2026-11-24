package pl.mafia.engine

typealias PlayerId = Int

enum class Faction { TOWN, MAFIA, NEUTRAL }

enum class RoleId {
    CITIZEN, MAFIOSO, DETECTIVE, DOCTOR, JOKER, DAY_SHOOTER, AVENGER,
    GODFATHER, BODYGUARD, BLACKMAILER, LOOKOUT, MAYOR, SHERIFF, POISONER,
}

enum class NightActionType { KILL, PROTECT, INVESTIGATE, GUARD, BLOCK, WATCH, EXAMINE, POISON }

enum class DayActionType { SHOOT }

data class RoleDefinition(
    val id: RoleId,
    val faction: Faction,
    val nightAction: NightActionType? = null,
    val dayAction: DayActionType? = null,
    val dayActionUses: Int = 0,
    // The game ends immediately and this role's faction wins when the player is executed by vote.
    val winsIfExecuted: Boolean = false,
    // When killed by the mafia at night, a random living mafioso dies too.
    val revengeOnMafiaKill: Boolean = false,
    // Roles that profit from drawing attention; setups limit how many appear in small games.
    val bait: Boolean = false,
    // Detective investigations report this role as not mafia.
    val appearsInnocent: Boolean = false,
    // How many times a night action may take effect in a game; null = unlimited.
    val nightActionUses: Int? = null,
    val voteWeight: Int = 1,
)

object Roles {
    private val definitions: Map<RoleId, RoleDefinition> = listOf(
        RoleDefinition(RoleId.CITIZEN, Faction.TOWN),
        RoleDefinition(RoleId.MAFIOSO, Faction.MAFIA, NightActionType.KILL),
        RoleDefinition(RoleId.DETECTIVE, Faction.TOWN, NightActionType.INVESTIGATE),
        RoleDefinition(RoleId.DOCTOR, Faction.TOWN, NightActionType.PROTECT),
        RoleDefinition(RoleId.JOKER, Faction.NEUTRAL, winsIfExecuted = true, bait = true),
        RoleDefinition(RoleId.DAY_SHOOTER, Faction.TOWN, dayAction = DayActionType.SHOOT, dayActionUses = 1),
        RoleDefinition(RoleId.AVENGER, Faction.TOWN, revengeOnMafiaKill = true, bait = true),
        RoleDefinition(RoleId.GODFATHER, Faction.MAFIA, NightActionType.KILL, appearsInnocent = true),
        RoleDefinition(RoleId.BODYGUARD, Faction.TOWN, NightActionType.GUARD),
        RoleDefinition(RoleId.BLACKMAILER, Faction.MAFIA, NightActionType.BLOCK),
        RoleDefinition(RoleId.LOOKOUT, Faction.TOWN, NightActionType.WATCH),
        RoleDefinition(RoleId.MAYOR, Faction.TOWN, voteWeight = 2),
        RoleDefinition(RoleId.SHERIFF, Faction.TOWN, NightActionType.EXAMINE, nightActionUses = 1),
        RoleDefinition(RoleId.POISONER, Faction.MAFIA, NightActionType.POISON, nightActionUses = 2),
    ).associateBy { it.id }

    fun of(id: RoleId): RoleDefinition = definitions.getValue(id)
}

data class Player(
    val id: PlayerId,
    val name: String,
    val role: RoleId,
    val alive: Boolean = true,
) {
    val definition: RoleDefinition get() = Roles.of(role)
    val faction: Faction get() = definition.faction
}

enum class Phase { NIGHT, DAY, FINISHED }

// Mafia members whose own ability is not a kill take over the kill when no killer is left alive;
// otherwise the game could never end.
fun effectiveNightAction(actor: Player, players: List<Player>): NightActionType? {
    val own = actor.definition.nightAction
    if (actor.faction != Faction.MAFIA || own == null || own == NightActionType.KILL) return own
    val killerAlive = players.any { it.alive && it.faction == Faction.MAFIA && it.definition.nightAction == NightActionType.KILL }
    return if (killerAlive) own else NightActionType.KILL
}
