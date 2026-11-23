package pl.mafia.engine

import kotlin.random.Random

/**
 * Wiedza jednego gracza w danym momencie. Boty (i później klienci sieciowi)
 * dostają wyłącznie widok, nigdy pełny stan gry, żeby nie dało się podejrzeć cudzych ról.
 */
data class PlayerView(
    val selfId: PlayerId,
    val role: RoleId,
    val day: Int,
    val phase: Phase,
    val alive: List<PlayerId>,
    val mafiaTeammates: List<PlayerId>,
    /** Cel zabójstwa wybrany już tej nocy przez kogoś z drużyny (widzą go tylko mafiosi). */
    val teammateKillChoice: PlayerId?,
    val legalNightTargets: List<PlayerId>,
    val legalVoteTargets: List<PlayerId>,
    val investigations: List<Investigation>,
    /** Role ujawnione po śmierci (zabici i straceni). */
    val revealedRoles: Map<PlayerId, RoleId>,
)

/**
 * Maszyna stanów gry: noc -> rozstrzygnięcie -> dzień (głosowanie) -> egzekucja -> warunek wygranej.
 * Nie zna czasu rzeczywistego, sieci ani interfejsu: ktoś z zewnątrz zgłasza akcje
 * i wywołuje [resolveNight] / [resolveDay].
 */
