# Project: Dark-Fantasy Natural-Language Roguelike

A Java + Spring Boot roguelike RPG where the player interacts using unrestricted natural-language actions. The game is designed as a portfolio-quality software engineering project and as a way to learn Spring properly.

## Mandatory Gameplay Architecture

The core pipeline is:

Player natural language
→ AI Action Interpreter
→ `ActionIntent`
→ deterministic Java rules engine
→ `ResolvedOutcome`
→ AI Narrator

This boundary is non-negotiable.

## AI Authority

The LLM MAY:
- interpret player language into canonical structured intent;
- resolve ordinary linguistic references using player-visible context;
- narrate confirmed backend outcomes;
- describe enemy attacks from backend-generated attack properties;
- generate constrained, soft character-background flavour.

The LLM MUST NOT determine:
- success or failure;
- dice rolls, DCs, modifiers, damage, trauma, wounds or conditions;
- inventory state, item ownership or ability charges;
- enemy decisions or combat strategy;
- procedural world generation;
- hidden world state, secret exits, traps, loot or weaknesses;
- permanent gameplay facts that were not supplied by the backend.

Java owns game truth.

## Development Rules

- Read the relevant files in `/docs` before implementing a subsystem.
- Do not invent permanent mechanics that are absent from the design.
- Prefer deterministic, testable Java logic over AI judgement.
- Keep the implementation inside the MVP defined in `docs/MVP_SCOPE.md` unless explicitly asked to expand it.
- Keep static content data-driven where practical: weapons, items, passives, abilities, enemies, scene archetypes and events.
- Do not create one Java service/class per item or weapon unless a genuinely new mechanic requires it.
- Generated run state must be persisted and must not reroll on refresh or revisit.
- Use seeded procedural generation wherever randomness matters so bugs can be reproduced.
- Add automated tests for deterministic game logic.
- Never make tests depend on a live LLM call.
- Run relevant tests/builds after changes.
- Explain significant Spring design choices so the project remains educational.

## Working Style

For substantial work:
1. Read the relevant docs.
2. Inspect the current codebase.
3. Produce a bounded implementation plan.
4. Identify any contradiction between code and docs.
5. Implement only the requested stage.
6. Add/update tests.
7. Run the relevant tests/build.
8. Review the implementation against this file and the relevant docs.

Do not broaden scope just because an additional feature would be convenient.
