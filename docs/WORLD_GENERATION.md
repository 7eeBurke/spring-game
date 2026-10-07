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

Persistence: the hub uses the same persistent `SceneInstance` concept as other scenes, with kind `HUB` and definition code `THE_LAST_LANTERN`. A `HUB` scene belongs directly to the run: it has no `RegionInstance` and no procedural scene seed (see World and Scene State Model).

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

## World and Scene State Model

The world/scene state model (instances, state, the player-safe view and their persistence) is separate from region generation. Generation produces instances of this model; the model itself contains no generation logic.

### RegionInstance

A generated region belonging to a run: `id`, `runId`, `definitionCode` (for example `HOLLOW_CHAPEL`). Generation metadata (route graph, roles, generation context) belongs to region generation.

### SceneInstance (relational metadata)

- `id`, `runId`
- `definitionCode` (for example `THE_LAST_LANTERN`, `RUINED_NAVE`)
- placement:
  - `HUB` — belongs directly to the run; no region, no procedural seed;
  - `REGION` — belongs to a `RegionInstance` and carries its `sceneSeed`;
- `discovered`
- `revision` — starts at 0 and increases by one on every state update (optimistic concurrency, see `ARCHITECTURE.md`)
- `state` — the `SceneState`

Invalid hub/region combinations cannot be represented, and no magic IDs or seeds are used.

### SceneState (dynamic contents only)

Scene identity, run, region, kind, definition code, seed, discovered flag and revision are `SceneInstance` metadata and are not duplicated here.

- `zones[]` — meaningful areas (not coordinates or tiles), each with a stable local ID and display name; at least one
- `connections[]` — undirected movement possibilities between two zones of the scene
- `entities[]` — placement identity only (local ID, definition code, zone); enemy state comes later
- `objects[]`, `hazards[]`, `activeEvents[]` — local ID, definition code, zone; mechanics come later
- `exits[]` — local ID, origin zone, destination scene ID
- `environmentFlags[]` — scene-wide state flags (codes)
- `hiddenContent[]` — typed references (kind + local ID) marking content as hidden; content is stored once
- `discoveredFacts[]` — fact codes the player has genuinely learned

Structural integrity is enforced: unique local IDs per collection, every zone reference resolves, connections join two distinct existing zones with no duplicate pairs, hidden references resolve to real content of their kind, and flags/facts are unique codes.

Scene history is deferred until its entry schema is designed. Per-object state (for example burned or open) and zone tags are deferred until a mechanic needs them.

### PlayerSceneView

The only scene representation given to action interpretation and AI roles. It uses its own records, never the authoritative ones, and contains only:
- current zone;
- visible zones (ID and display name);
- visible connections between visible zones (no connection IDs);
- visible entities, objects and hazards (local ID, definition code, zone);
- known exits (local ID and zone; never the destination);
- discovered facts.

Projection rules, given the set of currently visible zones supplied by the caller:
- hidden zones are never shown, even if the caller lists them;
- hidden content is never shown;
- content inside a zone that is not effectively visible is never shown;
- a connection is shown only if it is not hidden and both its zones are visible;
- the current zone must exist, must not be hidden and must be among the visible zones.

Determining which zones are visible (line of sight) is deferred. Active events and environment flags are not exposed until rules define how they become perceivable. Combat range and relative positioning belong to combat.

### Player Location

The run's current location is a scene and a zone within it. It is world state, not character state. The scene must belong to the same run, and the zone must exist in that scene and must not be hidden.

## Region Generation

Region generation builds a complete generated region (graph, roles, archetypes, every scene's contents) as instances of the model above, validates it as a whole, and persists it once. This includes validating that exits reference real scenes and that required routes are coherent; the database cannot validate exit destinations stored inside scene state. See Scene Generation, Hollow Chapel MVP Graph and Seeds and Validation.

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

Dynamic scene state is persisted once and loaded as stored; it is never regenerated or rerolled because a scene is revisited.

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

This reproduction assumes the same game-rules and static-content version (see `CONTENT.md`). Cross-version replay and content versioning are deferred.

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
