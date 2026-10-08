# World and Procedural Generation

## Design Goal

A player should learn a region's themes and possible content without memorising exact room order, spawn positions, guaranteed items or fixed interactions.

The starting portion of a roguelike is seen more often than later content, so opening variation must receive stronger anti-repeat weighting.

## Static vs Run State

Hierarchy:

`RegionDefinition`
→ `SceneArchetypeDefinition`
→ generated `RegionInstance`
→ generated/persisted `SceneState`
→ filtered `PlayerSceneView`

Authored definitions say what can exist (classpath JSON, see `CONTENT.md`).
Run instances say what exists now (PostgreSQL).

## Generation Pipeline

A new run's whole world is generated once, when the world is initialized:

1. take the `runSeed` and the immutable `GenerationContextSnapshot` (input; never changed by generation);
2. for attempt 0, 1, … (at most 8): generate a complete candidate region **in memory**: topology, archetypes and every scene's contents;
3. validate the complete candidate (see Complete-Region Validation); the first valid attempt wins;
4. build the fixed `THE_LAST_LANTERN` hub, linked to the region's entry scene;
5. persist everything atomically: generation context, region, hub, every region scene and the initial player location (see `ARCHITECTURE.md`).

Nothing is generated lazily when a scene is entered, nothing is rerolled when a scene is revisited, and a failed candidate is never persisted.

Character stats, loadout and Fated have no influence on generation, so mandatory progression is never tailored to (or dependent on) the rolled character.

## The Last Lantern (Hub)

`THE_LAST_LANTERN` is a small fixed hub scene. The player visits it after character generation/introduction and before entering Hollow Chapel.

- It is not part of the procedural Hollow Chapel graph.
- The player must be able to leave almost immediately.
- It must not become a repetitive tutorial sequence.

Authored contents: zones `lantern_hearth` (Lantern Hearth) and `chapel_road` (Chapel Road), joined by one connection; one exit `road_to_chapel` in `chapel_road`. No entities, objects, hazards, events or hidden content. The hub's exit names the region it leads to (`HOLLOW_CHAPEL`), which is the region generated for the run.

Persistence: kind `HUB`, definition code `THE_LAST_LANTERN`, revision 0, discovered. A `HUB` scene belongs directly to the run: no `RegionInstance` and no procedural scene seed. Its exit points at the generated entry scene, and the entry scene has one return exit `lantern_road` back to the hub.

The player's initial location is `lantern_hearth` in the hub.

## Hollow Chapel Graph

### Counts

- **Required scenes: 5–7.** These are the normal scenes on the route, including the entry scene and the pre-boss scene. The boss is not counted.
- **Optional scenes: 0–2.** Dead-end side scenes.
- **Branches: 1–2.** A branch is a **route fork**: a stage where the route splits into two alternative scenes that rejoin at the next stage. Optional scenes are not branches.
- **Boss scene: 1.**

A region therefore has 6–10 scenes. A route with B forks needs at least 2 + 2B required scenes, so two branches need 6 or 7 required scenes.

### Topology

The route is a sequence of stages from the entry to the pre-boss scene. The first and last stages hold one scene each; a fork stage holds two; other middle stages hold one.

```
Required 6, branches 1, optional 1:

          ┌─ A ─┐
ENTRY ─ S ┤     ├─ S2 ─ PRE ─ BOSS
          └─ B ─┘
               │
             (opt)

Required 6, branches 2, optional 0:

        ┌ A ┐   ┌ C ┐
ENTRY ──┤   ├───┤   ├── PRE ── BOSS
        └ B ┘   └ D ┘
```

- Every scene of a stage links to every scene of the next stage; the two scenes of a fork do not link to each other.
- The pre-boss scene links to the boss.
- Each optional scene links to exactly one host: a distinct route scene other than the pre-boss scene.
- Every link is **bidirectional** and is two real `SceneExit`s. No region exit is ever hidden, locked or one-way.
- The only exit leaving the region is the entry scene's return exit to the hub.

Exits within a scene are named `exit_1`, `exit_2`, … in the generation order of their destinations; each exit's zone is drawn from the archetype's exit zones.

