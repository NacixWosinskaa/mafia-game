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
    val sightings: List<Sighting>,
    val roleChecks: List<RoleCheck>,
    // The blackmailer picked you last night: your night action did nothing and you cannot vote today.
    val blockedLastNight: Boolean,
    // You will die at the end of tonight unless the doctor protects you.
    val poisoned: Boolean,
    // Night number -> players who died at night (kills, bodyguard sacrifices, avenger revenge).
    val nightDeaths: Map<Int, List<PlayerId>>,
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
    private val knownSightings = mutableListOf<Sighting>()
    private var blockedLastNight = emptySet<PlayerId>()
    private val knownRoleChecks = mutableListOf<RoleCheck>()
    private val poisoned = mutableSetOf<PlayerId>()
    private val nightActionUsed = mutableMapOf<PlayerId, Int>()
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

    // Everyone who won, set when the game ends: the winning faction including its dead members, or the neutral winner.
    var winners: Set<PlayerId> = emptySet()
        private set

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
        val type = effectiveNightAction(actor, _players) ?: return emptyList()
        val limit = actor.definition.nightActionUses.takeIf { type == actor.definition.nightAction }
        if (limit != null && (nightActionUsed[actorId] ?: 0) >= limit) return emptyList()
        return alivePlayers().filter { target ->
            when (type) {
                NightActionType.KILL -> target.faction != Faction.MAFIA
                NightActionType.INVESTIGATE, NightActionType.GUARD, NightActionType.WATCH,
                NightActionType.EXAMINE -> target.id != actor.id
                NightActionType.BLOCK -> target.faction != Faction.MAFIA
                NightActionType.POISON -> target.faction != Faction.MAFIA && target.id !in poisoned
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
        val result = NightResolver.resolve(_players, nightActions, killAllowed, day, poisoned.toSet())
        val blocked = result.blocks.values.toSet()

        _players.filter { it.alive && it.definition.nightAction == NightActionType.PROTECT }.forEach { doctor ->
            val target = nightActions[doctor.id]?.takeIf { doctor.id !in blocked }
            if (target != null) lastProtected[doctor.id] = target else lastProtected.remove(doctor.id)
        }

        result.blocks.forEach { (blocker, target) -> _events += GameEvent.ActionBlocked(blocker, target) }
        blockedLastNight = blocked
        result.preventedAttackOn?.let { _events += GameEvent.AttackPrevented(it) }
        result.sacrifice?.let { _events += GameEvent.BodyguardSacrificed(it.bodyguardId, it.targetId) }
        result.deaths.forEach { id ->
            kill(id)
            if (player(id).definition.revengeOnMafiaKill) {
                alivePlayers().filter { it.faction == Faction.MAFIA }.randomOrNull(random)?.let { mafioso ->
                    _events += GameEvent.RevengeTriggered(id, mafioso.id)
                    kill(mafioso.id)
                }
            }
        }
        result.cured.forEach {
            poisoned -= it
            _events += GameEvent.PoisonCured(it)
        }
        result.poisonDeaths.forEach {
            poisoned -= it
            if (player(it).alive) {
                _events += GameEvent.PoisonKilled(it)
                kill(it)
            }
        }
        poisoned.removeAll { !player(it).alive }
        result.poisonings.forEach { (poisonerId, targetId) ->
            if (player(targetId).alive) {
                poisoned += targetId
                nightActionUsed.merge(poisonerId, 1, Int::plus)
                _events += GameEvent.PlayerPoisoned(poisonerId, targetId)
            }
        }
        result.roleChecks.forEach {
            knownRoleChecks += it
            nightActionUsed.merge(it.sheriffId, 1, Int::plus)
            _events += GameEvent.Examined(it.sheriffId, it.targetId, it.role)
        }
        result.investigations.forEach {
            knownInvestigations += it
            _events += GameEvent.Investigated(it.detectiveId, it.targetId, it.isMafia)
        }
        result.sightings.forEach {
            knownSightings += it
            _events += GameEvent.Watched(it.lookoutId, it.targetId, it.visitorIds)
        }
        nightActions.clear()

        if (!checkWinner()) {
            phase = Phase.DAY
            _events += GameEvent.DayBegan(day)
        }
    }

    fun canVote(voterId: PlayerId): Boolean = phase == Phase.DAY && player(voterId).alive && voterId !in blockedLastNight

    fun legalVoteTargets(voterId: PlayerId): List<PlayerId> {
        if (!canVote(voterId)) return emptyList()
        return alivePlayers().map { it.id }.filter { it != voterId }
    }

    // A null target is a vote for nobody. Submitting again replaces the previous vote.
    fun submitVote(voterId: PlayerId, targetId: PlayerId?) {
        check(phase == Phase.DAY) { "Votes can only be submitted during the day" }
        require(player(voterId).alive) { "Dead player $voterId cannot vote" }
        require(voterId !in blockedLastNight) { "Player $voterId was blackmailed and cannot vote today" }
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
        if (config.shooterDiesOnMiss && victim.faction != Faction.MAFIA) {
            val shooter = player(actorId)
            _players[actorId] = shooter.copy(alive = false)
            votes.remove(actorId)
            _events += GameEvent.ShooterMissed(actorId, shooter.role)
        }
        checkWinner()
    }

    fun resolveDay() {
        check(phase == Phase.DAY) { "It is not day" }
        val validVotes = votes.filter { (voter, target) ->
            player(voter).alive && (target == null || player(target).alive)
        }
        val tally: Map<PlayerId?, Int> = validVotes.entries
            .groupBy({ it.value }, { player(it.key).definition.voteWeight })
            .mapValues { it.value.sum() }
        _events += GameEvent.VotesTallied(validVotes, tally)
        val today = day
        val aliveBefore = alivePlayers().size

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

        val votesAgainst = validVotes.values.count { it == executed }
        val executionWinner = executed?.let { player(it) }?.takeIf {
            it.definition.winsIfExecuted && today >= config.executionWinFromDay &&
                (!config.executionWinNeedsMajority || votesAgainst * 2 > aliveBefore)
        }
        if (executionWinner != null) {
            finish(executionWinner.faction, executionWinner.id)
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

    private fun finish(result: Faction, neutralWinner: PlayerId? = null) {
        winner = result
        winners = _players.filter { it.id == neutralWinner || (result != Faction.NEUTRAL && it.faction == result) }
            .map { it.id }.toSet()
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
            .firstOrNull { (actor, _) ->
                actor in teammates && player(actor).let { it.alive && effectiveNightAction(it, _players) == NightActionType.KILL }
            }
            ?.value
            ?.takeIf { effectiveNightAction(me, _players) == NightActionType.KILL }
        val revealed = buildMap<PlayerId, RoleId> {
            _events.forEach {
                when (it) {
                    is GameEvent.PlayerKilled -> put(it.playerId, it.role)
                    is GameEvent.PlayerExecuted -> put(it.playerId, it.role)
                    is GameEvent.PlayerShot -> put(it.targetId, it.role)
                    is GameEvent.ShooterMissed -> put(it.shooterId, it.role)
                    else -> Unit
                }
            }
        }
        var night = 0
        val nightDeaths = mutableMapOf<Int, MutableList<PlayerId>>()
        _events.forEach {
            if (it is GameEvent.NightBegan) night = it.day
            if (it is GameEvent.PlayerKilled) nightDeaths.getOrPut(night) { mutableListOf() } += it.playerId
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
            sightings = knownSightings.filter { it.lookoutId == playerId },
            roleChecks = knownRoleChecks.filter { it.sheriffId == playerId },
            blockedLastNight = playerId in blockedLastNight,
            poisoned = playerId in poisoned,
            nightDeaths = nightDeaths,
        )
    }

    companion object {
        fun create(
            playerCount: Int,
            config: GameConfig = GameConfig(),
            random: Random = Random.Default,
            names: List<String> = List(playerCount) { "Player ${it + 1}" },
        ): Game = Game(names, RoleSetups.forPlayers(playerCount, random).shuffled(random), config, random)

        // Roles are assigned by list index (id = index). Mainly for tests.
        fun withRoles(
            roles: List<RoleId>,
            config: GameConfig = GameConfig(),
            random: Random = Random.Default,
            names: List<String> = List(roles.size) { "Player ${it + 1}" },
        ): Game = Game(names, roles, config, random)
    }
}
