package pl.mafia.simulator

import pl.mafia.engine.GameConfig
import pl.mafia.engine.RoleSetups
import pl.mafia.engine.TieRule
import pl.mafia.engine.bots.HeuristicBot
import pl.mafia.engine.bots.InformedBot
import pl.mafia.engine.bots.RandomBot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulationTest {

    @Test
    fun `random bots finish every game without errors`() {
        val random = Random(1)
        val bot = RandomBot(random)
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            val stats = simulate(n, 300, GameConfig(), random, bot)
            assertEquals(300, stats.townWins + stats.mafiaWins)
        }
    }

    @Test
    fun `heuristic bots finish every game without errors`() {
        val random = Random(2)
        val bot = HeuristicBot(random)
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            val stats = simulate(n, 300, GameConfig(), random, bot)
            assertEquals(300, stats.townWins + stats.mafiaWins)
        }
    }

    @Test
    fun `informed bots finish every game without errors`() {
        val random = Random(4)
        val bot = InformedBot(random)
        for (n in RoleSetups.MIN_PLAYERS..RoleSetups.MAX_PLAYERS) {
            val stats = simulate(n, 300, GameConfig(), random, bot)
            assertEquals(300, stats.townWins + stats.mafiaWins)
        }
    }

    @Test
    fun `simulation works with changed rules`() {
        val random = Random(3)
        val bot = HeuristicBot(random)
        val config = GameConfig(killOnFirstNight = true, tieRule = TieRule.RANDOM_AMONG_TIED)
        val stats = simulate(8, 300, config, random, bot)
        assertEquals(300, stats.townWins + stats.mafiaWins)
    }

    @Test
    fun `same seed gives the same results`() {
        val a = simulate(8, 200, GameConfig(), Random(7), HeuristicBot(Random(7)))
        val b = simulate(8, 200, GameConfig(), Random(7), HeuristicBot(Random(7)))
        assertEquals(a, b)
    }
}
