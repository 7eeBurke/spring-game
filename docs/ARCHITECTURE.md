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

The exact contract is specified in `AI_CONTRACTS.md` (schema version 1).

Top-level fields:
- `schemaVersion`
- optional `responseToAttack` (the only place an incoming attack is referenced)
- `steps[]` (1-based sequence, in list order)
- `confidence`
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

### Action Boundary

```
player text → (future) Action Interpreter → ActionIntent
            → ActionValidator (PlayerSceneView + owned references + known incoming attacks)
            → (Stage 11) action resolution → ResolvedOutcome → persistence → narration
```

- `action` holds the contract: `ActionIntent`, steps, the sealed payloads and targets, and the vocabulary enums. The action type is derived from the payload.
- `action.validation` holds `ActionValidationContext`, `PlayerActionReferences`, `ActionValidator`, the result model and the `PhysicalPlausibilityPolicy` seam.
- Validation consults only the player-safe view and the player's current opaque references: never `SceneState`, hidden content, scene or exit-destination identities, or persistence. Unknown and hidden scene references are indistinguishable, so validation errors cannot leak hidden state.
- Owned weapons, abilities and items are referenced by opaque per-context references, not definition codes or persistent instance IDs.
- Validation never chooses stats, DCs or suitability, never rolls, and never mutates the intent. Both packages are pure Java.

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
- `shared` — small cross-feature utilities (`DefinitionCodes` code format, `StrictJson` mapper factory); no Spring or JPA
- `content` — static authored definitions (weapons, passives, abilities, items) loaded from classpath JSON into an immutable catalogue (see `CONTENT.md`)
  - `content.world` — authored world-generation content (world elements, scene archetypes, regions, fixed scenes) and its catalogue/loader
- `character` — character generation and character state
- `mechanics` — checks, DCs, suitability, damage, trauma
- `action` — the `ActionIntent` contract (steps, payloads, targets, vocabulary); step resolution and `ResolvedOutcome` later
  - `action.validation` — deterministic validation of an intent against the player-safe view and owned references
- `combat`
- `enemy`
- `world` — region and scene instances, `SceneState` and its integrity rules, `PlayerLocation` (pure Java; region generation will live alongside later)
  - `world.view` — `PlayerSceneView` and its projector: the player-safe boundary
  - `world.generation` — deterministic world generation: `RunWorldGenerator`, topology, archetype selection, scene contents, `CompleteRegionValidator`, and the in-memory aggregates `GeneratedRegion` and `GeneratedRunWorld` (pure Java, no Spring)
- `run` — run lifecycle and the `GameRun` read model
- `persistence` — JPA entities, entity/domain mapping, the scene-state JSON codec and the persistence facades (`GameRunStore`, `WorldStore`)
- `ai` — AI role adapters and deterministic fallbacks
- `api` — HTTP controllers and request/response DTOs

Packages are created only when a stage needs them.
Domain game logic stays independent of Spring wherever practical (plain Java classes, constructor-injected, unit-testable without a Spring context); Spring wiring lives at the edges (`config`, `api`, `persistence`).

Dependency direction: `persistence` depends on `world`, `run`, `character`, `content` and `mechanics`, never the reverse. Domain and content types carry no JPA, Spring or Jackson annotations; JPA entities stay inside `persistence` and are never returned to callers.

### Player-Safe Scene View

Action interpretation and AI roles receive `PlayerSceneView`, never authoritative `SceneState`. The view uses its own records (never the authoritative ones), so a field added to authoritative state cannot leak automatically. It never contains hidden content, content inside hidden or non-visible zones, exit destinations or any UUID, seeds, revisions, environment flags or active events. The caller decides which zones are visible; the projector owns the safe filtering (see `WORLD_GENERATION.md`).

## Technology Stack

- Java 21 LTS
- Spring Boot 4.1.x (Spring Web, Spring Data JPA, Validation, OpenAPI)
- Maven, with the Maven Wrapper committed to the repository
- PostgreSQL
- Flyway for schema migrations
- Hibernate schema validation (`ddl-auto=validate`), not automatic schema creation
- Testcontainers PostgreSQL for database integration tests
- Jackson 3 (`tools.jackson`, version managed by the Spring Boot parent) for static content JSON and persisted scene-state documents
- React + TypeScript + Vite for the frontend (later stage)
- Lombok is optional

