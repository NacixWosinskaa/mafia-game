# Mafia – faza 1: silnik zasad + symulator z botami

Szkic projektu w Kotlinie (Gradle, dwa moduły):

- `engine` – czysta biblioteka z zasadami gry, bez sieci i bez interfejsu
- `simulator` – program konsolowy, który rozgrywa tysiące gier z botami i liczy statystyki wygranych

## Uruchomienie

Otwórz folder `mafia` w IntelliJ jako projekt Gradle (File → Open → wybierz folder) albo użyj terminala:

```
./gradlew test                                        # testy silnika i symulatora
./gradlew :simulator:run                              # symulacja: 10000 gier na rozmiar, boty heurystyczne, seed 42
./gradlew :simulator:run --args="20000 random 7"      # <liczba gier> <random|heuristic> <seed>
```

Na Windowsie zamiast `./gradlew` użyj `gradlew.bat`.

## Struktura

```
engine/src/main/kotlin/pl/mafia/engine/
  Model.kt          role jako dane (RoleDefinition, Roles), Player, Faction, Phase
  Config.kt         GameConfig – wszystkie reguły, które mogą się zmienić
  RoleSetups.kt     tabela składów ról dla 6–12 graczy
  NightResolver.kt  rozstrzyganie nocy: ochrona → zabójstwo → śledztwa
  Game.kt           maszyna stanów gry + PlayerView (wiedza jednego gracza)
  Events.kt         zdarzenia gry (hak pod przyszłą warstwę lore)
  bots/Bots.kt      interfejs Bot, RandomBot, HeuristicBot
simulator/src/main/kotlin/pl/mafia/simulator/
  Simulation.kt     rozgrywanie gier i statystyki
  Main.kt           punkt wejścia
```

## Domyślne zasady (zmieniasz je w `GameConfig`)

| Zasada | Domyślnie | Gdzie zmienić |
| --- | --- | --- |
| Remis w głosowaniu | brak egzekucji | `tieRule = TieRule.RANDOM_AMONG_TIED` |
| Pierwsza noc | bez zabójstwa | `killOnFirstNight = true` |
| Lekarz chroni siebie | tak | `doctorCanSelfProtect` |
| Lekarz ta sama osoba dwa razy z rzędu | nie | `doctorCanRepeatTarget` |
| Detektyw | wynik „mafia / nie mafia" | `NightResolver` |
| Głosowanie | wygrywa najwięcej głosów, można głosować „na nikogo" | `Game.resolveDay` |
| Wygrana | miasto: brak mafii; mafia: co najmniej tyle osób co reszta | `Game.checkWinner` |

Remis i pierwsza noc to reguły oznaczone jako „do potwierdzenia": zmiana to jedna linijka w `GameConfig`
(np. w `Main.kt`: `GameConfig(killOnFirstNight = true)`), a symulator od razu pokaże efekt.

## Jak dodać nową rolę

1. Dodaj wartość do `RoleId` i wpis w `Roles` (frakcja + typ akcji nocnej).
2. Jeśli rola ma nową akcję, dodaj `NightActionType`, zasady celów w `Game.legalNightTargets`
   i miejsce w kolejności w `NightResolver`.
3. Dodaj ją do `RoleSetups` i napisz test.

## Co silnik NIE robi (celowo, na później)

- nie zna czasu rzeczywistego ani timerów (tury są wywoływane z zewnątrz)
- nie ma sieci, kont ani interfejsu
- nie generuje lore – tylko emituje zdarzenia (`GameEvent`), które narrator zamieni w tekst
- boty nie rozmawiają, więc wyniki balansu dotyczą gry bez dyskusji
