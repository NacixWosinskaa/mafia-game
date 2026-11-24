package pl.mafia.simulator

import pl.mafia.engine.Game
import pl.mafia.engine.GameConfig
import pl.mafia.engine.RoleSetups
import pl.mafia.engine.bots.Bot
import pl.mafia.engine.bots.HeuristicBot
import pl.mafia.engine.bots.InformedBot
import pl.mafia.engine.bots.RandomBot
import kotlin.random.Random

// Args (all optional): [setups|roles|range|effects] <games per size> <random|heuristic|informed> <seed>
// With "setups" the report is per role combination, with "roles" per role, instead of per player count.
// "range" and "effects" ignore the bot type and play every town temperament (see Temperaments.kt).
fun main(args: Array<String>) {
    val mode = args.firstOrNull()?.takeIf { it in setOf("setups", "roles", "range", "effects") }
    val rest = if (mode != null) args.drop(1) else args.toList()
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
    when (mode) {
        "setups" -> reportSetups(games, config, random, bot)
        "roles" -> reportRoles(games, config, random, bot)
        "range" -> reportRange(games, config, seed)
        "effects" -> reportEffects(games, config, seed)
        else -> reportSizes(games, config, random, bot)
    }
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

private fun reportRange(games: Int, config: GameConfig, seed: Long) {
    println("Town win % by town temperament")
    println("%-8s %s %-8s %-8s".format("Players", temperaments.joinToString(" ") { "%-11s".format(it.name) }, "Min", "Max"))
    for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
        val rates = temperaments.map { simulate(n, games, config, Random(seed + n), it.create(Random(seed + n))).townPercent }
        println("%-8d %s %-8.1f %-8.1f".format(n, rates.joinToString(" ") { "%-11.1f".format(it) }, rates.min(), rates.max()))
    }
}

private fun reportEffects(games: Int, config: GameConfig, seed: Long) {
    val effects = temperaments.map { simulateRoleEffects(games, config, Random(seed), it.create(Random(seed))) }
    println("Change in town win rate (points) when the role is in the setup")
    println("%-14s %s %-8s".format("Role", temperaments.joinToString(" ") { "%-11s".format(it.name) }, "Spread"))
    val roles = effects.first().keys.sortedBy { it.ordinal }
    for (role in roles) {
        val values = effects.map { it.getValue(role) }
        println("%-14s %s %-8.1f".format(role, values.joinToString(" ") { "%-+11.1f".format(it) }, values.max() - values.min()))
    }
}

private fun reportRoles(games: Int, config: GameConfig, random: Random, bot: Bot) {
    println("%-14s %-10s %-10s".format("Role", "Players", "Win %"))
    for (s in simulateRoles(games, config, random, bot)) {
        println("%-14s %-10d %-10.1f".format(s.role, s.games, s.winPercent))
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
