# Static Content

Static content definitions say what can exist. They are authored as JSON, loaded at startup into an immutable catalogue, and never stored in the database.

Run state (what a particular character carries, what exists in a scene) is separate and refers to definitions by their definition code.

## Files

Location: `src/main/resources/content/`

| File | Definition type |
|---|---|
| `weapons.json` | `WeaponDefinition` |
| `passives.json` | `PassiveDefinition` |
| `abilities.json` | `AbilityDefinition` |
| `items.json` | `ItemDefinition` |
| `character-names.json` | character name pool (strings) |

Each definition file is a top-level JSON array of objects; `character-names.json` is a top-level JSON array of strings. Authored order is preserved for deterministic iteration and inspection; it carries no selection meaning. Character generation currently selects uniformly within each pool (see `CHARACTER_GENERATION.md`); weighted selection rules are deferred (see `DEFERRED_DECISIONS.md`).

## Definition Codes

Every definition has a stable, machine-readable definition code, for example `LONGSWORD` or `BANDAGE`.

- Format: upper snake case, `[A-Z][A-Z0-9_]*`.
- Unique within its content type. Different content types may share a code.
- A definition code is not the runtime instance ID of a weapon or item a character possesses. The runtime instance ID format is deferred.

Every definition also has a non-blank `displayName`.

## Schema

All fields are required. Unknown fields fail loading.

### Weapons

| Field | Type | Constraint |
|---|---|---|
| `code` | string | definition code format |
| `displayName` | string | not blank |
| `baseDamage` | integer | `>= 0` |
| `trauma` | integer | `>= 0` |
| `primaryDamageType` | `DamageType` | `SLASHING`, `PIERCING`, `BLUNT`, `FIRE` |

The primary damage type is content identity only. Which damage an individual attack method actually deals (for example an Ember Rod used to strike physically) is deferred.

### Passives and Abilities

| Field | Type | Constraint |
|---|---|---|
| `code` | string | definition code format |
| `displayName` | string | not blank |

### Items

| Field | Type | Constraint |
|---|---|---|
| `code` | string | definition code format |
| `displayName` | string | not blank |
| `category` | `ItemCategory` | `RECOVERY`, `UTILITY` |

### Character Names

`character-names.json` is an array of strings. Each name:
- is a JSON string (numbers or booleans are not converted);
- is not blank;
- has no leading or trailing whitespace (names are rejected, never trimmed);
- is unique ignoring case, so `Aldric` and `aldric` cannot both appear and silently double a choice's probability.

Names are stored exactly as authored. Adding a name is a JSON-only change.

## Loading Rules

Loading fails, naming the resource, for:
- a missing content file;
- malformed JSON or trailing content;
- a missing or null field;
- an unknown (for example misspelled) field;
- an unknown enum value (enum values are case-sensitive);
- a string where a number is expected, or a fractional number for an integer field;
- a number or boolean where a string is expected;
- a key repeated within one object;
- a blank display name or an invalid code;
- negative weapon base damage or trauma;
- a duplicate code within one content type;
- a blank, untrimmed or case-insensitively duplicated character name.

## Adding Content

Adding an entry that uses only the existing fields is a JSON-only change; no Java change is needed.
A genuinely new mechanic (a new field or behaviour) requires a design decision first.

## Versioning

Reproducing a run from `runSeed` + `generationContextSnapshot` assumes the same game-rules and static-content version. Changing content JSON between application versions may change what a seed produces. Cross-version replay and content versioning are deferred (see `DEFERRED_DECISIONS.md`).

## Bundled MVP Catalogue

### Weapons

| Code | Display name | Base damage | Trauma | Primary damage type |
|---|---|---|---|---|
| `LONGSWORD` | Longsword | 6 | 3 | `SLASHING` |
| `DAGGER` | Dagger | 4 | 2 | `PIERCING` |
| `WAR_HAMMER` | War Hammer | 7 | 6 | `BLUNT` |
| `EMBER_ROD` | Ember Rod | 5 | 3 | `FIRE` |

### Passives

| Code | Display name |
|---|---|
| `LIGHT_FOOT` | Light Foot |
| `IRON_GRIP` | Iron Grip |
| `GRAVE_SENSE` | Grave Sense |
| `CLEAR_MIND` | Clear Mind |
| `IMPROVISER` | Improviser |
| `ASH_TOUCHED` | Ash Touched |

