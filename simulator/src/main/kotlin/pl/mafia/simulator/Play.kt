package pl.mafia.simulator

import pl.mafia.engine.Game
import pl.mafia.engine.GameConfig
import pl.mafia.engine.RoleSetups
import pl.mafia.engine.bots.InformedBot
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.random.Random

// Args (all optional): [players] [seed] [your name]
fun playConsole(args: List<String>) {
    // Polish letters must survive on consoles whose default charset is not UTF-8.
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8))
    val messages = Messages.load()
    val playerCount = args.getOrNull(0)?.toInt() ?: 10
    val seed = args.getOrNull(1)?.toLong() ?: System.nanoTime()
    val humanName = args.getOrNull(2) ?: messages.get("human.default")
    require(playerCount in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
        "Players must be in ${RoleSetups.MIN_PLAYERS}..${RoleSetups.MAX_PLAYERS}"
    }

    val random = Random(seed)
    val humanId = random.nextInt(playerCount)
    val botNames = messages.list("names").shuffled(random).iterator()
    val names = List(playerCount) { if (it == humanId) humanName else botNames.next() }
    val game = Game.create(playerCount, GameConfig(), random, names)
    val bot = InformedBot(random, trust = 0.6, noise = 1.0, candor = 0.6, cunning = 0.5)
    try {
        ConsoleGame(game, bot, humanId, ::readLine, ::println, messages).run()
    } catch (e: InputClosed) {
        println()
    }
}
