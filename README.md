# Mafia game

A turn-based Mafia game with extra roles, a configurable rule set and (later) generated lore. This repo is phase 1: a rules engine plus a simulator that plays thousands of bot games to check balance. There is no app, network or UI yet.

## Modules

- `engine` is a plain Kotlin library with the game rules and the bots.
- `simulator` is a console program that runs bot games and prints win rates per player count.

## Running it

Open the root folder in IntelliJ as a Gradle project, or use the terminal (`gradlew.bat` on Windows):

```
./gradlew test
./gradlew :simulator:run
./gradlew :simulator:run --args="20000 random 7"   # <games per size> <random|heuristic|informed> <seed>
```

## How the engine works

A `Game` is a state machine: night, resolve, day (vote), execution, win check. Callers submit actions with `submitNightAction` / `submitVote` and advance with `resolveNight()` / `resolveDay()`. The engine has no clock; timers belong to whatever drives it.

- Roles are data (`Roles` in `Model.kt`): a faction, an optional night action, an optional day action with a use limit, and a flag for "wins if executed".
- Night order lives in `NightResolver`: protect, then kill, then investigate.
- Players and bots only see a `PlayerView`, never the full state. Public information in it: revealed roles of the dead, all `Claim`s and the vote history (`DayVotes`).
- During the day a player can make a `Claim` ("I investigated X, they are / are not mafia") with `submitClaim`. The engine does not check truth, so mafia can lie.
- The engine emits `GameEvent`s describing what happened. The future lore layer will turn those into text.

## Roles

| Role | Faction | Ability |
| --- | --- | --- |
| Citizen | town | none |
| Mafioso | mafia | team picks one kill target per night |
| Detective | town | learns whether one player is mafia, each night |
| Doctor | town | protects one player per night |
| Day shooter | town | one shot per game during the day; the target dies at once, can't be protected, role is revealed. If the target is not mafia the shooter dies too |
| Avenger | town | if the mafia kills them at night, a random living mafioso dies as well (not on a day shot or an execution) |
| Godfather | mafia | kills like a mafioso; the detective sees them as not mafia |
| Blackmailer | mafia | does not kill; each night cancels one non-mafia player's night action (they are told they were blocked) |
| Bodyguard | town | guards one player; if the mafia attacks that player, the bodyguard dies instead. The doctor's protection is applied first |
| Lookout | town | learns who else acted on the chosen player that night (blocked players still count as visitors) |
| Joker | neutral | wins alone if executed by the vote from day 3 on; executed earlier or dying any other way is just a loss |

Night order: block, protect, kill (a guard takes the hit), investigate and watch. Mafia specials are part of the mafia headcount and count as mafia for the win check.

