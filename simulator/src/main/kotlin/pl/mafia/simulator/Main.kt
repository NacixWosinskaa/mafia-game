package pl.mafia.simulator

import pl.mafia.engine.GameConfig
import pl.mafia.engine.RoleSetups
import pl.mafia.engine.bots.Bot
import pl.mafia.engine.bots.HeuristicBot
import pl.mafia.engine.bots.RandomBot
import kotlin.random.Random

/**
 * Użycie: ./gradlew :simulator:run --args="<liczba gier> <random|heuristic> <seed>"
 * Wszystkie argumenty są opcjonalne (domyślnie: 10000 heuristic 42).
 */
fun main(args: Array<String>) {
    val games = args.getOrNull(0)?.toInt() ?: 10_000
    val botKind = args.getOrNull(1) ?: "heuristic"
    val seed = args.getOrNull(2)?.toLong() ?: 42L

    val random = Random(seed)
    val bot: Bot = when (botKind) {
        "random" -> RandomBot(random)
        "heuristic" -> HeuristicBot(random)
        else -> error("Nieznany typ bota: $botKind (dostępne: random, heuristic)")
    }

    // Reguły do eksperymentów zmieniasz tutaj, np. GameConfig(killOnFirstNight = true)
    val config = GameConfig()

    println("Gier na rozmiar: $games, boty: $botKind, seed: $seed")
    println("Konfiguracja: $config")
    println()
    println("%-8s %-10s %-10s %-10s".format("Graczy", "Miasto %", "Mafia %", "Sr. dni"))
    for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
        val s = simulate(n, games, config, random, bot)
        println(
            "%-8d %-10.1f %-10.1f %-10.2f".format(
                s.playerCount, s.townPercent, s.mafiaPercent, s.averageDays,
            ),
        )
    }
}
