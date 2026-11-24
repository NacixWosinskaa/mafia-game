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
| Joker | neutral | wins alone if executed by the vote from day 3 on; executed earlier or dying any other way is just a loss |

Planned, not implemented: the jailor (picks a player at night; that player can't talk, vote or act the next day and has a private chat with the jailor). It needs chat, so it waits for the backend.

## Setups

A setup is built by `RoleSetups` for 6-14 players: mafia count from a table, always one detective, a doctor (guaranteed from 8 players, optional below), a few special roles, citizens for the rest.

- Special roles are drawn without replacement from a pool, so none repeats in a game. Only citizens and mafiosi repeat.
- Below 12 players at most one neutral role and one "bait" role (joker, avenger); from 12, two of each.
- Mafia by player count: 1 (6), 2 (7-9), 2 or 3 drawn per game (10-11), 3 (12-14).
- Slots for special roles by player count: 0-1 (6-7, where the doctor is one of them), 1 (8-9), 2 (10-11), 3 (12-14). The pool has only three roles today, so 12-14 player games are all alike until it grows.
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

Run `./gradlew :simulator:run` for win rates per player count, or `./gradlew :simulator:run --args="setups 10000 informed 42"` for one row per role combination.

Win rates in % (4,000 games per size, random setups, default config, seed 42):

| Players | heuristic: town / mafia / joker | informed: town / mafia / joker |
| --- | --- | --- |
| 6 | 47.5 / 52.6 / - | 88.5 / 11.6 / - |
| 7 | 12.7 / 87.4 / - | 73.7 / 26.3 / - |
| 8 | 22.0 / 74.9 / 3.2 | 80.9 / 16.7 / 2.5 |
| 9 | 24.9 / 70.8 / 4.3 | 88.2 / 8.8 / 3.0 |
| 10 | 18.2 / 76.1 / 5.7 | 81.5 / 14.6 / 3.9 |
| 11 | 21.3 / 71.9 / 6.9 | 84.5 / 9.8 / 5.7 |
| 12 | 13.5 / 70.8 / 15.7 | 80.5 / 7.5 / 12.0 |
| 13 | 15.3 / 68.6 / 16.1 | 82.2 / 4.5 / 13.3 |
| 14 | 17.4 / 64.3 / 18.4 | 82.0 / 2.6 / 15.5 |

Per combination with `informed` bots (3,000 games each, town / mafia / joker):

| Players | Setup | Result |
| --- | --- | --- |
| 6 | 1 mafia, no doctor / with doctor | 88.7 / 11.3 / 0, 90.7 / 9.3 / 0 |
| 7 | 2 mafia, no doctor / with doctor | 68.2 / 31.8 / 0, 74.9 / 25.1 / 0 |
| 8 | shooter / avenger / joker | 82.5 / 17.5 / 0, 88.8 / 11.2 / 0, 74.1 / 18.2 / 7.7 |
| 9 | shooter / avenger / joker | 87.1 / 12.9 / 0, 94.9 / 5.1 / 0, 80.5 / 8.7 / 10.8 |
| 10 | 2 mafia, shooter + avenger | 94.6 / 5.4 / 0 |
| 10 | 3 mafia, shooter + avenger | 82.0 / 18.0 / 0 |
| 10 | 2 mafia, shooter + joker | 82.0 / 9.6 / 8.4 |
| 10 | 3 mafia, shooter + joker | 63.5 / 28.0 / 8.5 |
| 11 | 2 mafia, shooter + avenger | 96.0 / 4.0 / 0 |
| 11 | 3 mafia, shooter + avenger | 89.7 / 10.3 / 0 |
| 11 | 2 mafia, shooter + joker | 82.7 / 7.0 / 10.3 |
| 11 | 3 mafia, shooter + joker | 73.6 / 15.6 / 10.8 |
| 12-14 | 3 mafia, shooter + avenger + joker | 80.0 / 6.2 / 13.8, 80.6 / 5.4 / 14.0, 81.9 / 3.1 / 15.1 |

Real balance sits between the two bot columns: `informed` is an upper bound for town, since its detective announces every result immediately and town bots never doubt each other. Taking the midpoint of the two columns, town wins 43-57% of games at 7-14 players; 6 players is town-favoured (about 68%) because a single mafioso is easy to hunt.

Findings so far:

- The joker used to win 28-34% of games whenever present. With the day-3 rule it wins 8-15%.
- The avenger shifts roughly 6-7 points from mafia to town.
- 13-14 players with 4 mafia were mafia-leaning; 3 mafia is closer to even. The 10-11 player setups with 2 mafia are strongly town-favoured and with 3 mafia mafia-leaning, hence the per-game draw.
- A kill on the first night shifts the midpoint 2-25 points towards the mafia (worst at 7 players), so it stays off by default.

## Known limits

Bots model claims and votes but not free discussion, and mafia bluff with a single fixed strategy. Use the numbers to compare role setups and rule changes, not as absolute win rates.
