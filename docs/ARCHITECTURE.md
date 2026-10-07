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

`WHILE` represents genuinely simultaneous actions and is subject to the simultaneous-action complexity rules (see `GAME_RULES.md`). `WHILE` resolution is deferred; such a step is currently mechanics-unavailable. `THEN` and `IF_PREVIOUS_SUCCEEDS` semantics are in `GAME_RULES.md` "Multi-step intents".

The AI may normalize language but may not output rolls, DCs, damage, trauma, success, injuries or new world facts.

### Action Boundary

```
player text → (future) Action Interpreter → ActionIntent
            → ActionValidator (PlayerSceneView + owned references + known incoming attacks)
            → ValidatedActionIntent
            → ActionEngine (ActionResolutionContext + caller's RandomGenerator) → ResolvedOutcome
            → (later) effect application → persistence → narration
```

- `action` holds the contract: `ActionIntent`, steps, the sealed payloads and targets, and the vocabulary enums. The action type is derived from the payload.
- `action.validation` holds `ActionValidationContext`, `PlayerActionReferences`, `ActionValidator`, the result model and the `PhysicalPlausibilityPolicy` seam.
- Validation consults only the player-safe view and the player's current opaque references: never `SceneState`, hidden content, scene or exit-destination identities, or persistence. Unknown and hidden scene references are indistinguishable, so validation errors cannot leak hidden state.
- Owned weapons, abilities and items are referenced by opaque per-context references, not definition codes or persistent instance IDs.
- Validation never chooses stats, DCs or suitability, never rolls, and never mutates the intent. Both packages are pure Java.

### Resolution Boundary

- `ActionValidator.validated(intent, context)` returns a `ValidatedActionIntent` only when validation finds no errors. Its constructor is package-private to `action.validation`, so an unvalidated intent cannot reach the engine. `validate(...)` is unchanged.
- `action.resolution` holds `ActionEngine`, its package-private per-action resolvers, the backend-only `ActionResolutionContext` and the outcome model. It is pure Java: no Spring, persistence, JPA or AI, and no randomness except the `RandomGenerator` the caller passes in.
- `ActionResolutionContext` is authoritative and backend-only, and is never given to the interpreter or narrator. It holds `PlayerCharacterState`, `SceneState`, `PlayerLocation`, the same `PlayerActionReferences` used for validation, combat profiles keyed by `TargetProfileKey(entityId, Optional<BodyPart>)`, and backend-built `IncomingAttack`s keyed by their opaque reference. The `enemy` package produces enemy profiles and incoming attacks (see Enemies); resolution only consumes them.
- Before drawing any randomness, the engine checks that the two contexts agree:
  - the same player references and incoming-attack references;
  - the validated view's current zone equals `PlayerLocation`;
  - every scene reference the intent actually uses exists in `SceneState` with the same kind and local ID, is not hidden, and has the same player-safe fields as the view.

  A mismatch is an orchestration error (`IllegalArgumentException`), never a gameplay result. No backend identity is added to `ActionIntent`.
- The engine mutates nothing. It reports typed effects; applying them to state and persisting them comes later.
- The engine reuses `CheckResolver`, `DamageCalculator` and `TraumaCalculator` and never repeats their arithmetic. Contact mappings live in `mechanics.ContactRules`, and the per-action stats and baselines in `ResolutionRules`.

## ResolvedOutcome

Confirmed backend truth, produced only by Java (see `GAME_RULES.md` "Action Resolution" for the rules).

`ResolvedOutcome` (schema version 1):
- `schemaVersion`
- optional `responseToAttack`: the opaque incoming-attack reference the intent responded to
- `overall`: an `OverallResult`
- `steps`: one `StepOutcome` per intent step, in order
- `metadata`: `ResolutionMetadata(rollsConsumed, rulesVersion)`, with no timestamps or IDs
- `effects()`: every step's effects, flattened in step order

`StepOutcome`:
- `stepId` and `actionType`
- `status`: `RESOLVED`, `CANCELLED` or `MECHANICS_UNAVAILABLE`
- for `RESOLVED`: a `StepSuccess` (`SUCCESS`, `PARTIAL` or `FAILURE`), a `CheckResult` only for rolled steps (an automatic step never fakes a d20), a typed `StepResult` and its effects
- for `CANCELLED`: only a `CancellationReason` (`PREVIOUS_STEP_NOT_SUCCESSFUL`, `PLAYER_DOWN`)
- for `MECHANICS_UNAVAILABLE`: only an `UnavailableReason` (`ACTION_NOT_IMPLEMENTED`, `SIMULTANEOUS_ACTION`, `MISSING_TARGET_PROFILE`, `NO_INCOMING_ATTACK`, `INCOMING_ATTACK_ALREADY_RESOLVED`, `UNDEFINED_INJURY_MODIFIER`)

