package pl.mafia.simulator

import pl.mafia.engine.Game
import pl.mafia.engine.GameConfig
import pl.mafia.engine.RoleSetups
import pl.mafia.engine.bots.Bot
import pl.mafia.engine.bots.HeuristicBot
import pl.mafia.engine.bots.InformedBot
import pl.mafia.engine.bots.RandomBot
import kotlin.random.Random

// Args (all optional): [setups] <games per size> <random|heuristic|informed> <seed>
// With "setups" the report is per role combination instead of per player count.
fun main(args: Array<String>) {
    val perSetup = args.firstOrNull() == "setups"
    val rest = if (perSetup) args.drop(1) else args.toList()
    val games = rest.getOrNull(0)?.toInt() ?: 10_000
    val botKind = rest.getOrNull(1) ?: "informed"
    val seed = rest.getOrNull(2)?.toLong() ?: 42L

    val random = Random(seed)
    val bot: Bot = when (botKind) {
        "random" -> RandomBot(random)
        "heuristic" -> HeuristicBot(random)
        "informed" -> InformedBot(random)
        else -> error("Unknown bot type: $botKind (expected random, heuristic or informed)")
    }

    val config = GameConfig()

    println("Games: $games, bots: $botKind, seed: $seed")
    println("Config: $config")
    println()
    if (perSetup) reportSetups(games, config, random, bot) else reportSizes(games, config, random, bot)
}

private fun reportSizes(games: Int, config: GameConfig, random: Random, bot: Bot) {
    println("%-8s %-10s %-10s %-10s %-10s".format("Players", "Town %", "Mafia %", "Neutral %", "Avg days"))
    for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
        val s = simulate(n, games, config, random, bot)
        println(
            "%-8d %-10.1f %-10.1f %-10.1f %-10.2f".format(
                s.playerCount, s.townPercent, s.mafiaPercent, s.neutralPercent, s.averageDays,
            ),
        )
    }
}

private fun reportSetups(games: Int, config: GameConfig, random: Random, bot: Bot) {
    println("%-8s %-28s %-10s %-10s %-10s".format("Players", "Mafia / special roles", "Town %", "Mafia %", "Neutral %"))
    for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
        for (setup in RoleSetups.combinations(n)) {
            val s = simulateGames(n, games, bot) {
                Game.withRoles(RoleSetups.build(n, setup).shuffled(random), config, random)
            }
            val label = "${setup.mafia}M " + setup.specials.joinToString("+").ifEmpty { "-" }
            println("%-8d %-28s %-10.1f %-10.1f %-10.1f".format(n, label, s.townPercent, s.mafiaPercent, s.neutralPercent))
        }
    }
}
