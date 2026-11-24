package pl.mafia.engine

import kotlin.random.Random

// What a single player knows. Bots and network clients get this, never the full game state.
data class PlayerView(
    val selfId: PlayerId,
    val role: RoleId,
    val day: Int,
    val phase: Phase,
    val alive: List<PlayerId>,
    val mafiaTeammates: List<PlayerId>,
    val teammateKillChoice: PlayerId?,
    val legalNightTargets: List<PlayerId>,
    val legalVoteTargets: List<PlayerId>,
    val legalShotTargets: List<PlayerId>,
    val investigations: List<Investigation>,
    val revealedRoles: Map<PlayerId, RoleId>,
    val claims: List<Claim>,
    val voteHistory: List<DayVotes>,
)

// Night -> resolve -> day (vote) -> execution -> win check. No clocks, network or UI;
// callers submit actions and trigger resolveNight() / resolveDay().
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
    private val shotsUsed = mutableMapOf<PlayerId, Int>()
    private val knownInvestigations = mutableListOf<Investigation>()
    private val _claims = mutableListOf<Claim>()
    private val _voteHistory = mutableListOf<DayVotes>()

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
        require(names.size == roles.size) { "Names and roles must have the same size" }
        _events += GameEvent.GameStarted(roles.size)
        _events += GameEvent.NightBegan(day)
    }

    fun player(id: PlayerId): Player =
        _players.getOrNull(id) ?: throw IllegalArgumentException("No player with id $id")

    fun alivePlayers(): List<Player> = _players.filter { it.alive }

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
        check(phase == Phase.NIGHT) { "Night actions can only be submitted at night" }
        require(targetId in legalNightTargets(actorId)) {
            "Illegal target $targetId for player $actorId"
        }
        nightActions[actorId] = targetId
    }

    fun resolveNight() {
        check(phase == Phase.NIGHT) { "It is not night" }
        val killAllowed = day > 1 || config.killOnFirstNight
        val result = NightResolver.resolve(_players, nightActions, killAllowed)

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

    fun legalVoteTargets(voterId: PlayerId): List<PlayerId> {
        if (phase != Phase.DAY || !player(voterId).alive) return emptyList()
        return alivePlayers().map { it.id }.filter { it != voterId }
    }

    // A null target is a vote for nobody. Submitting again replaces the previous vote.
    fun submitVote(voterId: PlayerId, targetId: PlayerId?) {
        check(phase == Phase.DAY) { "Votes can only be submitted during the day" }
        require(player(voterId).alive) { "Dead player $voterId cannot vote" }
        if (targetId != null) {
            require(targetId in legalVoteTargets(voterId)) { "Illegal vote target $targetId" }
        }
        votes[voterId] = targetId
    }

    // Claims are only allowed during the day; the target may already be dead.
    fun submitClaim(speakerId: PlayerId, targetId: PlayerId, claimsMafia: Boolean) {
        check(phase == Phase.DAY) { "Claims can only be made during the day" }
        require(player(speakerId).alive) { "Dead player $speakerId cannot make claims" }
        player(targetId)
        require(targetId != speakerId) { "Player $speakerId cannot make a claim about themselves" }
        require(_claims.none { it.speakerId == speakerId && it.targetId == targetId }) {
            "Player $speakerId already made a claim about $targetId"
        }
        _claims += Claim(speakerId, day, targetId, claimsMafia)
        _events += GameEvent.ClaimMade(speakerId, targetId, claimsMafia)
    }

    fun legalShotTargets(actorId: PlayerId): List<PlayerId> {
        val actor = player(actorId)
        if (phase != Phase.DAY || !actor.alive) return emptyList()
        val definition = actor.definition
        if (definition.dayAction != DayActionType.SHOOT) return emptyList()
        if ((shotsUsed[actorId] ?: 0) >= definition.dayActionUses) return emptyList()
        return alivePlayers().map { it.id }.filter { it != actorId }
    }

    // The target dies immediately and cannot be protected. A shot can end the game.
    fun submitDayShot(actorId: PlayerId, targetId: PlayerId) {
        check(phase == Phase.DAY) { "Shots can only be fired during the day" }
        require(targetId in legalShotTargets(actorId)) { "Illegal shot from $actorId at $targetId" }
        shotsUsed.merge(actorId, 1, Int::plus)
        val victim = player(targetId)
        _players[targetId] = victim.copy(alive = false)
        votes.remove(targetId)
        _events += GameEvent.PlayerShot(actorId, targetId, victim.role)
        checkWinner()
    }

    fun resolveDay() {
        check(phase == Phase.DAY) { "It is not day" }
        val validVotes = votes.filter { (voter, target) ->
            player(voter).alive && (target == null || player(target).alive)
        }
        val tally: Map<PlayerId?, Int> = validVotes.values.groupingBy { it }.eachCount()
        _events += GameEvent.VotesTallied(validVotes, tally)

        var executed: PlayerId? = null
        if (tally.isEmpty()) {
            _events += GameEvent.NoExecution(NoExecutionReason.NO_VOTES)
        } else {
            val max = tally.values.max()
            val top: List<PlayerId?> = tally.filterValues { it == max }.keys.toList()
            if (top.size == 1) {
                executed = executeOrSkip(top.single())
            } else if (config.tieRule == TieRule.RANDOM_AMONG_TIED) {
                executed = executeOrSkip(top.random(random))
            } else {
                _events += GameEvent.NoExecution(NoExecutionReason.TIE)
            }
        }
        _voteHistory += DayVotes(day, validVotes, executed)
        votes.clear()
        day++

        val executedPlayer = executed?.let { player(it) }
        if (executedPlayer != null && executedPlayer.definition.winsIfExecuted) {
            finish(executedPlayer.faction)
        } else if (!checkWinner()) {
            phase = Phase.NIGHT
            _events += GameEvent.NightBegan(day)
        }
    }

    private fun executeOrSkip(chosen: PlayerId?): PlayerId? {
        if (chosen == null) {
            _events += GameEvent.NoExecution(NoExecutionReason.NOBODY_CHOSEN)
            return null
        }
        val victim = player(chosen)
        _players[chosen] = victim.copy(alive = false)
        _events += GameEvent.PlayerExecuted(chosen, victim.role)
        return chosen
    }

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
        finish(result)
        return true
    }

    private fun finish(result: Faction) {
        winner = result
        phase = Phase.FINISHED
        _events += GameEvent.GameOver(result)
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
                    is GameEvent.PlayerShot -> put(it.targetId, it.role)
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
            legalShotTargets = legalShotTargets(playerId),
            investigations = knownInvestigations.filter { it.detectiveId == playerId },
            revealedRoles = revealed,
            claims = _claims.toList(),
            voteHistory = _voteHistory.toList(),
        )
    }

    companion object {
        fun create(
            playerCount: Int,
            config: GameConfig = GameConfig(),
            random: Random = Random.Default,
            names: List<String> = List(playerCount) { "Player ${it + 1}" },
        ): Game = Game(names, RoleSetups.forPlayers(playerCount).shuffled(random), config, random)

        // Roles are assigned by list index (id = index). Mainly for tests.
        fun withRoles(
            roles: List<RoleId>,
            config: GameConfig = GameConfig(),
            random: Random = Random.Default,
            names: List<String> = List(roles.size) { "Player ${it + 1}" },
        ): Game = Game(names, roles, config, random)
    }
}