### Abilities

| Code | Display name |
|---|---|
| `STONEBLOOD` | Stoneblood |
| `EMBER_EDGE` | Ember Edge |
| `SHADOW_STEP` | Shadow Step |
| `WARDING_SIGIL` | Warding Sigil |

### Items

| Code | Display name | Category |
|---|---|---|
| `BANDAGE` | Bandage | `RECOVERY` |
| `RESTORATIVE_SALVE` | Restorative Salve | `RECOVERY` |
| `ROPE` | Rope | `UTILITY` |
| `TORCH` | Torch | `UTILITY` |
| `CROWBAR` | Crowbar | `UTILITY` |
| `LOCKPICKS` | Lockpicks | `UTILITY` |

### Character Names

26 names:

Aldric, Bram, Cael, Corvin, Dagny, Edric, Elske, Garrow, Hesk, Ilse, Isolde, Joren, Kestrel, Lorne, Maren, Morwen, Nyle, Oswin, Perrin, Rook, Sabine, Tamsin, Ulric, Vesna, Wren, Yorick

## Not Yet Defined

Definitions deliberately do not yet contain:
- weapon attack methods, attack templates, suitability, tags, handedness, stat affinities or attack-form modifiers;
- effectiveness against targets;
- passive triggers, bonuses or effects;
- ability charges, cooldowns, targeting, effects, affinities or requirements;
- item effects or physical tags.

These remain deferred (see `DEFERRED_DECISIONS.md`).

# Lore

`src/main/resources/content/lore.json` holds the fixed world premise that the Character Introduction Narrator restates. It is loaded strictly by `LoreCatalog` and is canon kept in a resource, not in prompt code.

Schema: `{ "premise": [ non-blank strings ] }`, with at least one line.

Bundled premise:
1. The world is sustained by a single fading supernatural flame.
2. As the flame weakens, the dead rise.
3. Time and reality have begun to distort.
4. You are a Bound Soul.
5. The Last Lantern is a small refuge on the road to the Hollow Chapel.

Narrators may restate this premise but never extend it with new canon. Expanding the lore needs explicit approval.

# World Generation Content

Authored world-generation content lives in `src/main/resources/content/world/` and is loaded by `WorldContentLoader` into an immutable `WorldContentCatalog`, with the same strict loading rules as the files above. It is static classpath content, never stored in the database; PostgreSQL stores only generated instances.

| File | Definition type |
|---|---|
| `world-elements.json` | `WorldElementDefinition` |
| `scene-archetypes.json` | `SceneArchetypeDefinition` |
| `regions.json` | `RegionDefinition` |
| `fixed-scenes.json` | `FixedSceneDefinition` |

Each file is a top-level JSON array. Authored order is kept for inspection and deterministic iteration; generation uses explicit selection rules (see `WORLD_GENERATION.md`), never "first in the file".

## Schemas

### World elements

Identity only: `code`, `displayName`, `kind` (`ENTITY`, `OBJECT`, `HAZARD`, `EVENT`). Codes are unique across the file. No stats, behaviour or mechanics.

### Scene archetypes

| Field | Constraint |
|---|---|
| `code`, `displayName` | definition code; non-blank name |
| `zones` | `{id, displayName}`; at least one, unique IDs |
| `connections` | `{id, zoneA, zoneB}`; distinct existing zones, no duplicate pairs |
| `exitZones` | non-empty, unique, existing zones where region exits may be placed |
| `slots` | content slots (below); unique slot IDs |

All zones must be connected to each other.

A content slot: `id` (becomes the placed content's local ID), `kind`, `candidates` (non-empty, unique, existing elements of the slot's kind), `zones` (non-empty, unique, existing zones), `chance` (1–100), `hiddenChance` (0–100).

### Regions

`code`, `displayName`, `normalArchetypes` (at least 3, unique, existing, excluding the boss archetype), `bossArchetype`, `bossEntity` (an `ENTITY`), and the ranges `requiredScenes`, `optionalScenes`, `branches` as `{min, max}`. Both ends of the branch range must be attainable: `requiredScenes.min >= 2 + 2 × branches.min` and `requiredScenes.max >= 2 + 2 × branches.max`. The boss archetype must have a slot that always places exactly the boss entity, visible (`chance` 100, `hiddenChance` 0); no normal archetype may place it.

