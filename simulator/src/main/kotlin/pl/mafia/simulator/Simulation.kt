package pl.mafia.simulator

import pl.mafia.engine.Faction
import pl.mafia.engine.Game
import pl.mafia.engine.GameConfig
import pl.mafia.engine.RoleId
import pl.mafia.engine.RoleSetups
import pl.mafia.engine.bots.Bot
import kotlin.random.Random

// roles and winnerRoles are the roles dealt at the start, one entry per player.
data class GameOutcome(val winner: Faction, val days: Int, val roles: List<RoleId>, val winnerRoles: List<RoleId>)

data class SizeStats(
    val playerCount: Int,
    val games: Int,
    val townWins: Int,
    val mafiaWins: Int,
    val neutralWins: Int,
    val averageDays: Double,
) {
    val townPercent: Double get() = 100.0 * townWins / games
    val mafiaPercent: Double get() = 100.0 * mafiaWins / games
    val neutralPercent: Double get() = 100.0 * neutralWins / games
}

fun playGame(game: Game, bot: Bot): GameOutcome {
    var rounds = 0
    val dealt = game.players.map { it.role }
    while (!game.isOver) {
        check(++rounds < 500) { "Game does not terminate, the rules have a bug" }

        for (player in game.alivePlayers()) {
            if (game.legalNightTargets(player.id).isEmpty()) continue
            val target = bot.chooseNightTarget(game.viewFor(player.id)) ?: continue
            game.submitNightAction(player.id, target)
        }
        game.resolveNight()
        if (game.isOver) break

        for (player in game.alivePlayers()) {
            for (claim in bot.chooseClaims(game.viewFor(player.id))) {
                game.submitClaim(player.id, claim.targetId, claim.claimsMafia)
            }
        }
        for (player in game.alivePlayers()) {
            if (game.isOver || !game.player(player.id).alive) continue
            val target = bot.chooseShot(game.viewFor(player.id)) ?: continue
            game.submitDayShot(player.id, target)
        }
        if (game.isOver) break

        for (player in game.alivePlayers()) {
            if (!game.canVote(player.id)) continue
            game.submitVote(player.id, bot.chooseVote(game.viewFor(player.id)))
        }
        game.resolveDay()
    }
    return GameOutcome(game.winner!!, game.day, dealt, game.winners.sorted().map { dealt[it] })
}

fun simulateGames(playerCount: Int, games: Int, bot: Bot, newGame: () -> Game): SizeStats {
    var town = 0
    var mafia = 0
    var neutral = 0
    var days = 0L
    repeat(games) {
        val outcome = playGame(newGame(), bot)
        when (outcome.winner) {
            Faction.TOWN -> town++
            Faction.MAFIA -> mafia++
            Faction.NEUTRAL -> neutral++
        }
        days += outcome.days
    }
    return SizeStats(playerCount, games, town, mafia, neutral, days.toDouble() / games)
}

data class RoleStats(val role: RoleId, val games: Int, val wins: Int) {
    val winPercent: Double get() = 100.0 * wins / games
}

// Win rate of each role over random setups of every supported size, one entry per dealt player.
fun simulateRoles(gamesPerSize: Int, config: GameConfig, random: Random, bot: Bot): List<RoleStats> {
    val played = mutableMapOf<RoleId, Int>()
    val won = mutableMapOf<RoleId, Int>()
    for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
        repeat(gamesPerSize) {
            val outcome = playGame(Game.create(n, config, random), bot)
            outcome.roles.forEach { played.merge(it, 1, Int::plus) }
            outcome.winnerRoles.forEach { won.merge(it, 1, Int::plus) }
        }
    }
    return played.map { (role, games) -> RoleStats(role, games, won[role] ?: 0) }.sortedBy { it.role.ordinal }
}

// How much a setup's town win rate moves with each role, against the mean of setups with the same
// player and mafia count. Each setup is played `gamesPerSetup` times.
fun simulateRoleEffects(gamesPerSetup: Int, config: GameConfig, random: Random, bot: Bot): Map<RoleId, Double> {
    val results = mutableListOf<Triple<Pair<Int, Int>, List<RoleId>, Double>>()
    for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
        for (setup in RoleSetups.combinations(n)) {
            val stats = simulateGames(n, gamesPerSetup, bot) {
                Game.withRoles(RoleSetups.build(n, setup).shuffled(random), config, random)
            }
            results += Triple(n to setup.mafia, setup.specials, stats.townPercent)
        }
    }
    val baseline = results.groupBy({ it.first }, { it.third }).mapValues { it.value.average() }
    val deltas = mutableMapOf<RoleId, MutableList<Double>>()
    for ((key, specials, town) in results) specials.forEach { deltas.getOrPut(it) { mutableListOf() } += town - baseline.getValue(key) }
    return deltas.mapValues { it.value.average() }
}

fun simulate(
    playerCount: Int,
    games: Int,
    config: GameConfig,
    random: Random,
    bot: Bot,
): SizeStats = simulateGames(playerCount, games, bot) { Game.create(playerCount, config, random) }