Planned, not implemented: the jailor (picks a player at night; that player can't talk, vote or act the next day and has a private chat with the jailor). It needs chat, so it waits for the backend.

## Setups

A setup is built by `RoleSetups` for 6-14 players: mafia count from a table, always one detective, a doctor (guaranteed from 8 players, optional below), a few special roles, citizens for the rest.

- Special roles are drawn without replacement from a pool (shooter, avenger, joker, bodyguard, lookout), so none repeats in a game. Only citizens and mafiosi repeat.
- Godfather and blackmailer replace mafiosi from 8 players on: at most one below 12 players, two from 12, and always at least one plain mafioso.
- Below 12 players at most one neutral role and one "bait" role (joker, avenger); from 12, two of each.
- Mafia by player count: 1 (6), 2 (7-9), 2 or 3 drawn per game (10-11), 3 (12-14).
- Slots for special roles by player count: 0-1 (6-7, where the doctor is one of them), 1 (8-9), 2 (10-11), 3 (12-14). From 12 players there are around 40 allowed setups per size.
- `RoleSetups.combinations(n)` lists every allowed `Setup` (mafia count + specials). To drop one after looking at the simulator, add it to `rejected`.

## Rules and where to change them

| Rule | Default | Where |
| --- | --- | --- |
| Tie in the vote | no execution | `GameConfig.tieRule` |
| First night | no kill | `GameConfig.killOnFirstNight` |
| Doctor protects self | yes | `GameConfig.doctorCanSelfProtect` |
| Doctor repeats a target | no | `GameConfig.doctorCanRepeatTarget` |
| Shooter dies when the target is not mafia | yes | `GameConfig.shooterDiesOnMiss` |
| Detective result | mafia / not mafia | `NightResolver` |
| Vote | most votes wins, "nobody" is a valid vote | `Game.resolveDay` |
| Joker wins by execution from day | 3 | `GameConfig.executionWinFromDay` |
| Joker needs a majority of the living | no | `GameConfig.executionWinNeedsMajority` |
| Win | town: no mafia left; mafia: at least as many as everyone else (the joker counts as "else"); joker: executed by vote | `Game.checkWinner`, `Game.resolveDay` |
| Role counts per player count (6-14) | see `RoleSetups` | `RoleSetups` |

To try a rule change, edit the `GameConfig()` line in `simulator/.../Main.kt` and rerun.

## Adding a role

1. Add it to `RoleId` and `Roles`.
2. If it has a new night action, add a `NightActionType`, its target rules in `Game.legalNightTargets` and its place in `NightResolver`. Day actions follow the pattern of `Game.submitDayShot`.
3. Teach `InformedBot` about it (`chooseShot`, `chooseClaims`, ...), otherwise it plays like a citizen.
4. Add it to `RoleSetups` and write tests.

## Bots

| Bot | Behaviour |
| --- | --- |
| `random` | Random legal choices. Lower bound. |
| `heuristic` | Random, but mafia coordinate kills and the detective checks new people. No claims, never shoots. |
| `informed` | Detective announces results, mafia bluff and hunt accusers, doctor protects accusers, the shooter fires on strong evidence only, the joker makes provocative accusations, everyone else votes for the most suspicious player (`bots/Suspicion.kt`). |

`InformedBot` takes four knobs for playing style: `trust` (how much unproven claims count), `noise` (random wobble in choices), `candor` (chance per day that the detective or lookout shares results) and `cunning` (chance that a bluffing mafioso vouches for a teammate). `Temperaments.kt` in the simulator bundles them into four town styles: careless (the `heuristic` bot), suspicious, careful and trusting.

Run `./gradlew :simulator:run` for win rates per player count, `./gradlew :simulator:run --args="setups 1000 informed 42"` for one row per role combination, or `--args="roles 3000 informed 42"` for the win rate of each role. `--args="range 3000"` and `--args="effects 500"` play all four temperaments and print the spread.

Win rates in % (4,000 games per size, random setups, default config, seed 42):

| Players | heuristic: town / mafia / joker | informed: town / mafia / joker |
| --- | --- | --- |
| 6 | 47.5 / 52.6 / - | 88.5 / 11.6 / - |
| 7 | 12.7 / 87.4 / - | 73.7 / 26.3 / - |
| 8 | 20.2 / 77.6 / 2.2 | 78.0 / 20.4 / 1.6 |
| 9 | 22.0 / 75.0 / 3.0 | 86.4 / 11.1 / 2.5 |
| 10 | 17.1 / 78.7 / 4.2 | 80.2 / 16.0 / 3.8 |
| 11 | 18.5 / 76.4 / 5.2 | 86.5 / 9.0 / 4.6 |
| 12 | 11.5 / 78.9 / 9.7 | 82.3 / 7.7 / 10.0 |
| 13 | 12.4 / 77.5 / 10.1 | 84.6 / 4.8 / 10.7 |
| 14 | 13.3 / 76.2 / 10.5 | 85.1 / 3.7 / 11.2 |

Win rate of each role over all sizes (`roles` report, 3,000 games per size, random setups; counted per dealt player):

| Role | heuristic % | informed % |
| --- | --- | --- |
| Citizen | 18.7 | 83.5 |
| Detective | 19.2 | 82.9 |
| Doctor | 18.0 | 83.4 |
| Day shooter | 13.6 | 84.8 |
| Avenger | 22.0 | 88.1 |
| Bodyguard | 13.7 | 84.7 |
| Lookout | 13.4 | 86.7 |
| Mafioso | 77.9 | 12.1 |
| Godfather | 79.6 | 14.3 |
| Blackmailer | 75.3 | 7.3 |
| Joker | 15.8 | 14.7 |

Town win rate by town temperament (`range` report, 3,000 games per size):

| Players | careless | suspicious | careful | trusting |
| --- | --- | --- | --- | --- |
| 6 | 50.8 | 87.2 | 88.5 | 88.1 |
| 7 | 12.5 | 64.0 | 66.1 | 72.8 |
| 8 | 21.0 | 79.2 | 76.0 | 77.9 |
| 9 | 23.0 | 88.3 | 86.1 | 86.5 |
| 10 | 17.3 | 81.2 | 81.5 | 80.1 |
| 11 | 18.6 | 88.1 | 86.3 | 86.6 |
| 12 | 9.7 | 87.4 | 84.4 | 84.2 |
| 13 | 11.7 | 88.6 | 87.5 | 85.7 |
| 14 | 13.8 | 91.2 | 87.6 | 86.1 |

The three informed styles land within a few points of each other, so the knobs do not produce a realistic middle between "does nothing" and "reasons well". The bots do not model human play well enough to pick absolute win rates from them.

Effect of each special role on the town win rate, in points against the average setup of the same player and mafia count (`effects` report, 500 games per setup):

| Role | careless | suspicious | careful | trusting | Spread |
| --- | --- | --- | --- | --- | --- |
| Doctor (6-7 players) | +2.5 | +2.4 | +2.3 | +2.3 | 0.1 |
| Avenger | +6.2 | +3.5 | +4.2 | +4.3 | 2.7 |
| Lookout | -1.0 | +1.2 | +2.1 | +3.0 | 4.0 |
| Blackmailer | +1.8 | +0.7 | +1.1 | +1.1 | 1.1 |
| Bodyguard | -0.9 | +0.5 | +0.6 | +0.4 | 1.4 |
| Day shooter | -1.0 | +1.4 | +0.6 | +0.3 | 2.4 |
| Godfather | -1.9 | -2.1 | -3.3 | -4.5 | 2.6 |
| Joker | -3.1 | -7.0 | -7.8 | -8.4 | 5.4 |

The earlier two-column comparison (`heuristic` vs `informed`) is kept above for reference. Treat absolute win rates as unreliable: the spread between bot styles is huge, and real players will land somewhere unknown in between. What does hold across styles is the ranking of roles (table above), so use the simulator to compare setups and spot broken roles, and use real games to set the actual numbers.

Findings so far:

- The joker used to win 28-34% of games whenever present. With the day-3 rule it wins 8-15%.
- The godfather looked far stronger (-11.5 points) when the mafia only accused people; once mafiosi also vouch for teammates it is -2 to -4.5.
- The blackmailer is close to neutral for town once the mafia bluffs better, but it still replaces a killer with a role that only blocks.
- The joker is the one role whose effect swings a lot between styles (-3 to -8).
- Bodyguard, lookout and day shooter are close to neutral. The lookout's effect depends on bots acting on its sightings, which humans do better.
- 13-14 players with 4 mafia were mafia-leaning; 3 mafia is closer to even. The 10-11 player setups with 2 mafia are strongly town-favoured and with 3 mafia mafia-leaning, hence the per-game draw.
- A kill on the first night shifts the midpoint 2-25 points towards the mafia (worst at 7 players), so it stays off by default.

## Known limits

Bots model claims and votes but not free discussion, and mafia bluff with a single fixed strategy. Use the numbers to compare role setups and rule changes, not as absolute win rates.