Scenes have only structural roles (entry, route, pre-boss, optional, boss). Thematic roles (combat, event, resource, danger) and their compatibility with archetypes are deferred.

### Topology draws

From the attempt's stream, in this order: required count uniformly from its range; branch count uniformly from the values valid for it; optional count uniformly; the placement of forks among the middle stages, uniformly over all arrangements; the optional scenes' hosts, uniformly without replacement.

## Archetypes and Anti-Repetition

Normal archetypes: `RUINED_NAVE`, `CLOISTER`, `SACRISTY`, `OSSUARY`, `BELL_PASSAGE`, `RELIQUARY`. Boss arena: `GUARDIAN_SANCTUM` (always the boss scene, never a normal scene).

### Opening scene

The entry scene's archetype is drawn with integer weights. Each normal archetype has weight **8**, except archetypes in the snapshot's recent openings, which take the weight of their most recent position:

| Position in recent openings | Weight |
|---|---|
| most recent | 1 |
| second | 3 |
| third | 5 |
| not recent | 8 |

A roll in `[0, total)` walks the cumulative weights in authored order. No weight is zero, so a choice always exists even when every archetype is recent. Recent codes from other regions have no effect.

### Other scenes

Scenes are assigned in generation order (route stages, then optional scenes). For each scene: exclude archetypes already assigned to its graph neighbours, keep the archetypes with the lowest use count so far in this region, and choose uniformly. Adjacent scenes therefore never share an archetype, and all six are used before any is reused (reuse happens only with more than six normal scenes).

### Across runs

Recent-run history reaches generation only through the snapshot, read once when the world is created. Building the snapshot from a player's past runs is deferred until an account layer exists. Run creation (Stage 14) currently passes an empty snapshot.

## Scene Contents

Each archetype has fixed zones and connections (no coordinates or grids), a list of zones that region exits may use, and **content slots**. Each slot has a kind (entity, object, hazard or event), candidate definition codes, candidate zones, a fill chance and a hidden chance (integer percentages). For each slot in authored order:

1. roll `[0, 100)` below the chance to decide whether the slot is filled;
2. if filled, choose a candidate uniformly and a zone uniformly;
3. roll `[0, 100)` below the hidden chance to decide whether it starts hidden.

The slot ID becomes the content's local ID. Any scene may end up quiet or nearly empty; no scene is required to contain an enemy, object, hazard or event. Zones are never hidden, and progression exits are never hidden. Hidden content is safe because `PlayerSceneView` never shows it.

The boss arena always places one visible `CHAPEL_GUARDIAN`; no other archetype can place it.

**Region-unique events:** after every scene is generated, scenes are visited in generation order and each event code is kept only at its first occurrence. A dropped duplicate's hidden-content reference is removed with it.

The bundled archetype contents are listed in `CONTENT.md`.

## Randomness and Seeds