class Game private constructor(
    names: List<String>,
    roles: List<RoleId>,
    val config: GameConfig,
    private val random: Random,
) {
    private val _players: MutableList<Player> =
        roles.mapIndexed { id, role -> Player(id, names[id], role) }.toMutableList()
    private val _events = mutableListOf<GameEvent>()
    private val nightActions = linkedMapOf<PlayerId, PlayerId>()
    private val votes = linkedMapOf<PlayerId, PlayerId?>()
    private val lastProtected = mutableMapOf<PlayerId, PlayerId>()
    private val knownInvestigations = mutableListOf<Investigation>()

    val players: List<Player> get() = _players.toList()
    val events: List<GameEvent> get() = _events.toList()

    var phase: Phase = Phase.NIGHT
        private set
    var day: Int = 1
        private set
    var winner: Faction? = null
        private set

    val isOver: Boolean get() = phase == Phase.FINISHED

    init {
        require(names.size == roles.size) { "Liczba nazw musi się zgadzać z liczbą ról" }
        _events += GameEvent.GameStarted(roles.size)
        _events += GameEvent.NightBegan(day)
    }

    fun player(id: PlayerId): Player =
        _players.getOrNull(id) ?: throw IllegalArgumentException("Nie ma gracza o id $id")

    fun alivePlayers(): List<Player> = _players.filter { it.alive }

    // ---------------------------------------------------------------- noc

    /** Cele dozwolone dla gracza tej nocy. Pusta lista = gracz nie ma teraz akcji. */
    fun legalNightTargets(actorId: PlayerId): List<PlayerId> {
        val actor = player(actorId)
        if (phase != Phase.NIGHT || !actor.alive) return emptyList()
        val type = actor.definition.nightAction ?: return emptyList()
        return alivePlayers().filter { target ->
            when (type) {
                NightActionType.KILL -> target.faction != Faction.MAFIA
                NightActionType.INVESTIGATE -> target.id != actor.id
                NightActionType.PROTECT ->
                    (config.doctorCanSelfProtect || target.id != actor.id) &&
                        (config.doctorCanRepeatTarget || lastProtected[actor.id] != target.id)
            }
        }.map { it.id }
    }

    fun submitNightAction(actorId: PlayerId, targetId: PlayerId) {
        check(phase == Phase.NIGHT) { "Akcje nocne można zgłaszać tylko w nocy" }
        require(targetId in legalNightTargets(actorId)) {
            "Niedozwolony cel $targetId dla gracza $actorId"
        }
        nightActions[actorId] = targetId
    }

    fun resolveNight() {
        check(phase == Phase.NIGHT) { "Teraz nie jest noc" }
        val killAllowed = day > 1 || config.killOnFirstNight
        val result = NightResolver.resolve(_players, nightActions, killAllowed)

        // Pamięć lekarza: brak akcji kasuje ograniczenie "nie dwa razy z rzędu"
        _players.filter { it.alive && it.definition.nightAction == NightActionType.PROTECT }.forEach { doctor ->
            val target = nightActions[doctor.id]
            if (target != null) lastProtected[doctor.id] = target else lastProtected.remove(doctor.id)
        }

        result.preventedAttackOn?.let { _events += GameEvent.AttackPrevented(it) }
        result.deaths.forEach { kill(it) }
        result.investigations.forEach {
            knownInvestigations += it
            _events += GameEvent.Investigated(it.detectiveId, it.targetId, it.isMafia)
        }
        nightActions.clear()

        if (!checkWinner()) {
            phase = Phase.DAY
            _events += GameEvent.DayBegan(day)
        }
    }

    // ---------------------------------------------------------------- dzień

    fun legalVoteTargets(voterId: PlayerId): List<PlayerId> {
        if (phase != Phase.DAY || !player(voterId).alive) return emptyList()
        return alivePlayers().map { it.id }.filter { it != voterId }
    }

    /** [targetId] == null oznacza głos "na nikogo". Ponowne zgłoszenie zmienia głos. */
    fun submitVote(voterId: PlayerId, targetId: PlayerId?) {
        check(phase == Phase.DAY) { "Głosować można tylko w dzień" }
        require(player(voterId).alive) { "Martwy gracz $voterId nie może głosować" }
        if (targetId != null) {
            require(targetId in legalVoteTargets(voterId)) { "Niedozwolony cel głosu $targetId" }
        }
        votes[voterId] = targetId
    }

    fun resolveDay() {
        check(phase == Phase.DAY) { "Teraz nie jest dzień" }
        val tally: Map<PlayerId?, Int> = votes.values.groupingBy { it }.eachCount()
        _events += GameEvent.VotesTallied(tally)

        if (tally.isEmpty()) {
            _events += GameEvent.NoExecution(NoExecutionReason.NO_VOTES)
        } else {
            val max = tally.values.max()
            val top: List<PlayerId?> = tally.filterValues { it == max }.keys.toList()
            if (top.size == 1) {
                executeOrSkip(top.single())
            } else if (config.tieRule == TieRule.RANDOM_AMONG_TIED) {
                executeOrSkip(top.random(random))
            } else {
                _events += GameEvent.NoExecution(NoExecutionReason.TIE)
            }
        }
        votes.clear()
        day++

        if (!checkWinner()) {
            phase = Phase.NIGHT
            _events += GameEvent.NightBegan(day)
        }
    }

    private fun executeOrSkip(chosen: PlayerId?) {
        if (chosen == null) {
            _events += GameEvent.NoExecution(NoExecutionReason.NOBODY_CHOSEN)
        } else {
            val victim = player(chosen)
            _players[chosen] = victim.copy(alive = false)
            _events += GameEvent.PlayerExecuted(chosen, victim.role)
        }
    }

    // ---------------------------------------------------------------- wspólne

    private fun kill(id: PlayerId) {
        val victim = player(id)
        _players[id] = victim.copy(alive = false)
        _events += GameEvent.PlayerKilled(id, victim.role)
    }

    private fun checkWinner(): Boolean {
        val alive = alivePlayers()
        val mafia = alive.count { it.faction == Faction.MAFIA }
        val others = alive.size - mafia
        val result = when {
            mafia == 0 -> Faction.TOWN
            mafia >= others -> Faction.MAFIA
            else -> return false
        }
        winner = result
        phase = Phase.FINISHED
        _events += GameEvent.GameOver(result)
        return true
    }

    fun viewFor(playerId: PlayerId): PlayerView {
        val me = player(playerId)
        val teammates = if (me.faction == Faction.MAFIA) {
            _players.filter { it.faction == Faction.MAFIA && it.id != playerId }.map { it.id }
        } else {
            emptyList()
        }
        val teammateChoice = nightActions.entries
            .firstOrNull { (actor, _) -> actor in teammates && player(actor).alive }
            ?.value
        val revealed = buildMap<PlayerId, RoleId> {
            _events.forEach {
                when (it) {
                    is GameEvent.PlayerKilled -> put(it.playerId, it.role)
                    is GameEvent.PlayerExecuted -> put(it.playerId, it.role)
                    else -> Unit
                }
            }
        }
        return PlayerView(
            selfId = playerId,
            role = me.role,
            day = day,
            phase = phase,
            alive = alivePlayers().map { it.id },
            mafiaTeammates = teammates,
            teammateKillChoice = teammateChoice,
            legalNightTargets = legalNightTargets(playerId),
            legalVoteTargets = legalVoteTargets(playerId),
            investigations = knownInvestigations.filter { it.detectiveId == playerId },
            revealedRoles = revealed,
        )
    }

    companion object {
        /** Nowa gra dla 6-12 graczy ze składem z [RoleSetups], role losowo rozdzielone. */
        fun create(
            playerCount: Int,
            config: GameConfig = GameConfig(),
            random: Random = Random.Default,
            names: List<String> = List(playerCount) { "Gracz ${it + 1}" },
        ): Game = Game(names, RoleSetups.forPlayers(playerCount).shuffled(random), config, random)

        /** Gra z ręcznie ustawionymi rolami (id = indeks na liście). Głównie do testów. */
        fun withRoles(
            roles: List<RoleId>,
            config: GameConfig = GameConfig(),
            random: Random = Random.Default,
            names: List<String> = List(roles.size) { "Gracz ${it + 1}" },
        ): Game = Game(names, roles, config, random)
    }
}
