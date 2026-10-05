# World and Procedural Generation

## Design Goal

A player should learn a region's themes and possible content without memorising exact room order, spawn positions, guaranteed items or fixed interactions.

The starting portion of a roguelike is seen more often than later content, so opening variation must receive stronger anti-repeat weighting.

## Static vs Run State

Hierarchy:

`RegionDefinition`
→ `SceneArchetype`
→ generated `RegionInstance`
→ generated/persisted `SceneState`
→ filtered `PlayerSceneView`

Authored definitions say what can exist.
Run instances say what exists now.

## The Last Lantern (Hub)

`THE_LAST_LANTERN` is a small fixed hub scene. The player visits it after character generation/introduction and before entering Hollow Chapel.

- It is not part of the procedural Hollow Chapel graph.
- The player must be able to leave almost immediately.
- It must not become a repetitive tutorial sequence.

Persistence: the hub uses the same persistent `SceneInstance` concept as other scenes, but belongs directly to the run rather than to a `RegionInstance`. The future persistence model therefore:
- permits a scene instance to have no region association;
- identifies each scene instance as a hub scene or a region scene.

## Hollow Chapel MVP Graph

Generate approximately:
- 5–7 required normal scenes;
- 0–2 optional scenes;
- 1–2 meaningful branches;
- 1 boss scene.

Assign scene roles before choosing archetypes, for example:
- `ENTRY`
- `EXPLORATION`
- `COMBAT`
- `EVENT`
- `RESOURCE`
- `DANGER`
- `TRANSITION`
- `PRE_BOSS`
- `OPTIONAL_REWARD`
- `BOSS`

Then choose compatible scene archetypes.

## MVP Scene Archetypes

Normal:
- `RUINED_NAVE`
- `CLOISTER`
- `SACRISTY`
- `OSSUARY`
- `BELL_PASSAGE`
- `RELIQUARY`

Boss:
- `GUARDIAN_SANCTUM`

Do not hardcode exact object/enemy positions into archetypes.

## Anti-Repetition

Track recent-run history for opening archetypes/events/enemies.
Recently seen content should be downweighted rather than permanently banned.

Recent-run history is read once, at run creation, into that run's `generationContextSnapshot` (see Seeds and Validation). Generation never reads live history directly.

Opening scenes should receive stronger anti-repeat weighting than late scenes.
Pure RNG repeating the same opening several times is considered a generation quality failure even if technically random.

## Scene Generation

The entire Hollow Chapel region — graph and every scene's contents — is generated at run creation:
1. generation completes fully in memory (graph plus all scene contents);
2. region-level validation runs against the complete generated region (see Seeds and Validation);
3. only a valid region is persisted, in a single transaction.

Nothing is persisted for a region that fails validation.
Undiscovered scene contents are not generated (or rerolled) when the player enters a scene.

Recommended per-scene generation order (in memory):
1. zones;
2. zone connections;
3. environmental objects;
4. hazards;
5. enemies/NPCs;
6. loot/tools;
7. hidden content;
8. events;
9. scene-level validation.

Persistence happens once, for the whole region, after region-level validation succeeds.

Normal scenes typically contain 3–5 zones; boss arena 3–4.

Use content budgets/constraints so procedural generation is controlled rather than arbitrary.
Do not force the pattern "enemy + interactable + reward" into every scene.
Some scenes may be quiet, event-driven, resource-rich, dangerous, or almost empty.

## SceneState

Authoritative conceptual fields:
- `sceneInstanceId`
- `regionInstanceId` (absent for the hub scene, which belongs directly to the run)
- `archetypeCode`
- `sceneSeed`
- `sceneTags[]`
- `zones[]`
- `zoneConnections[]`
- `entities[]`
- `objects[]`
- `hazards[]`
- `exits[]`
- `activeEvents[]`
- `environmentStates[]`
- `hiddenContent[]`
- `discoveredFacts[]`
- `sceneHistory[]`
- `revision`

## PlayerSceneView

Filtered view containing only what the player currently knows/sees:
- current zone;
- visible/known zones;
- visible entities;
- visible objects;
- visible hazards;
- known exits;
- known environmental facts;
- player position/range;
- recent visible changes.

The AI must not receive hidden content.

## Persistence

Generated scenes are instantiated once.
Revisiting or refreshing must not reroll:
- objects;
- enemies;
- secrets;
- events;
- loot;
- exits.

World mutations persist: dead enemies remain dead, items remain removed, revealed exits remain revealed, burned objects remain burned.

## Progression

Required progression should specify a goal, not one mandatory item.
For example, opening a boss gate may support several generated solution categories:
- key route;
- force route;
- arcane route;
- NPC route;
- secret route.

At least one valid route must always exist without requiring a specific randomly generated player stat or missing tool.

## Seeds and Validation

Every run gets a `runSeed`; generated regions/scenes derive deterministic streams/seeds.

A fully reproducible generation input consists of:
- `runSeed`
- `generationContextSnapshot`

The `generationContextSnapshot` contains the recent-content/anti-repeat information used when the run was created. It is captured at run creation, persisted with the run, and immutable for the lifetime of that run.

The same `runSeed` plus the same `generationContextSnapshot` must reproduce the same generated region.

After generation, validate hard constraints such as:
- boss reachable;
- required scenes connected;
- no required dead-end;
- at least one valid progression solution;
- no impossible mandatory traversal;
- recovery opportunity before boss;
- no excessive consecutive high-threat scenes;
- unique events not duplicated.

Validation runs against the complete in-memory region. Regenerate invalid structures before persisting; only a valid region is persisted, transactionally.
