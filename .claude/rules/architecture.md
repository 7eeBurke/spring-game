# Architecture Rules

## Core Boundary

Never call an LLM from deterministic game-resolution logic.

`ActionIntent` describes what the player attempts.
`ResolvedOutcome` records what the Java engine determined happened.
Narration is downstream of state resolution and cannot mutate state.

## Visibility

The Action Interpreter receives `PlayerSceneView`, never authoritative `SceneState`.
Hidden objects, traps, exits, rewards, secret weaknesses and undiscovered facts must not be exposed to the interpreter or narrator.

## Validation

AI-produced structured intent must pass Java validation before resolution:
1. schema validation;
2. reference validation;
3. ownership/knowledge validation;
4. mechanical plausibility validation.

A strange but physically possible action is valid and should be resolved with poor suitability rather than rejected.
An action relying on nonexistent or unknown world facts must not be accepted as factual.

## Procedural Generation

Procedural generation is Java-owned, seeded and deterministic for a given `runSeed` plus `generationContextSnapshot` (captured at run creation, immutable for the run).
The entire Hollow Chapel region is generated and validated at run creation; scene contents are never generated on entry.
Generated regions/scenes are instantiated once and persisted.
Returning to a scene must restore the same world state.

## Enemy Behaviour

Enemy action selection is Java weighted-utility logic, not an LLM.
Enemies may use observable player state and short-term combat history, but must not read hidden player stats or hidden future state.

## Content Model

Prefer reusable definitions plus run instances:
- definition = what can exist;
- instance/state = what exists in this run now.

Examples:
- `WeaponDefinition` vs carried item instance;
- `EnemyDefinition` vs `EnemyInstance`;
- `SceneArchetype` vs `SceneState`;
- `EventDefinition` vs run event instance.

## Persistence

Persist generated introductions and generated scene content when consistency matters.
Refreshing, revisiting or reloading must not cause rerolls.
