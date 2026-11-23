package pl.mafia.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameTest {

    // id: 0 mafioso, 1 lekarz, 2 detektyw, 3-5 obywatele
    private val roles = listOf(
        RoleId.MAFIOSO, RoleId.DOCTOR, RoleId.DETECTIVE,
        RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN,
    )

    private fun game(config: GameConfig = GameConfig()) = Game.withRoles(roles, config)

    private fun Game.everyoneVotes(target: (PlayerId) -> PlayerId?) {
        alivePlayers().forEach { submitVote(it.id, target(it.id)) }
    }

    @Test
    fun `domyslnie pierwsza noc jest bez zabojstwa`() {
        val g = game()
        g.submitNightAction(0, 3)
        g.resolveNight()
        assertEquals(6, g.alivePlayers().size)
        assertEquals(Phase.DAY, g.phase)
    }

    @Test
    fun `po wlaczeniu opcji mafia zabija juz pierwszej nocy`() {
        val g = game(GameConfig(killOnFirstNight = true))
        g.submitNightAction(0, 3)
        g.resolveNight()
        assertFalse(g.player(3).alive)
        assertTrue(g.events.any { it is GameEvent.PlayerKilled && it.playerId == 3 })
    }

    @Test
    fun `lekarz ratuje ofiare`() {
        val g = game(GameConfig(killOnFirstNight = true))
        g.submitNightAction(0, 3)
        g.submitNightAction(1, 3)
        g.resolveNight()
        assertTrue(g.player(3).alive)
        assertTrue(g.events.any { it is GameEvent.AttackPrevented && it.targetId == 3 })
    }

    @Test
    fun `mafia nie moze zabic swojego`() {
        val g = Game.withRoles(listOf(RoleId.MAFIOSO, RoleId.MAFIOSO, RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN, RoleId.CITIZEN))
        assertFailsWith<IllegalArgumentException> { g.submitNightAction(0, 1) }
    }

    @Test
    fun `obywatel nie ma akcji nocnej`() {
        val g = game()
        assertFailsWith<IllegalArgumentException> { g.submitNightAction(3, 0) }
    }

    @Test
    fun `detektyw dostaje wynik sledztwa w swoim widoku`() {
        val g = game()
        g.submitNightAction(2, 0)
        g.resolveNight()
        assertEquals(listOf(Investigation(2, 0, true)), g.viewFor(2).investigations)
        assertEquals(emptyList(), g.viewFor(3).investigations)
    }

    @Test
    fun `widok obywatela nie zdradza rol innych graczy`() {
        val g = game()
        val view = g.viewFor(3)
        assertEquals(RoleId.CITIZEN, view.role)
        assertEquals(emptyList(), view.mafiaTeammates)
        assertEquals(emptyMap(), view.revealedRoles)
    }

    @Test
    fun `lekarz nie moze chronic tej samej osoby dwie noce z rzedu`() {
        val g = game()
        g.submitNightAction(1, 3)
        g.resolveNight()
        g.everyoneVotes { null }
        g.resolveDay()
        assertEquals(Phase.NIGHT, g.phase)
        val targets = g.legalNightTargets(1)
        assertFalse(3 in targets)
        assertTrue(1 in targets, "lekarz może chronić siebie")
        assertFailsWith<IllegalArgumentException> { g.submitNightAction(1, 3) }
    }

    @Test
    fun `lekarz moze chronic siebie tylko gdy pozwala na to konfiguracja`() {
        val g = game(GameConfig(doctorCanSelfProtect = false))
        assertFalse(1 in g.legalNightTargets(1))
    }

    @Test
    fun `wiekszosc glosow egzekuje gracza`() {
        val g = game()
        g.resolveNight()
        g.everyoneVotes { id -> if (id == 3) 0 else 3 }
        g.resolveDay()
        assertFalse(g.player(3).alive)
        assertTrue(g.events.any { it is GameEvent.PlayerExecuted && it.playerId == 3 })
    }

    @Test
    fun `remis oznacza brak egzekucji`() {
        val g = game()
        g.resolveNight()
        // 3 głosy na gracza 1, 3 głosy na gracza 0
        g.everyoneVotes { id -> if (id in listOf(0, 2, 4)) 1 else 0 }
        g.resolveDay()
        assertEquals(6, g.alivePlayers().size)
        assertTrue(g.events.any { it == GameEvent.NoExecution(NoExecutionReason.TIE) })
    }

    @Test
    fun `remis moze byc rozstrzygany losowo po zmianie konfiguracji`() {
        val g = game(GameConfig(tieRule = TieRule.RANDOM_AMONG_TIED))
        g.resolveNight()
        g.everyoneVotes { id -> if (id in listOf(0, 2, 4)) 1 else 0 }
        g.resolveDay()
        assertEquals(5, g.alivePlayers().size)
    }

    @Test
    fun `glosy na nikogo nie egzekuja nikogo`() {
        val g = game()
        g.resolveNight()
        g.everyoneVotes { null }
        g.resolveDay()
        assertEquals(6, g.alivePlayers().size)
        assertTrue(g.events.any { it == GameEvent.NoExecution(NoExecutionReason.NOBODY_CHOSEN) })
    }

    @Test
    fun `miasto wygrywa po straceniu ostatniego mafiosa`() {
        val g = game()
        g.resolveNight()
        g.everyoneVotes { id -> if (id == 0) 1 else 0 }
        g.resolveDay()
        assertTrue(g.isOver)
        assertEquals(Faction.TOWN, g.winner)
    }

    @Test
    fun `mafia wygrywa gdy ma co najmniej tyle osob co reszta`() {
        val g = Game.withRoles(
            listOf(RoleId.MAFIOSO, RoleId.MAFIOSO, RoleId.CITIZEN, RoleId.CITIZEN),
            GameConfig(killOnFirstNight = true),
        )
        g.submitNightAction(0, 2)
        g.resolveNight()
        assertTrue(g.isOver)
        assertEquals(Faction.MAFIA, g.winner)
    }

    @Test
    fun `po zakonczeniu gry nie mozna juz nic zglaszac`() {
        val g = game()
        g.resolveNight()
        g.everyoneVotes { id -> if (id == 0) 1 else 0 }
        g.resolveDay()
        assertFailsWith<IllegalStateException> { g.submitVote(1, 2) }
        assertFailsWith<IllegalStateException> { g.resolveNight() }
    }
}
