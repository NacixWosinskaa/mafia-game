package pl.mafia.simulator

import pl.mafia.engine.Faction
import pl.mafia.engine.Game
import pl.mafia.engine.GameConfig
import pl.mafia.engine.bots.Bot
import kotlin.random.Random

data class GameOutcome(val winner: Faction, val days: Int)

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
            game.submitVote(player.id, bot.chooseVote(game.viewFor(player.id)))
        }
        game.resolveDay()
    }
    return GameOutcome(game.winner!!, game.day)
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

fun simulate(
    playerCount: Int,
    games: Int,
    config: GameConfig,
    random: Random,
    bot: Bot,
): SizeStats = simulateGames(playerCount, games, bot) { Game.create(playerCount, config, random) }
