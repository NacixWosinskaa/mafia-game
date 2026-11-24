package pl.mafia.simulator

import pl.mafia.engine.Faction
import pl.mafia.engine.Game
import pl.mafia.engine.GameEvent
import pl.mafia.engine.PlayerId
import pl.mafia.engine.RoleId
import pl.mafia.engine.Roles
import pl.mafia.engine.bots.Bot
import pl.mafia.engine.effectiveNightAction
import java.io.InputStreamReader
import java.util.Properties

class Messages(private val texts: Properties) {
    fun get(key: String, vararg args: Any?): String =
        String.format(texts.getProperty(key) ?: error("Missing message: $key"), *args)

    fun list(key: String): List<String> = get(key).split(",")

    companion object {
        fun load(resource: String = "/play-pl.properties"): Messages {
            val stream = Messages::class.java.getResourceAsStream(resource) ?: error("Missing resource $resource")
            return Messages(Properties().apply { load(InputStreamReader(stream, Charsets.UTF_8)) })
        }
    }
}

class InputClosed : RuntimeException("Input closed")

// One human seat against bots. The human only gets what a player may know (PlayerView and public events).
class ConsoleGame(
    private val game: Game,
    private val bot: Bot,
    private val humanId: PlayerId,
    private val input: () -> String?,
    private val output: (String) -> Unit,
    private val messages: Messages,
) {
    private var eventCursor = 0
    private var seenInvestigations = 0
    private var seenRoleChecks = 0
    private var seenSightings = 0

    private fun name(id: PlayerId) = game.player(id).name
    private fun roleName(role: RoleId) = messages.get("role.$role")
    private val human get() = game.player(humanId)

    fun run() {
        intro()
        while (!game.isOver) {
            night()
            if (game.isOver) break
            day()
        }
        ending()
    }

    private fun intro() {
        val view = game.viewFor(humanId)
        output(messages.get("intro.players", game.players.joinToString(", ") { it.name }))
        output(messages.get("intro", human.name, roleName(human.role)))
        output(messages.get("ability.${human.role}"))
        if (view.mafiaTeammates.isNotEmpty()) {
            output(messages.get("intro.teammates", view.mafiaTeammates.joinToString(", ") { name(it) }))
        }
        output("")
    }

    private fun night() {
        output(messages.get("night.header", game.day))
        if (!human.alive) output(messages.get("spectating"))
        val type = effectiveNightAction(human, game.players)
        val targets = game.legalNightTargets(humanId)
        if (human.alive && type != null && targets.isNotEmpty()) {
            val choice = choose(messages.get("prompt.night.$type"), targets, allowPass = true)
            if (choice != null) game.submitNightAction(humanId, choice)
        }
        for (player in game.alivePlayers()) {
            if (player.id == humanId || game.legalNightTargets(player.id).isEmpty()) continue
            val target = bot.chooseNightTarget(game.viewFor(player.id)) ?: continue
            game.submitNightAction(player.id, target)
        }
        val deathsBefore = game.events.count { it is GameEvent.PlayerKilled }
        game.resolveNight()
        if (game.events.count { it is GameEvent.PlayerKilled } == deathsBefore) output(messages.get("night.nobody"))
        printNewEvents()
        printPrivateInfo()
        output("")
    }

    private fun day() {
        output(messages.get("day.header", game.day))
        for (player in game.alivePlayers()) {
            if (player.id == humanId) continue
            for (claim in bot.chooseClaims(game.viewFor(player.id))) game.submitClaim(player.id, claim.targetId, claim.claimsMafia)
        }
        for (player in game.alivePlayers()) {
            if (player.id == humanId || game.isOver) continue
            if (!game.player(player.id).alive) continue
            bot.chooseShot(game.viewFor(player.id))?.let { game.submitDayShot(player.id, it) }
        }
        printNewEvents()
        if (game.isOver) return
        if (human.alive) humanMenu()
        if (game.isOver) return

        for (player in game.alivePlayers()) {
            if (player.id == humanId || !game.canVote(player.id)) continue
            game.submitVote(player.id, bot.chooseVote(game.viewFor(player.id)))
        }
        if (human.alive && game.canVote(humanId)) {
            game.submitVote(humanId, choose(messages.get("vote.prompt"), game.legalVoteTargets(humanId), allowPass = true))
        }
        game.resolveDay()
        printNewEvents()
        output("")
    }

    private fun humanMenu() {
        while (true) {
            val view = game.viewFor(humanId)
            output(messages.get("menu.claim"))
            if (view.legalShotTargets.isNotEmpty()) output(messages.get("menu.shoot"))
            output(messages.get("menu.status"))
            output(messages.get("menu.vote"))
            output(messages.get("menu.prompt"))
            when (readLine()?.trim()) {
                "1" -> humanClaim()
                "2" -> if (humanShoot()) return
                "3" -> status()
                "4" -> return
                else -> output(messages.get("prompt.invalid"))
            }
            if (game.isOver || !human.alive) return
        }
    }

    private fun humanClaim() {
        val already = game.viewFor(humanId).claims.filter { it.speakerId == humanId }.map { it.targetId }.toSet()
        val options = game.alivePlayers().map { it.id }.filter { it != humanId && it !in already }
        if (options.isEmpty()) {
            output(messages.get("claim.none"))
            return
        }
        val target = choose(messages.get("claim.prompt"), options, allowPass = true) ?: return
        output(messages.get("claim.kind"))
        val kind = readLine()?.trim()
        if (kind != "1" && kind != "2") {
            output(messages.get("prompt.invalid"))
            return
        }
        game.submitClaim(humanId, target, kind == "1")
        printNewEvents()
    }

    // True when the shot was fired.
    private fun humanShoot(): Boolean {
        val options = game.legalShotTargets(humanId)
        if (options.isEmpty()) {
            output(messages.get("shoot.none"))
            return false
        }
        val target = choose(messages.get("shoot.prompt"), options, allowPass = true) ?: return false
        game.submitDayShot(humanId, target)
        printNewEvents()
        return true
    }

    private fun status() {
        val view = game.viewFor(humanId)
        output(messages.get("status.role", roleName(human.role)))
        output(messages.get("status.alive", view.alive.joinToString(", ") { name(it) }))
        val dead = game.players.filter { !it.alive }
        if (dead.isNotEmpty()) {
            output(messages.get("status.dead", dead.joinToString(", ") { "${it.name} (${roleName(view.revealedRoles[it.id] ?: it.role)})" }))
        }
        if (view.claims.isEmpty()) output(messages.get("status.noclaims")) else {
            output(messages.get("status.claims"))
            view.claims.forEach { output("  " + claimText(it.speakerId, it.targetId, it.claimsMafia)) }
        }
    }

    private fun claimText(speaker: PlayerId, target: PlayerId, mafia: Boolean) =
        messages.get(if (mafia) "claim.mafia" else "claim.town", name(speaker), name(target))

    private fun printNewEvents() {
        val events = game.events
        for (event in events.drop(eventCursor)) {
            when (event) {
                is GameEvent.PlayerKilled -> output(messages.get("death.night", name(event.playerId), roleName(event.role)))
                is GameEvent.PlayerExecuted -> output(messages.get("execution", name(event.playerId), roleName(event.role)))
                is GameEvent.NoExecution -> output(messages.get("noexec.${event.reason}"))
                is GameEvent.PlayerShot -> output(messages.get("shot", name(event.shooterId), name(event.targetId), roleName(event.role)))
                is GameEvent.ShooterMissed -> output(messages.get("shooter.missed", name(event.shooterId), roleName(event.role)))
                is GameEvent.ClaimMade -> output(claimText(event.speakerId, event.targetId, event.claimsMafia))
                is GameEvent.VotesTallied -> {
                    output(messages.get("votes.header"))
                    event.votes.forEach { (voter, target) ->
                        output(messages.get("votes.line", name(voter), target?.let { name(it) } ?: messages.get("votes.nobody")))
                    }
                }
                else -> Unit
            }
        }
        eventCursor = events.size
    }

    private fun printPrivateInfo() {
        val view = game.viewFor(humanId)
        view.investigations.drop(seenInvestigations).forEach {
            output(messages.get(if (it.isMafia) "info.investigation.mafia" else "info.investigation.clean", name(it.targetId)))
        }
        view.roleChecks.drop(seenRoleChecks).forEach { output(messages.get("info.rolecheck", name(it.targetId), roleName(it.role))) }
        view.sightings.drop(seenSightings).forEach {
            output(
                if (it.visitorIds.isEmpty()) messages.get("info.sighting.none", name(it.targetId))
                else messages.get("info.sighting", name(it.targetId), it.visitorIds.joinToString(", ") { id -> name(id) }),
            )
        }
        seenInvestigations = view.investigations.size
        seenRoleChecks = view.roleChecks.size
        seenSightings = view.sightings.size
        if (view.poisoned) output(messages.get("info.poisoned"))
        if (view.blockedLastNight) output(messages.get("info.blocked"))
    }

    private fun ending() {
        printNewEvents()
        val winner = game.winner!!
        output(messages.get("end.winner.$winner"))
        output(messages.get(if (humanId in game.winners) "end.you.won" else "end.you.lost"))
        output(messages.get("end.roles"))
        game.players.forEach {
            output(messages.get("end.role.line", it.name, roleName(it.role), messages.get(if (it.alive) "end.alive" else "end.dead")))
        }
    }

    private fun readLine(): String? = input() ?: throw InputClosed()

    // Returns the chosen player, or null for "pass". Options are numbered from 1.
    private fun choose(prompt: String, options: List<PlayerId>, allowPass: Boolean): PlayerId? {
        while (true) {
            output(prompt)
            options.forEachIndexed { index, id -> output("${index + 1}) ${name(id)}") }
            if (allowPass) output(messages.get("prompt.pass"))
            output(messages.get("prompt.number"))
            val number = readLine()?.trim()?.toIntOrNull()
            when {
                number == 0 && allowPass -> return null
                number != null && number in 1..options.size -> return options[number - 1]
                else -> output(messages.get("prompt.invalid"))
            }
        }
    }
}