### Fixed scenes

`code`, `displayName`, `zones`, `connections` (connected, as for archetypes), `startZone`, and `exit` `{id, zoneId, destinationRegion}` naming an existing region.

Loading fails, naming the file or directory, for malformed JSON, missing or unknown fields, wrong value types, bad codes, duplicate codes, unknown references, kind mismatches, unknown or duplicate zones, broken or disconnected layouts, bad chances and unattainable count ranges.

## Bundled World Content

### World elements

| Kind | Codes |
|---|---|
| `ENTITY` | `HOLLOW_ACOLYTE`, `BONE_WARDEN`, `ASHBOUND_PENITENT`, `CHAPEL_GUARDIAN` |
| `OBJECT` | `WOODEN_PEW`, `STONE_PILLAR`, `ALTAR`, `WOODEN_DOOR`, `CHAIN`, `CORPSE`, `CRATE` |
| `HAZARD` | `UNSTABLE_CEILING`, `FIRE`, `PRESSURE_PLATE`, `COLLAPSING_FLOOR` |
| `EVENT` | `FERRYMAN_OF_ASH`, `WOUNDED_PILGRIM`, `FALSE_BLESSING`, `HIDDEN_PRAYER_SHARD`, `RELIQUARY_BARGAIN` |

`WOODEN_DOOR` is authored but not placed by any slot yet. Fated variants (Bleeding Statue, Broken Veteran, Cursed Relic) are deferred.

### `HOLLOW_CHAPEL`

Normal archetypes `RUINED_NAVE`, `CLOISTER`, `SACRISTY`, `OSSUARY`, `BELL_PASSAGE`, `RELIQUARY`; boss archetype `GUARDIAN_SANCTUM`; boss entity `CHAPEL_GUARDIAN`; required scenes 5–7, optional scenes 0–2, branches 1–2.

### Archetypes

Slots are written as `slot: kind [candidates] → zones, chance / hiddenChance`.

**`RUINED_NAVE`** — zones `nave_entrance`, `central_aisle`, `collapsed_pews`, `apse`; connections entrance–aisle, aisle–pews, aisle–apse; exit zones `nave_entrance`, `apse`.
- `pews`: OBJECT [WOODEN_PEW] → central_aisle, collapsed_pews, 80 / 0
- `nave_altar`: OBJECT [ALTAR] → apse, 60 / 0
- `nave_enemy`: ENTITY [HOLLOW_ACOLYTE, ASHBOUND_PENITENT] → central_aisle, collapsed_pews, apse, 50 / 20
- `nave_ceiling`: HAZARD [UNSTABLE_CEILING] → collapsed_pews, central_aisle, 35 / 50
- `nave_event`: EVENT [FALSE_BLESSING, WOUNDED_PILGRIM] → apse, nave_entrance, 25 / 0

**`CLOISTER`** — zones `cloister_walk`, `overgrown_garth`, `broken_arcade`; connections walk–garth, walk–arcade, garth–arcade; exit zones `cloister_walk`, `broken_arcade`.
- `arcade_pillar`: OBJECT [STONE_PILLAR] → broken_arcade, cloister_walk, 70 / 0
- `garth_corpse`: OBJECT [CORPSE] → overgrown_garth, cloister_walk, 40 / 30
- `cloister_enemy`: ENTITY [HOLLOW_ACOLYTE] → overgrown_garth, broken_arcade, 55 / 25
- `arcade_floor`: HAZARD [COLLAPSING_FLOOR] → broken_arcade, 25 / 60
- `cloister_event`: EVENT [WOUNDED_PILGRIM] → cloister_walk, 20 / 0

**`SACRISTY`** — zones `vestry_door`, `vestment_racks`, `locked_alcove`; connections door–racks, racks–alcove; exit zone `vestry_door`.
- `sacristy_crate`: OBJECT [CRATE] → vestment_racks, locked_alcove, 70 / 20
- `alcove_chain`: OBJECT [CHAIN] → locked_alcove, 40 / 0
- `sacristy_enemy`: ENTITY [ASHBOUND_PENITENT] → vestment_racks, 35 / 0
- `sacristy_plate`: HAZARD [PRESSURE_PLATE] → vestry_door, locked_alcove, 30 / 80
- `prayer_shard`: EVENT [HIDDEN_PRAYER_SHARD] → locked_alcove, vestment_racks, 30 / 100

