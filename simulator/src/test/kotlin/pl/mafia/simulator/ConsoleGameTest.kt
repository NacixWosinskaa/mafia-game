package pl.mafia.simulator

import pl.mafia.engine.Game
import pl.mafia.engine.GameConfig
import pl.mafia.engine.bots.InformedBot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConsoleGameTest {

    private val messages = Messages.load()

    private fun play(seed: Long, answer: (String) -> String?): List<String> {
        val random = Random(seed)
        val humanId = seed.toInt() % 8
        val game = Game.create(8, GameConfig(), random, List(8) { if (it == humanId) "Ty" else "Bot $it" })
        val lines = mutableListOf<String>()
        ConsoleGame(game, InformedBot(random), humanId, { answer(lines.last()) }, { lines += it }, messages).run()
        return lines
    }

    // First option everywhere; the menu choice is "go to voting".
    private fun passive(lastLine: String): String? =
        if (lastLine == messages.get("menu.prompt")) "4" else "1"

    @Test
    fun `a full game can be played to the end with scripted answers`() {
        for (seed in 0L..15L) {
            val lines = play(seed, ::passive)
            assertTrue(lines.any { it.startsWith("Wygrywa") }, "seed $seed has a winner")
            assertTrue(lines.contains(messages.get("end.roles")))
        }
    }

    @Test
    fun `the human never sees other players' roles before they die`() {
        val lines = play(3, ::passive)
        val firstRoleListing = lines.indexOf(messages.get("end.roles"))
        val roleNames = messages.get("role.MAFIOSO")
        assertTrue(lines.take(firstRoleListing).none { it.startsWith("  Bot") && it.contains(roleNames) && it.contains("(") })
    }

    @Test
    fun `closing the input ends the game with an exception`() {
        assertFailsWith<InputClosed> { play(1) { null } }
    }

    @Test
    fun `invalid input is asked again`() {
        var first = true
        val lines = play(2) { last ->
            if (first && last == messages.get("prompt.number")) {
                first = false
                "abc"
            } else {
                passive(last)
            }
        }
        assertTrue(lines.count { it == messages.get("prompt.invalid") } >= 1)
    }
}
