package pl.mafia.simulator

import pl.mafia.engine.bots.Bot
import pl.mafia.engine.bots.HeuristicBot
import pl.mafia.engine.bots.InformedBot
import kotlin.random.Random

// Town playing styles from "barely reads the table" to "believes every claim".
// The mafia bluffs harder (vouches for teammates) the less the town trusts.
class Temperament(val name: String, val create: (Random) -> Bot)

val temperaments = listOf(
    Temperament("careless") { HeuristicBot(it) },
    Temperament("suspicious") { InformedBot(it, trust = 0.3, noise = 2.0, candor = 0.3, cunning = 1.0) },
    Temperament("careful") { InformedBot(it, trust = 0.6, noise = 1.0, candor = 0.6, cunning = 0.5) },
    Temperament("trusting") { InformedBot(it, trust = 1.0, noise = 0.0, candor = 1.0, cunning = 0.0) },
)
