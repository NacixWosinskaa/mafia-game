package pl.mafia.engine

typealias PlayerId = Int

enum class Faction { TOWN, MAFIA, NEUTRAL }

enum class RoleId { CITIZEN, MAFIOSO, DETECTIVE, DOCTOR, JOKER, DAY_SHOOTER }

enum class NightActionType { KILL, PROTECT, INVESTIGATE }

enum class DayActionType { SHOOT }

data class RoleDefinition(
    val id: RoleId,
    val faction: Faction,
    val nightAction: NightActionType? = null,
    val dayAction: DayActionType? = null,
    val dayActionUses: Int = 0,
    // The game ends immediately and this role's faction wins when the player is executed by vote.
    val winsIfExecuted: Boolean = false,
)

object Roles {
    private val definitions: Map<RoleId, RoleDefinition> = listOf(
        RoleDefinition(RoleId.CITIZEN, Faction.TOWN),
        RoleDefinition(RoleId.MAFIOSO, Faction.MAFIA, NightActionType.KILL),
        RoleDefinition(RoleId.DETECTIVE, Faction.TOWN, NightActionType.INVESTIGATE),
        RoleDefinition(RoleId.DOCTOR, Faction.TOWN, NightActionType.PROTECT),
        RoleDefinition(RoleId.JOKER, Faction.NEUTRAL, winsIfExecuted = true),
        RoleDefinition(RoleId.DAY_SHOOTER, Faction.TOWN, dayAction = DayActionType.SHOOT, dayActionUses = 1),
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
