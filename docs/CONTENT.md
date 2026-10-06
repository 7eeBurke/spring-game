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

Each file is a top-level JSON array of objects. Authored order is preserved for deterministic iteration and inspection; it carries no selection meaning. Content-selection rules are deferred (see `DEFERRED_DECISIONS.md`).

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

## Loading Rules

Loading fails, naming the resource, for:
- a missing content file;
- malformed JSON or trailing content;
- a missing or null field;
- an unknown (for example misspelled) field;
- an unknown enum value (enum values are case-sensitive);
- a string where a number is expected, or a fractional number for an integer field;
- a key repeated within one object;
- a blank display name or an invalid code;
- negative weapon base damage or trauma;
- a duplicate code within one content type.

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

## Not Yet Defined

Definitions deliberately do not yet contain:
- weapon attack methods, attack templates, suitability, tags, handedness, stat affinities or attack-form modifiers;
- effectiveness against targets;
- passive triggers, bonuses or effects;
- ability charges, cooldowns, targeting, effects, affinities or requirements;
- item effects or physical tags.

These remain deferred (see `DEFERRED_DECISIONS.md`).
