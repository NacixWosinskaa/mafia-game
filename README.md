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
| Day shooter | town | one shot per game during the day; the target dies at once, can't be protected, role is revealed |
| Joker | neutral | wins alone if executed by the vote; dying any other way is just a loss |

The joker appears from 8 players, the day shooter from 10 (`RoleSetups`).

## Rules and where to change them

| Rule | Default | Where |
| --- | --- | --- |
| Tie in the vote | no execution | `GameConfig.tieRule` |
| First night | no kill | `GameConfig.killOnFirstNight` |
| Doctor protects self | yes | `GameConfig.doctorCanSelfProtect` |
| Doctor repeats a target | no | `GameConfig.doctorCanRepeatTarget` |
| Detective result | mafia / not mafia | `NightResolver` |
| Vote | most votes wins, "nobody" is a valid vote | `Game.resolveDay` |
| Win | town: no mafia left; mafia: at least as many as everyone else (the joker counts as "else"); joker: executed by vote | `Game.checkWinner`, `Game.resolveDay` |
| Role counts per player count (6-12) | see table in code | `RoleSetups` |

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

Win rates in % (10,000 games per size, default config, seed 42):

| Players | heuristic: town / mafia / joker | informed: town / mafia / joker |
| --- | --- | --- |
| 6 | 49.7 / 50.3 / - | 90.6 / 9.5 / - |
| 7 | 13.7 / 86.3 / - | 75.1 / 24.9 / - |
| 8 | 14.7 / 60.8 / 24.5 | 62.0 / 10.1 / 27.9 |
| 9 | 16.0 / 59.3 / 24.7 | 65.7 / 4.3 / 30.1 |
| 10 | 3.2 / 76.1 / 20.7 | 55.5 / 15.5 / 29.0 |
| 11 | 4.1 / 73.3 / 22.6 | 57.6 / 11.1 / 31.3 |
| 12 | 4.8 / 70.5 / 24.7 | 61.0 / 5.7 / 33.3 |

Real balance sits between the two columns: `informed` is an upper bound for town, since its detective announces every result immediately and town bots never doubt each other. The joker wins 20-33% of games with either bot, which is too much; tune it before trusting the other columns.

## Known limits

Bots model claims and votes but not free discussion, and mafia bluff with a single fixed strategy. Use the numbers to compare role setups and rule changes, not as absolute win rates.
