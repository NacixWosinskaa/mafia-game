package pl.mafia.engine

typealias PlayerId = Int

enum class Faction { TOWN, MAFIA, NEUTRAL }

enum class RoleId { CITIZEN, MAFIOSO, DETECTIVE, DOCTOR }

enum class NightActionType { KILL, PROTECT, INVESTIGATE }

data class RoleDefinition(
    val id: RoleId,
    val faction: Faction,
    val nightAction: NightActionType? = null,
)

object Roles {
    private val definitions: Map<RoleId, RoleDefinition> = listOf(
        RoleDefinition(RoleId.CITIZEN, Faction.TOWN),
        RoleDefinition(RoleId.MAFIOSO, Faction.MAFIA, NightActionType.KILL),
        RoleDefinition(RoleId.DETECTIVE, Faction.TOWN, NightActionType.INVESTIGATE),
        RoleDefinition(RoleId.DOCTOR, Faction.TOWN, NightActionType.PROTECT),
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