The sealed `StepResult` types are:

| Result | Contents |
|---|---|
| `AttackResult` | weapon reference and code, target entity, optional named body part, contact, the Stage 4 `DamageResult` and `TraumaResult` |
| `DefenseResult` | incoming-attack reference, defense method, incoming contact, `DamageResult`, `TraumaResult` |
| `MovementResult` | from-zone, to-zone, whether the player moved |
| `CommunicationResult` | communication kind and optional addressee. The spoken content is not carried. |

The sealed `OutcomeEffect` types are:
- `TargetDamaged(entityId, hpDamage, bodyPart, impactSeverity)`, produced on any contact, even when the HP damage is 0;
- `PlayerDamaged(hpDamage, bodyPart, impactSeverity)`;
- `PlayerMoved(fromZone, toZone)`.

Possible overall results:
- `COMPLETE_SUCCESS`
- `PARTIAL_SUCCESS`
- `FAILURE`
- `INTERRUPTED`: resolution began, but the player went down and the remaining steps were cancelled
- `MECHANICS_UNAVAILABLE`: a valid intent, but no step had designed mechanics

There is no `INVALID` result: invalid intents never reach resolution. These are not part of the outcome yet:
- prose;
- narration facts, which will be derived from the typed step outcomes;
- resource changes and new events, since nothing produces them yet.

## Enemies

### Packages and Types

- `content.enemy`: static enemy content (`AnatomyDefinition`, `EnemyDefinition`, `EnemyAttackOption`, `EnemyStatRule`, `EnemyTrait`, `EnemyCatalog`, `EnemyContentLoader`).
- `enemy`: run state and the builders that feed Stage 11.
  - `EnemyInstance` and `EnemyBody` hold the state.
  - `EnemyGenerator`, `EnemyRosterGenerator`, `GeneratedEnemyRoster`, `SceneEnemy` and `EnemyRosterValidator` handle generation.
  - `EnemyCombatant` is the checked combination of instance, definition, anatomy and weapon.
  - `EnemyTargetProfiles` and `EnemyAttacks` build the Stage 11 inputs, and `EnemyRules` holds the formulas and baselines.
- `enemy.behavior`: `EnemyDecisionContext`, `EnemyBehavior`, `EnemyDecision` (`Attack` or `Hold`), `WeightedCandidate` and `EnemyBehaviorRules`.
- `run.initialization`: `RunInitialization` (the world plus its enemy roster) and `RunInitializer`, which runs world generation and then enemy generation.

All four packages are pure Java: no Spring, JPA or AI, and no randomness except the generators passed in. `EnemyCatalog` is exposed as a Spring bean in `config`.

### Identity and State

- An enemy's runtime identity is `(sceneId, entityId)`: its scene plus the scene-local ID of its `SceneEntity`. There is no separate enemy UUID.
- Placement (zone, hidden or not) stays in `SceneState`. `EnemyInstance` holds only the mechanical state: definition code, stats, max and current HP, body (each part of its anatomy with a severity) and weapon code.
- Traits, behaviour weights, attack options and anatomy are static definition data and are not copied into the instance.
- An instance can represent reduced HP and injured parts. Applying `ResolvedOutcome` effects to enemies is deferred.

### Coherence

Builders and behaviour take an `EnemyCombatant`. Its constructor rejects, as a programming error and before any random draw or Stage 11 call:
- a definition that is not the instance's;
- an anatomy that is not the definition's;
- a body that does not have exactly the anatomy's parts;
- a weapon that is not the instance's.

`EnemyAttacks` also rejects an attack option that does not belong to the enemy.

### Feeding Stage 11

- `EnemyTargetProfiles` builds `TargetCombatProfile`s keyed by `TargetProfileKey`: the whole enemy plus every present, non-destroyed part. Stage 11 only consumes them, and it is unchanged.
- `EnemyAttacks` builds Stage 11's `IncomingAttack` from an attack option, the enemy's stats and its weapon definition. It rolls nothing and computes no damage.
- The flow is: `EnemyDecisionContext` → `EnemyBehavior.decide` → `EnemyDecision.Attack` carrying an `IncomingAttack` → Stage 11 resolves the player's `DEFEND` → `ResolvedOutcome`.

