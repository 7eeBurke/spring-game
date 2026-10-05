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
- `character` — character generation and character state
- `mechanics` — checks, DCs, suitability, damage, trauma
- `action` — `ActionIntent`, validation, step resolution, `ResolvedOutcome`
- `combat`
- `enemy`
- `world` — region/scene generation, `SceneState`, `PlayerSceneView`
- `run` — run lifecycle
- `ai` — AI role adapters and deterministic fallbacks
- `api` — HTTP controllers and request/response DTOs

Packages are created only when a stage needs them.
Domain game logic stays independent of Spring wherever practical (plain Java classes, constructor-injected, unit-testable without a Spring context); Spring wiring lives at the edges (`config`, `api`, persistence adapters).

## Technology Stack

- Java 21 LTS
- Spring Boot 4.1.x (Spring Web, Spring Data JPA, Validation, OpenAPI)
- Maven, with the Maven Wrapper committed to the repository
- PostgreSQL
- Flyway for schema migrations
- Hibernate schema validation (`ddl-auto=validate`), not automatic schema creation
- Testcontainers PostgreSQL for database integration tests
- React + TypeScript + Vite for the frontend (later stage)
- Lombok is optional

The AI provider is deliberately unspecified until the AI integration stage. Do not add an AI provider SDK before then.

## Persistence Strategy

- Important lifecycle/domain relationships (run, character, region instance, scene instance, etc.) remain relational.
- `SceneInstance` has relational identity/metadata plus a `revision`.
- `SceneInstance` always belongs to a run. A region scene also belongs to a `RegionInstance`; the hub scene (`THE_LAST_LANTERN`) has no region association. Each scene instance is identified as a hub or region scene.
- A generated region is built and validated fully in memory, then persisted in a single transaction; invalid regions are never persisted.
- Dynamic nested `SceneState` content may be persisted as PostgreSQL JSONB.
- Static game definitions (weapons, items, passives, abilities, enemies, scene archetypes, events) do not automatically become database tables.
