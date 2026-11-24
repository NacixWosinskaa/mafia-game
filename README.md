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

- Roles are data (`Roles` in `Model.kt`): a faction and an optional night action.
- Night order lives in `NightResolver`: protect, then kill, then investigate.
- Players and bots only see a `PlayerView`, never the full state. Public information in it: revealed roles of the dead, all `Claim`s and the vote history (`DayVotes`).
- During the day a player can make a `Claim` ("I investigated X, they are / are not mafia") with `submitClaim`. The engine does not check truth, so mafia can lie.
- The engine emits `GameEvent`s describing what happened. The future lore layer will turn those into text.

## Rules and where to change them

| Rule | Default | Where |
| --- | --- | --- |
| Tie in the vote | no execution | `GameConfig.tieRule` |
| First night | no kill | `GameConfig.killOnFirstNight` |
| Doctor protects self | yes | `GameConfig.doctorCanSelfProtect` |
| Doctor repeats a target | no | `GameConfig.doctorCanRepeatTarget` |
| Detective result | mafia / not mafia | `NightResolver` |
| Vote | most votes wins, "nobody" is a valid vote | `Game.resolveDay` |
| Win | town: no mafia left; mafia: at least as many as everyone else | `Game.checkWinner` |
| Role counts per player count (6-12) | see table in code | `RoleSetups` |

To try a rule change, edit the `GameConfig()` line in `simulator/.../Main.kt` and rerun.

## Adding a role

1. Add it to `RoleId` and `Roles`.
2. If it has a new night action, add a `NightActionType`, its target rules in `Game.legalNightTargets` and its place in `NightResolver`.
3. Add it to `RoleSetups` and write tests.

## Bots

| Bot | Behaviour |
| --- | --- |
| `random` | Random legal choices. Lower bound. |
| `heuristic` | Random, but mafia coordinate kills and the detective checks new people. No claims. |
| `informed` | Detective announces results, mafia bluff and hunt accusers, doctor protects accusers, town votes for the most suspicious player (`bots/Suspicion.kt`). |

Town win rate in % (10,000 games per size, default config, seed 42):

| Players | random | heuristic | informed |
| --- | --- | --- | --- |
| 6 | 39.1 | 49.7 | 90.6 |
| 7 | 12.5 | 13.7 | 75.1 |
| 8 | 15.3 | 15.6 | 83.1 |
| 9 | 17.5 | 19.1 | 93.2 |
| 10 | 5.6 | 3.5 | 75.1 |
| 11 | 7.2 | 4.4 | 91.1 |
| 12 | 8.2 | 5.4 | 95.2 |

Real balance sits between the `heuristic` and `informed` columns. `informed` is an upper bound for town: its detective announces every result immediately and town bots never doubt each other. The dips at 7 and 10 players are where `RoleSetups` adds a mafioso.

## Known limits

Bots model claims and votes but not free discussion, and mafia bluff with a single fixed strategy. Use the numbers to compare role setups and rule changes, not as absolute win rates.