**`OSSUARY`** — zones `bone_stair`, `skull_niches`, `charnel_pit`, `warden_post`; connections stair–niches, niches–pit, niches–post; exit zones `bone_stair`, `warden_post`.
- `pit_corpse`: OBJECT [CORPSE] → charnel_pit, skull_niches, 80 / 0
- `ossuary_warden`: ENTITY [BONE_WARDEN] → warden_post, charnel_pit, 60 / 15
- `niche_lurker`: ENTITY [HOLLOW_ACOLYTE] → skull_niches, 30 / 40
- `pit_floor`: HAZARD [COLLAPSING_FLOOR] → charnel_pit, 30 / 50
- `ossuary_event`: EVENT [FERRYMAN_OF_ASH] → bone_stair, 20 / 0

**`BELL_PASSAGE`** — zones `bell_landing`, `rope_gallery`, `cracked_belfry`; connections landing–gallery, gallery–belfry; exit zones `bell_landing`, `cracked_belfry`.
- `bell_chain`: OBJECT [CHAIN] → rope_gallery, cracked_belfry, 70 / 0
- `belfry_ceiling`: HAZARD [UNSTABLE_CEILING] → cracked_belfry, rope_gallery, 45 / 40
- `passage_enemy`: ENTITY [HOLLOW_ACOLYTE, BONE_WARDEN] → bell_landing, rope_gallery, 45 / 20
- `bell_event`: EVENT [FALSE_BLESSING] → bell_landing, 15 / 0

**`RELIQUARY`** — zones `reliquary_gate`, `relic_shelves`, `sealed_reliquary`, `side_chapel`; connections gate–shelves, shelves–sealed, shelves–side; exit zones `reliquary_gate`, `side_chapel`.
- `side_altar`: OBJECT [ALTAR] → side_chapel, 60 / 0
- `shelf_clutter`: OBJECT [CRATE, CORPSE] → relic_shelves, sealed_reliquary, 60 / 25
- `reliquary_enemy`: ENTITY [ASHBOUND_PENITENT, HOLLOW_ACOLYTE] → relic_shelves, side_chapel, 45 / 20
- `chapel_fire`: HAZARD [FIRE] → side_chapel, relic_shelves, 30 / 0
- `bargain_event`: EVENT [RELIQUARY_BARGAIN] → sealed_reliquary, 35 / 0

**`GUARDIAN_SANCTUM`** (boss) — zones `sanctum_threshold`, `guardian_dais`, `pillared_flank`, `ember_choir`; connections threshold–dais, threshold–flank, dais–flank, dais–choir; exit zone `sanctum_threshold`.
- `guardian`: ENTITY [CHAPEL_GUARDIAN] → guardian_dais, 100 / 0
- `sanctum_pillars`: OBJECT [STONE_PILLAR] → pillared_flank, 80 / 0
- `choir_fire`: HAZARD [FIRE] → ember_choir, guardian_dais, 50 / 0
- `flank_floor`: HAZARD [COLLAPSING_FLOOR] → pillared_flank, ember_choir, 30 / 0

### `THE_LAST_LANTERN`

Zones `lantern_hearth` (Lantern Hearth) and `chapel_road` (Chapel Road), connection `hearth_road`; start zone `lantern_hearth`; exit `road_to_chapel` in `chapel_road`, leading to `HOLLOW_CHAPEL`.

Adding archetypes, slots, elements or another region that uses the existing fields is a JSON-only change.

# Enemy Content

Location: `src/main/resources/content/enemy/`, loaded by `EnemyContentLoader` into an immutable `EnemyCatalog` with the same strict JSON rules and its own mapper. The loader needs the weapon and world catalogues to check cross-references. Enemy definitions are static content: there are no database tables for them, and enemy run state refers to them by code (see `ARCHITECTURE.md`).

| File | Definition type |
|---|---|
| `anatomies.json` | `AnatomyDefinition` |
| `enemies.json` | `EnemyDefinition` with its `EnemyAttackOption`s |

## Schemas

### Anatomies