The AI provider is deliberately unspecified until the AI integration stage. Do not add an AI provider SDK before then.

## Persistence Strategy

- Important lifecycle/domain relationships (run, character, region instance, scene instance, etc.) remain relational.
- `SceneInstance` always belongs to a run. A `REGION` scene also belongs to a `RegionInstance` and has a scene seed; a `HUB` scene (`THE_LAST_LANTERN`) has neither.
- `SceneInstance` metadata (identity, run, kind, region, definition code, seed, discovered flag, revision) is relational. Only the dynamic nested `SceneState` is stored as PostgreSQL JSONB, and metadata is never duplicated inside it.
- A generated region is built and validated fully in memory, then persisted in a single transaction; invalid regions are never persisted.

### Scene-State Documents

- `scene_instance.state` is JSONB written and read only by `SceneStateCodec`, through explicit document records (not by serializing domain records). Hibernate binds the text as JSON without interpreting it.
- `scene_instance.state_schema_version` identifies the document structure (currently 1). It is not content or rules versioning. An unsupported version fails clearly; migration between document versions is deferred.
- Decoding is strict: malformed JSON, unknown, missing or null fields, invalid enum values and documents violating `SceneState` invariants all fail as persisted-state corruption naming the scene. A corrupt document never becomes a default scene.

### Scene Revisions

- `scene_instance.revision` is the JPA `@Version` column, used only for mutable scene state. A new scene starts at 0; every state update increments it.
- `WorldStore.updateSceneState(sceneId, expectedRevision, newState)` writes only if the scene is still at `expectedRevision`. A stale revision, or a concurrent update that commits first, raises `StaleSceneStateException` and writes nothing. Two resolutions based on the same revision can never both commit.

### World Initialization

- A run's world is generated completely in memory (`world.generation`), producing a `GeneratedRunWorld`: the generation-context snapshot used, the hub, the generated region and the initial player location.
- Runtime UUIDs come from an injected `IdSource`, kept separate from procedural randomness; they are persistence identity, not part of the reproducibility contract.
- `WorldStore.initializeWorld(world)` persists it in one transaction. Before any write it re-runs the production `CompleteRegionValidator`, and it fails with `WorldAlreadyInitializedException` if the run already has a world. Inserts are flushed in foreign-key order: generation context and region; then the hub and every region scene; then the initial location. Any failure rolls everything back, so no partial world can remain.
- There is no replace, reset or reroll operation.
- `run_generation_context` stores the snapshot as a JSONB document read and written only by `GenerationContextCodec`, with a relational `schema_version` (currently 1); an unsupported version or corrupt document fails clearly.

### Same-Run Integrity

Composite foreign keys on `(id, run_id)` ensure a scene can only reference a region of its own run, and the run's location can only reference a scene of its own run. The location's zone is validated by the application (zones live inside the JSONB state): it must exist and must not be hidden. Exit destinations inside scene state have no database foreign key; complete-region validation checks them before persistence.
- Static game definitions (weapons, items, passives, abilities, enemies, scene archetypes, events) do not automatically become database tables. Weapons, passives, abilities and items are authored in `src/main/resources/content/*.json`, and world-generation content in `src/main/resources/content/world/*.json` (see `CONTENT.md`); run state refers to them by definition code.

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

### Current Tables (V2)

- `region_instance` — region identity, run and definition code.
- `scene_instance` — scene metadata, `revision`, `state_schema_version` and the JSONB `state`; a check constraint enforces the hub/region shape.
- `run_world_state` — keyed by `run_id`: the current scene and zone. A run without a world yet has no row.

### Current Tables (V3)

- `run_generation_context` — keyed by `run_id`: the immutable generation-context snapshot (JSONB) and its `schema_version`. One row per run; its presence marks the run's world as initialized.

### Static Content References

- Saving stores definition codes only, and only for definitions identical to the current catalogue's definition for that code.
- Loading resolves codes through the current `GameContentCatalog`. An unknown code, a run without its player character, or any stored value that fails domain validation raises a clear error; a partial state is never returned.
- Loading across changed static-content versions is not guaranteed (see `CONTENT.md` and `DEFERRED_DECISIONS.md`).

Player and run state is relational. JSONB is used only for the dynamic `SceneState`, not for simple fixed state.