- One algorithm: `L64X128MixRandom` (specified by the JDK's `java.util.random` documentation).
- Seed derivation uses the SplitMix64 finalizer `mix64`, and `derive(parent, domain, index) = mix64(parent ^ mix64(domain + index × 0x9E3779B97F4A7C15))`.
- **Attempt seed:** `derive(runSeed, 1, attempt)`. Each attempt's topology and archetype draws use one stream from it.
- **Scene seed:** `derive(attemptSeed, 2, sceneIndex)`, where scenes are indexed in generation order (route stages, optional scenes, boss). Each scene's contents and exit zones use that scene's own stream, so a draw in one scene never shifts another.
- The scene seed is persisted with each `REGION` scene as provenance only. It is never used to reroll a scene. The hub has no seed.
- Runtime UUIDs come from a separate ID source and never consume the procedural streams. They are persistence identity, not part of the reproducibility contract.

## Deterministic Retry

At most **8** attempts. Attempt seeds depend only on the run seed and attempt index (no clock, no UUIDs), and the snapshot and content are the same for every attempt, so the same inputs always produce the same winning attempt. If all 8 fail validation, generation fails with an error naming the run, region and the last attempt's problems. No attempt touches persistence.

## Complete-Region Validation

Before persistence the complete region is validated (and validated again by the persistence layer before any write):

- unique scene IDs; every scene is a `REGION` scene of the generated region and run, at revision 0;
- exactly one entry and one boss; the boss uses `GUARDIAN_SANCTUM`; other scenes use the region's normal archetypes;
- required, optional and branch counts within the definition; route stages have the documented shape;
- every exit resolves to a region scene (except the entry's single return exit); no self, duplicate, hidden or one-way exits;
- the exit graph is exactly the documented topology;
- every scene, including the boss, is reachable from the entry;
- no hidden zones; adjacent scenes never share an archetype;
- exactly one visible `CHAPEL_GUARDIAN`, only in the boss scene;
- each event code appears at most once.

The world as a whole also checks that the hub's exit leads to the entry, the entry's return exit leads to this hub, the player starts in a visible hub zone, and everything belongs to the run.

Not yet validated, because the mechanics do not exist: a recovery opportunity before the boss, threat pacing between scenes, and objective/gate solutions.

## Generation Context Snapshot

The `GenerationContextSnapshot` is the immutable input describing history before this run. Currently it holds only `recentOpeningArchetypeCodes`: at most 3 codes, most recent first, each a valid definition code (it may name content from other regions). It is persisted once per run, exactly as used, and its presence marks the run's world as initialized.

## World and Scene State Model

The world/scene state model (instances, state, the player-safe view and their persistence) is separate from region generation. Generation produces instances of this model; the model itself contains no generation logic.

### RegionInstance

A generated region belonging to a run: `id`, `runId`, `definitionCode` (for example `HOLLOW_CHAPEL`). Generation metadata (route structure, attempt, roles) is used for validation only and is not persisted.

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
- `entities[]` — placement identity only (local ID, definition code, zone); enemy mechanical state is kept separately (see Enemy State)
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

Determining which zones are visible (line of sight) is deferred. During play (Stage 14), every non-hidden zone of the current scene is treated as visible. Hidden content therefore stays hidden, and everything else in the scene is shown. Active events and environment flags are not exposed until rules define how they become perceivable. Combat range and relative positioning belong to combat.

### Player Location

The run's current location is a scene and a zone within it. It is world state, not character state. The scene must belong to the same run, and the zone must exist in that scene and must not be hidden.

When the player leaves through an exit (Stage 14), the new location is the destination scene, at the zone of that scene's own exit back to the origin. Generation guarantees exactly one such return exit. The destination is then marked `discovered`. Scene contents are never generated on entry: they already exist from run creation.

## Enemy State

Every placed entity whose code has an enemy definition (see `CONTENT.md`) gets its mechanical state (stats, HP, body, weapon) when the run's world is generated, including hidden placements. That state is persisted in the same transaction as the world, before play begins. It is never generated lazily on scene entry, and loading never regenerates it.

Each enemy draws from its own random stream, seeded by `WorldRandom.entitySeed(sceneSeed, localId)`: the SplitMix64 derivation used for scenes, with entity domain 3 and a 64-bit FNV-1a hash of the local ID's UTF-8 bytes. Adding, removing or reordering other entities therefore never changes an enemy. A normal enemy makes one draw (its stat shape) and the boss none (see `GAME_RULES.md` "Enemies"). Fated has no effect. The hub has no scene seed and holds no enemies.

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

Required progression should eventually specify a goal, not one mandatory item. For example, opening a boss gate may support several solution categories:
- key route;
- force route;
- arcane route;
- NPC route;
- secret route.

At least one valid route must always exist without requiring a specific randomly generated player stat or missing tool.

Objective and gate mechanics are deferred. The current generated graph is fully traversable structurally: no mandatory locks, hidden mandatory exits or tool-dependent progression.

The Chapel Guardian's reward is conceptually the Ashen Sigil. Reward granting is deferred; nothing represents it in generated content yet, and nothing is placed in the boss arena before the boss is defeated.

## Reproducibility

A fully reproducible generation input consists of:
- `runSeed`
- `generationContextSnapshot`

The same `runSeed` plus the same `generationContextSnapshot` reproduce the same topology, archetypes, scene contents and scene seeds (runtime UUIDs excluded).

This reproduction assumes the same game-rules and static-content version (see `CONTENT.md`). Cross-version replay and content versioning are deferred.