| Field | Rule |
|---|---|
| `code` | definition code, unique |
| `bodyParts` | non-empty list of `BodyPart`, no duplicates |

### Enemies

| Field | Rule |
|---|---|
| `code` | definition code, unique; must be a world element of kind `ENTITY` |
| `anatomy` | an anatomy code |
| `weapon` | a weapon code from `weapons.json`; damage, trauma, type and name are never repeated here |
| `statPriority` | all five `StatType`s, highest first; empty for a fixed-stat enemy |
| `fixedStats` | an object giving all five stats (3–10); empty (`{}`) for a shape-priority enemy |
| `baseHp` | at least 1, and high enough that max HP stays at least 1 for the lowest possible RESOLVE |
| `defenseStat` | the `StatType` behind the enemy's defense DC |
| `traits` | `EnemyTrait`s, no duplicates; `ADAPTIVE` and `RELENTLESS` cannot be combined |
| `holdWeight` | base behaviour weight of holding, at least 0 |
| `attacks` | at least one `{code, method: WeaponMethod, template: AttackTemplate, weight >= 1}` |

Exactly one of `statPriority` and `fixedStats` is non-empty. Attack option codes use the definition-code format, are unique across all enemies, and `HOLD` is reserved. A world `ENTITY` code with no enemy definition is allowed (for a future non-combat entity); every bundled `ENTITY` has one.

Loading fails, naming the file or directory, for malformed JSON, missing, null or unknown fields, wrong value types, unknown enum values, bad codes, duplicate codes, unknown anatomy, weapon or world entity, a world element that is not an `ENTITY`, and any rule above.

`EnemyTrait`: `AGGRESSIVE`, `CAUTIOUS`, `OPPORTUNISTIC`, `RECKLESS`, `ADAPTIVE`, `RELENTLESS`. Their behaviour effects are in `GAME_RULES.md` "Enemy behaviour"; `OPPORTUNISTIC` has none yet.

## Bundled Enemy Content

`HUMANOID` anatomy: all ten body parts. All four enemies use it.

| | `HOLLOW_ACOLYTE` | `BONE_WARDEN` | `ASHBOUND_PENITENT` | `CHAPEL_GUARDIAN` |
|---|---|---|---|---|
| Weapon | `DAGGER` | `WAR_HAMMER` | `EMBER_ROD` | `LONGSWORD` |
| Stats | priority AGI > PER > MIG > RES > ARC | priority MIG > RES > PER > AGI > ARC | priority ARC > RES > PER > AGI > MIG | fixed MIG 10, AGI 9, RES 8, PER 5, ARC 4 |
| Base HP | 12 | 20 | 14 | 40 |
| Defense stat | `AGILITY` | `MIGHT` | `AGILITY` | `MIGHT` |
| Traits | `AGGRESSIVE`, `OPPORTUNISTIC` | `RELENTLESS` | `CAUTIOUS`, `ADAPTIVE` | `AGGRESSIVE`, `RELENTLESS` |
| Hold weight | 0 | 0 | 10 | 0 |

Attack options (method / template / weight):

| Enemy | Options |
|---|---|
| `HOLLOW_ACOLYTE` | `ACOLYTE_QUICK_SLASH` SLASH / QUICK_SLASH / 55; `ACOLYTE_STAB` THRUST / THRUST / 45 |
| `BONE_WARDEN` | `WARDEN_OVERHEAD_SMASH` SMASH / OVERHEAD_STRIKE / 45; `WARDEN_HEAVY_SMASH` SMASH / HEAVY_SMASH / 35; `WARDEN_HAFT_HOOK` HOOK / HOOK_AND_PULL / 20 |
| `ASHBOUND_PENITENT` | `PENITENT_EMBER_BOLT` PROJECT / PROJECTED_ATTACK / 70; `PENITENT_ROD_STRIKE` SMASH / OVERHEAD_STRIKE / 20 |
| `CHAPEL_GUARDIAN` | `GUARDIAN_SWEEPING_CUT` SLASH / HORIZONTAL_SWING / 40; `GUARDIAN_OVERHEAD_CLEAVE` SLASH / OVERHEAD_STRIKE / 35; `GUARDIAN_LUNGE` THRUST / THRUST / 25 |

Adding an enemy for an existing world entity, or new attack options, is a JSON-only change.