### Decision Boundary

`EnemyDecisionContext(self, recentChoices, attackRef)` is to enemies what `PlayerSceneView` is to the player. It holds the enemy's own checked state and its own recent choices (copied and validated: `HOLD` or one of its options), and nothing about the player. No player stats, passive, ability, inventory or HP are observable yet, so none are given. A reflection test checks that no player or validation type is reachable from it.

### Not Yet Built

There is no turn loop: ordering enemies, prompting the player and applying effects come later. The caller supplies recent choices, because action history is not persisted.

## Narration Boundary

The narrator should receive a reduced `NarrationContext`, not the full internal object graph.
It may dramatize confirmed facts but may not add consequences or interactable objects.

## Package Architecture

Feature-oriented packages under the base package `com.leeburke.springgame`, growing toward:
- `config` — Spring configuration
- `shared` — small cross-feature utilities (`DefinitionCodes` code format, `StrictJson` mapper factory); no Spring or JPA
- `content` — static authored definitions (weapons, passives, abilities, items) loaded from classpath JSON into an immutable catalogue (see `CONTENT.md`)
  - `content.enemy` — enemy anatomies and definitions, cross-checked against weapons and world entities
  - `content.world` — authored world-generation content (world elements, scene archetypes, regions, fixed scenes) and its catalogue/loader
- `character` — character generation and character state
- `mechanics` — checks, DCs, suitability, damage, trauma
- `action` — the `ActionIntent` contract (steps, payloads, targets, vocabulary)
  - `action.validation` — deterministic validation of an intent against the player-safe view and owned references, and `ValidatedActionIntent`
  - `action.resolution` — the pure deterministic `ActionEngine` turning a validated intent into a `ResolvedOutcome` (no Spring, persistence or AI)
- `enemy` — enemy run state, generation, Stage 11 profile and attack builders
  - `enemy.behavior` — weighted-utility enemy decisions behind `EnemyDecisionContext`
- `world` — region and scene instances, `SceneState` and its integrity rules, `PlayerLocation` (pure Java; region generation will live alongside later)
  - `world.view` — `PlayerSceneView` and its projector: the player-safe boundary
  - `world.generation` — deterministic world generation: `RunWorldGenerator`, topology, archetype selection, scene contents, `CompleteRegionValidator`, and the in-memory aggregates `GeneratedRegion` and `GeneratedRunWorld` (pure Java, no Spring)
- `run` — run lifecycle and the `GameRun` read model
  - `run.initialization` — the in-memory starting state of a new run (world plus enemies) and its generator
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
- `RunInitializer` then generates the starting state of every enemy placed in that world (`EnemyRosterGenerator`), producing a `RunInitialization`. Enemy state is never generated later, on scene entry.
- `WorldStore.initializeWorld(initialization)` persists it in one transaction. Before any write it re-runs the production `CompleteRegionValidator` and `EnemyRosterValidator`, and it fails with `WorldAlreadyInitializedException` if the run already has a world.
- Inserts are flushed in foreign-key order: the generation context and region; the hub and every region scene; every enemy and its body parts; then the initial location. Any failure rolls everything back, so no partial world or roster can remain.
- `WorldStore` stores; it never generates. `EnemyStore` reads enemies back exactly as stored. The scene seed is provenance only: loading never regenerates enemies.
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

### Current Tables (V4)

- `enemy_instance` — keyed by `(scene_id, entity_local_id)`, with a foreign key to `scene_instance`. It holds the definition code, the five stats (3–10), `max_hp` (at least 1), `current_hp` (0 to max) and the weapon code. That the entity exists in the scene's JSONB state is checked by the application.
- `enemy_body_part` — one row per body part of an enemy (part and severity by enum name), keyed by `(scene_id, entity_local_id, body_part)`.
- There are no tables for enemy definitions, anatomies, traits, behaviour or attack options, which are static content, and none for action history. Loading checks rows against `EnemyCatalog`; an unknown definition or weapon, or a body that does not match its anatomy, raises `PersistedStateException`.

### Static Content References

- Saving stores definition codes only, and only for definitions identical to the current catalogue's definition for that code.
- Loading resolves codes through the current `GameContentCatalog`. An unknown code, a run without its player character, or any stored value that fails domain validation raises a clear error; a partial state is never returned.
- Loading across changed static-content versions is not guaranteed (see `CONTENT.md` and `DEFERRED_DECISIONS.md`).

Player and run state is relational. JSONB is used only for the dynamic `SceneState`, not for simple fixed state.
