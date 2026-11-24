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
| Joker | neutral | wins alone if executed by the vote; dying any other way is just a loss |

Planned, not implemented: the jailor (picks a player at night; that player can't talk, vote or act the next day and has a private chat with the jailor). It needs chat, so it waits for the backend.

## Setups

A setup is built by `RoleSetups` for 6-14 players: mafia count from a table, always one detective and one doctor, a few special roles, citizens for the rest.

- Special roles are drawn without replacement from a pool, so none repeats in a game. Only citizens and mafiosi repeat.
- Below 12 players at most one neutral role and one "bait" role (joker, avenger); from 12, two of each.
- Slots for special roles by player count: 0 (6-7), 1 (8-9), 2 (10-11), 3 (12-14). The pool has only three roles today, so 12-14 player games are all alike until it grows.
- `RoleSetups.combinations(n)` lists every allowed set. To drop one after looking at the simulator, add it to `rejected`.

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

Run `./gradlew :simulator:run` for win rates per player count, or `./gradlew :simulator:run --args="setups 10000 informed 42"` for one row per role combination.

Win rates in % (10,000 games per size, random setups, default config, seed 42):

| Players | heuristic: town / mafia / joker | informed: town / mafia / joker |
| --- | --- | --- |
| 6 | 49.8 / 50.2 / - | 89.9 / 10.1 / - |
| 7 | 13.4 / 86.6 / - | 75.5 / 24.5 / - |
| 8 | 22.0 / 69.7 / 8.4 | 78.3 / 12.1 / 9.6 |
| 9 | 24.4 / 67.0 / 8.6 | 82.8 / 6.9 / 10.3 |
| 10 | 6.9 / 82.5 / 10.6 | 66.1 / 19.7 / 14.2 |
| 11 | 8.9 / 79.6 / 11.5 | 73.8 / 11.3 / 14.9 |
| 12 | 13.0 / 62.7 / 24.3 | 63.3 / 5.9 / 30.8 |
| 13 | 2.7 / 75.1 / 22.1 | 57.5 / 9.7 / 32.8 |
| 14 | 3.3 / 72.9 / 23.8 | 61.1 / 5.2 / 33.7 |

Per combination with `informed` bots (town / mafia / joker):

| Players | Special roles | Result |
| --- | --- | --- |
| 8 | shooter / avenger / joker | 82.4 / 17.6 / 0, 89.6 / 10.4 / 0, 61.6 / 10.7 / 27.7 |
| 9 | shooter / avenger / joker | 87.4 / 12.6 / 0, 95.2 / 4.8 / 0, 65.2 / 4.5 / 30.3 |
| 10 | shooter + avenger | 82.3 / 17.7 / 0 |
| 10 | shooter + joker | 52.1 / 20.0 / 28.0 |
| 11 | shooter + avenger | 89.2 / 10.8 / 0 |
| 11 | shooter + joker | 57.2 / 12.3 / 30.5 |
| 12-14 | shooter + avenger + joker | 62.1 / 5.4 / 32.4, 57.6 / 10.0 / 32.4, 61.7 / 4.7 / 33.6 |

Real balance sits between the two bot columns: `informed` is an upper bound for town, since its detective announces every result immediately and town bots never doubt each other. Two findings stand out: the joker wins about 28-34% of games whenever present, which is too much, and the avenger shifts roughly 7 points from mafia to town.

## Known limits

Bots model claims and votes but not free discussion, and mafia bluff with a single fixed strategy. Use the numbers to compare role setups and rule changes, not as absolute win rates.
