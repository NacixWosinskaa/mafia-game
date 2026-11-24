# Conventions

- Code, identifiers, commit messages and documentation are in English. In-game content (lore, narration) is Polish by default but lives in content files, not in code.
- Keep code comments to a minimum. Comment only the non-obvious why, never what the code does.
- Write docs concretely and briefly, like a technical person explaining to a colleague: what it does, how to use it, no filler.

# Project

Kotlin, Gradle, two modules: `engine` (rules, bots) and `simulator` (bot games, win-rate stats). Run `./gradlew test`.
