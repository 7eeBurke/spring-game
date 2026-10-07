# Architecture

## Goal

Build a dark-fantasy roguelike in Java/Spring Boot where natural language gives the player freedom while Java remains authoritative over mechanics.

## Core Runtime Flow

1. Player submits free-form text.
2. AI Action Interpreter converts it to constrained `ActionIntent`.
3. Java validates the intent against current visible/owned/known state.
4. Java resolves each action step deterministically.
5. Java produces `ResolvedOutcome`.
6. State changes are persisted.
7. A narrator receives narration-safe facts and renders prose.

## ActionIntent

Top-level conceptual fields:
- `schemaVersion`
- optional `responseToAttackId`
- `steps[]`
- `interpretationConfidence`
- `unresolvedReferences[]`

Supported V1 action types:
- `ATTACK`
- `DEFEND`
- `MOVE`
- `INTERACT`
- `OBSERVE`
- `USE_ABILITY`
- `USE_ITEM`
- `COMMUNICATE`

Steps are ordered and may relate to the previous step using:
- `START`
- `THEN`
- `IF_PREVIOUS_SUCCEEDS`
- `WHILE`

`WHILE` represents genuinely simultaneous actions and is subject to the simultaneous-action complexity rules (see `GAME_RULES.md`). Exact `WHILE` resolution semantics are deferred to the action-resolution stage (see `DEFERRED_DECISIONS.md`).

The AI may normalize language but may not output rolls, DCs, damage, trauma, success, injuries or new world facts.

## ResolvedOutcome

Top-level conceptual fields:
- `schemaVersion`
- `actionIntentId`
- optional `responseToAttackId`
- `overallResult`
- `stepOutcomes[]`
- `stateChanges[]`
- `resourceChanges[]`
- `newEvents[]`
- `narrationFacts[]`
- resolution/debug metadata

Per-step resolution may include:
- `CheckResult`
- attack/defense/movement-specific result payloads
- generated mechanical effects
- cancellation reason for later dependent steps

Possible overall results:
- `COMPLETE_SUCCESS`
- `PARTIAL_SUCCESS`
- `FAILURE`
- `INTERRUPTED`
- `INVALID`

`INTERRUPTED` means action resolution began, but a resulting state or event prevented the remaining sequence from continuing.

Possible step status values:
- `RESOLVED`
- `CANCELLED`
- `INVALID`

## Narration Boundary

The narrator should receive a reduced `NarrationContext`, not the full internal object graph.
It may dramatize confirmed facts but may not add consequences or interactable objects.

## Package Architecture

Feature-oriented packages under the base package `com.leeburke.springgame`, growing toward:
- `config` — Spring configuration
- `shared` — small cross-feature value types/utilities
- `content` — static authored definitions (weapons, passives, abilities, items) loaded from classpath JSON into an immutable catalogue (see `CONTENT.md`)
- `character` — character generation and character state
- `mechanics` — checks, DCs, suitability, damage, trauma
- `action` — `ActionIntent`, validation, step resolution, `ResolvedOutcome`
- `combat`
- `enemy`
- `world` — region/scene generation, `SceneState`, `PlayerSceneView`
- `run` — run lifecycle and the `GameRun` read model
- `persistence` — JPA entities, entity/domain mapping and the run persistence facade (`GameRunStore`)
- `ai` — AI role adapters and deterministic fallbacks
- `api` — HTTP controllers and request/response DTOs

Packages are created only when a stage needs them.
Domain game logic stays independent of Spring wherever practical (plain Java classes, constructor-injected, unit-testable without a Spring context); Spring wiring lives at the edges (`config`, `api`, `persistence`).

Dependency direction: `persistence` depends on `run`, `character`, `content` and `mechanics`, never the reverse. Domain and content types carry no JPA or Spring annotations; JPA entities stay inside `persistence` and are never returned to callers.

## Technology Stack

- Java 21 LTS
- Spring Boot 4.1.x (Spring Web, Spring Data JPA, Validation, OpenAPI)
- Maven, with the Maven Wrapper committed to the repository
- PostgreSQL
- Flyway for schema migrations
- Hibernate schema validation (`ddl-auto=validate`), not automatic schema creation
- Testcontainers PostgreSQL for database integration tests
- Jackson 3 (`tools.jackson`, version managed by the Spring Boot parent) for static content JSON
- React + TypeScript + Vite for the frontend (later stage)
- Lombok is optional

The AI provider is deliberately unspecified until the AI integration stage. Do not add an AI provider SDK before then.

## Persistence Strategy

- Important lifecycle/domain relationships (run, character, region instance, scene instance, etc.) remain relational.
- `SceneInstance` has relational identity/metadata plus a `revision`.
- `SceneInstance` always belongs to a run. A region scene also belongs to a `RegionInstance`; the hub scene (`THE_LAST_LANTERN`) has no region association. Each scene instance is identified as a hub or region scene.
- A generated region is built and validated fully in memory, then persisted in a single transaction; invalid regions are never persisted.
- Dynamic nested `SceneState` content may be persisted as PostgreSQL JSONB.
- Static game definitions (weapons, items, passives, abilities, enemies, scene archetypes, events) do not automatically become database tables. Weapons, passives, abilities and items are authored in `src/main/resources/content/*.json` (see `CONTENT.md`); run state refers to them by definition code.

### Schema Ownership

- Flyway owns the schema. Migrations live in `src/main/resources/db/migration/`.
- Hibernate only validates the migrated schema (`spring.jpa.hibernate.ddl-auto=validate`); it never creates or updates tables.
- Open Session in View is disabled (`spring.jpa.open-in-view=false`); loading and mapping happen inside service transactions.
- Simple stable structural rules are database constraints: keys, foreign keys, one character per run, NOT NULL, stat range 3–10, Fated 0–5, `max_hp >= 1`, `0 <= current_hp <= max_hp`, tool-belt slot 0–4, and the stable enum-name sets. Enums are stored by name, never by ordinal.
- Generation policy (stat total and profiles, Fated distribution, HP formula, starting belt composition) is not duplicated in SQL.

### Current Tables (V1)

- `game_run` — internal UUID identity and the run seed.
- `player_character` — keyed by `run_id` (the run's ID, so one character per run): name, five stats, Fated, max/current HP, passive and ability definition codes.
- `player_body_part` — one row per body part and its severity.
- `player_tool_belt_entry` — one row per occupied belt slot: slot index (list position), entry kind (`WEAPON`/`ITEM`) and definition code. No runtime instance IDs.

### Static Content References

- Saving stores definition codes only, and only for definitions identical to the current catalogue's definition for that code.
- Loading resolves codes through the current `GameContentCatalog`. An unknown code, a run without its player character, or any stored value that fails domain validation raises a clear error; a partial state is never returned.
- Loading across changed static-content versions is not guaranteed (see `CONTENT.md` and `DEFERRED_DECISIONS.md`).

Player and run state is relational. JSONB is reserved for the later dynamic `SceneState`, not used for simple fixed state.
