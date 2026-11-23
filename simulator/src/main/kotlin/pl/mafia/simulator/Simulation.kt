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
    val averageDays: Double,
) {
    val townPercent: Double get() = 100.0 * townWins / games
    val mafiaPercent: Double get() = 100.0 * mafiaWins / games
}

fun playGame(playerCount: Int, config: GameConfig, random: Random, bot: Bot): GameOutcome {
    val game = Game.create(playerCount, config, random)
    var rounds = 0
    while (!game.isOver) {
        check(++rounds < 500) { "Gra nie kończy się, więc w zasadach jest błąd" }

        for (player in game.alivePlayers()) {
            if (game.legalNightTargets(player.id).isEmpty()) continue
            val target = bot.chooseNightTarget(game.viewFor(player.id)) ?: continue
            game.submitNightAction(player.id, target)
        }
        game.resolveNight()
        if (game.isOver) break

        for (player in game.alivePlayers()) {
            game.submitVote(player.id, bot.chooseVote(game.viewFor(player.id)))
        }
        game.resolveDay()
    }
    return GameOutcome(game.winner!!, game.day)
}

fun simulate(
    playerCount: Int,
    games: Int,
    config: GameConfig,
    random: Random,
    bot: Bot,
): SizeStats {
    var town = 0
    var mafia = 0
    var days = 0L
    repeat(games) {
        val outcome = playGame(playerCount, config, random, bot)
        when (outcome.winner) {
            Faction.TOWN -> town++
            Faction.MAFIA -> mafia++
            Faction.NEUTRAL -> Unit
        }
        days += outcome.days
    }
    return SizeStats(playerCount, games, town, mafia, days.toDouble() / games)
}
