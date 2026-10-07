# Game Rules

## Core Stats

Five stats:
- `MIGHT`: force, lifting, grappling, heavy physical actions, blocks/bracing.
- `AGILITY`: speed, coordination, reflexes, dodging, finesse attacks/parries.
- `PERCEPTION`: awareness, targeting, trajectory judgement, traps, injuries and situational clues.
- `ARCANA`: understanding and controlling supernatural forces.
- `RESOLVE`: composure, fear, pain, possession, corruption and concentration.

V1 stat modifier mapping:
- 3 → -3
- 4 → -2
- 5 → -1
- 6 → 0
- 7 → +1
- 8 → +2
- 9 → +3
- 10 → +4

## Checks

Core formula:

`d20 + relevant stat modifier` vs `Final Difficulty`.

Prototype base DC bands:
- 10 easy
- 12 manageable
- 14 standard danger
- 16 difficult
- 18 severe
- 20 extreme
- 22+ exceptional

Degree of success:
- result >= DC + 5 → `CRITICAL_SUCCESS`
- result >= DC → `SUCCESS`
- result >= DC - 4 → `PARTIAL_SUCCESS`
- lower → `FAILURE`

Critical failure is contextual; a natural 1 is not automatically catastrophic.

Final DC:

`FinalDC = BaseDC + clamp(sumOfNumericDCAdjustments, -5, +5)`

All ordinary numeric adjustments count toward this cap, including:
- weapon/action suitability;
- relevant injury;
- position;
- environment;
- passive effects;
- targeting;
- simultaneous-action complexity.

Physical impossibility is not represented as a numeric DC modifier; it is rejected before any check (see Suitability).

## Suitability

Levels:
- `EXCELLENT` → -2 DC
- `GOOD` → -1 DC
- `FAIR` → 0
- `POOR` → +1 DC
- `TERRIBLE` → +3 DC
- `IMPOSSIBLE` → no check is rolled

`TERRIBLE` is the lowest suitability that still permits a roll.
`IMPOSSIBLE` is rejected by Java mechanical plausibility validation with `ACTION_PHYSICALLY_IMPOSSIBLE` (see `AI_CONTRACTS.md`).

Poor ideas remain playable when physically possible.

## Body System

Body parts:
- `HEAD`
- `NECK`
- `CHEST`
- `ABDOMEN`
- `BACK`
- `LEFT_ARM`
- `RIGHT_ARM`
- `LEFT_LEG`
- `RIGHT_LEG`
- `HEART`

Severity states:
- `HEALTHY`
- `INJURED`
- `WOUNDED`
- `CRIPPLED`
- `DESTROYED`

Relevant injury adjustment:
- healthy/injured: normally 0
- wounded: about +1 DC
- crippled: about +3 DC
- destroyed: required use of that part is unavailable

Injuries affect relevant actions only; never globally lower a base stat.

Conditions:
- `BLEEDING`
- `FRACTURED`
- `BURNED`
- `POISONED`

HP represents overall ability to continue. Body-part state represents local functionality.

## Weapons

`DamageType`:
- `SLASHING`
- `PIERCING`
- `BLUNT`
- `FIRE`

The exact bundled weapon values (base damage, trauma, primary damage type) are defined in `src/main/resources/content/weapons.json` and documented in `CONTENT.md`. The notes below describe each weapon's intended character.

A weapon's primary damage type is content identity only. Which damage a particular attack method deals (for example an Ember Rod used as a physical striking tool) is deferred.

### Longsword
- primary type: `SLASHING`
- flexible attack/defense weapon

### Dagger
- primary type: `PIERCING`
- light, precise, short reach

### War Hammer
- primary type: `BLUNT`
- high local trauma, strong vs bone/structures

### Ember Rod
- primary type: `FIRE` (fire/arcane focus)
- base damage and trauma remain placeholder values pending balancing
- weak physical weapon, strong projected magical focus

Supported canonical attack methods:
- `SLASH`
- `THRUST`
- `SMASH`
- `HOOK`
- `POMMEL_STRIKE`
- `PROJECT`

Universal V1 attack templates:
- `THRUST`
- `HORIZONTAL_SWING`
- `LOW_SWEEP`
- `OVERHEAD_STRIKE`
- `QUICK_SLASH`
- `HEAVY_SMASH`
- `HOOK_AND_PULL`
- `PROJECTED_ATTACK`

## Attack Properties

Reusable properties:
- height: `LOW`, `MID`, `HIGH`, `OVERHEAD`
- width: `NARROW`, `NORMAL`, `WIDE`
- speed: `SLOW`, `NORMAL`, `FAST`
- force: `LIGHT`, `MODERATE`, `HEAVY`, `MASSIVE`
- reach: `SHORT`, `MEDIUM`, `LONG`
- commitment: `LOW`, `MEDIUM`, `HIGH`

Scalable properties use small ordered shifts; structural properties use direct overrides.

## Defense

Methods:
- `EVADE`
- `PARRY`
- `BLOCK`
- `BRACE`
- `TAKE_COVER`

Evade variants:
- `DUCK`
- `JUMP`
- `SIDESTEP`
- `RETREAT`
- `ROLL`
- `MOVE_FORWARD`
- `UNSPECIFIED`

Several defenses may be viable for the same attack. Suitability is derived from attack properties, equipment and situation.

`TAKE_COVER` is a `DefenseMethod` only: an immediate defensive response to an incoming attack.
Proactive movement toward cover is `MovementType.REPOSITION` with `relativeGoal = COVER`. `TAKE_COVER` is not a `MovementType`.

Parry contact points: `UNSPECIFIED`, `WEAPON`, `WEAPON_HEAD`, `SHAFT`, `BLADE`, `ATTACKING_ARM`.

How a defense is rolled and what gets through is described under Action Resolution.

## Action Vocabulary

The canonical vocabulary of the action contract (see `AI_CONTRACTS.md`). These values express intent only; how they are resolved is decided by Java action resolution.

Action types: `ATTACK`, `DEFEND`, `MOVE`, `INTERACT`, `OBSERVE`, `USE_ABILITY`, `USE_ITEM`, `COMMUNICATE`.

Step relations: `START`, `THEN`, `IF_PREVIOUS_SUCCEEDS`, `WHILE`.

Approach (the one dominant physical approach of an attack, movement or interaction): `NORMAL`, `PRECISE`, `FORCEFUL`, `CAUTIOUS`, `QUICK`, `STEALTHY`, `RUSHED`, `ACROBATIC`.

Attack purpose: `DAMAGE`, `DISARM`, `TRIP`, `PUSH`, `PULL`, `BREAK`, `IGNITE`, `EXTINGUISH`, `CREATE_DISTANCE`, `CLOSE_DISTANCE`, `REPOSITION`, `ESCAPE`, `RESTRAIN`, `DISTRACT`, `DEFEND`.

Attacks use the methods and templates listed under Weapons and always use an owned weapon. Unarmed attacks (punches, kicks) are not part of the current vocabulary; their method, template, damage and trauma are deferred.

Movement types: `ADVANCE`, `RETREAT`, `CLOSE_DISTANCE`, `REPOSITION`, `CIRCLE`, `CLIMB`, `DISENGAGE`, `HOLD_POSITION`. Relative goal: `NONE`, `COVER`.

| Intent | Representation |
|---|---|
| Move to a known zone | `ADVANCE` or `REPOSITION` with a zone target |
| Move through a known exit | `ADVANCE` with an exit target (`RETREAT` or `DISENGAGE` to back out or escape) |
| Reposition relative to an entity or object | `REPOSITION` or `CIRCLE` with that target |
| Close distance | `CLOSE_DISTANCE` with an entity or object target |
| Create distance | `RETREAT` |
| Move into cover | `REPOSITION` with goal `COVER`, optionally naming the covering object |
| Escape | `DISENGAGE` |

Movement vocabulary implies no range-band changes or movement costs; those belong to action resolution.

Interactions: `PUSH`, `PULL`, `BREAK`, `OPEN`, `CLOSE`, `PICK_UP`, `DROP`, `PLACE`, `JAM`, `IGNITE`, `EXTINGUISH`.

Observation: `SEARCH`, `INSPECT`, `LISTEN`, `WATCH`. (`TRACK` is not part of the MVP.)

Communication: `SAY`, `ASK`, `THREATEN`, `PERSUADE`, `DECEIVE`, `BARGAIN`.

## Position and Range

Range bands:
- `CONTACT`
- `CLOSE`
- `NEAR`
- `FAR`

Important temporary states include:
- `INSIDE_GUARD`
- `HIGH_GROUND`
- `BEHIND_COVER`
- `CORNERED`
- `PRONE`
- `OFF_BALANCE`
- `EXPOSED`

## Communication

There is deliberately no Charisma stat, and `COMMUNICATE` has no universal governing stat.
Social resolution is contextual: deterministic Java rules decide whether a check is needed and which factors matter. Detailed social resolution rules are deferred (see `DEFERRED_DECISIONS.md`).

## Damage

`ContactQuality` from attack resolution:
- critical success → `CLEAN`
- success → `SOLID`
- partial success → `GLANCING`
- failure → `NONE`

`ContactQuality` (how well the attack connected) and `ImpactSeverity` (how much local trauma resulted, see Trauma) are separate concepts and separate types, even though both contain a `GLANCING` and `SOLID` value.

HP formula:

`round(baseDamage × contactMultiplier × effectivenessMultiplier) - protection`

Final HP damage is `max(0, that value)`: protection never makes damage negative or heals.

Rounding is to the nearest whole HP; an exact half rounds up (all inputs are non-negative).
There is deliberately no minimum-one-damage rule: a glancing or ineffective hit may make contact and still deal 0 HP.

Contact multipliers:
- none 0 (no contact, so no damage)
- glancing 0.5
- solid 1.0
- clean 1.25

Effectiveness multipliers:
- `VERY_LOW` 0.25
- `LOW` 0.50
- `NORMAL` 1.0
- `HIGH` 1.25
- `VERY_HIGH` 1.5

The effectiveness levels and multipliers are defined here. How effectiveness is determined (weapon/damage type vs target anatomy, material or creature type) is deferred (see `DEFERRED_DECISIONS.md`).

Execution, impact and effectiveness are separate concepts.

### Protection

The MVP has no equippable armour system.
`protection` (and `traumaProtection` below) remain in the damage/trauma architecture for innate creature protection, environmental protection and future expansion.
Normal player protection is 0 in the MVP unless an explicitly implemented effect says otherwise.

## Trauma

Conceptual formula:

`weaponTrauma + contactModifier + existingInjury + anatomyInteraction + attackForm - traumaProtection - defensiveMitigation`

Contact trauma modifier:
- glancing -2
- solid 0
- clean +2

Existing injury modifier:
- healthy +0
- injured +1
- wounded +2
- crippled +3

The existing-injury modifier for a `DESTROYED` part is undefined (deferred). A defense against an attack aimed at a destroyed part is reported as mechanics-unavailable rather than guessed.

Trauma is only calculated when contact occurred. `ContactQuality.NONE` means no impact: there is no contact modifier, no trauma score and no `ImpactSeverity`.

When contact occurred, the raw trauma score is preserved as calculated (it may be negative after protection and mitigation). The effective score is `max(0, raw score)`, and the thresholds below apply to the effective score. There is no upper limit.

`ImpactSeverity` thresholds:
- 0–2 `GLANCING`
- 3–5 `SOLID`
- 6–8 `SEVERE`
- 9+ `DEVASTATING`

Severe/Devastating impacts may alter body severity and apply context-appropriate conditions.

Examples:
- severe blunt + bone + crushing form → fracture eligible;
- severe piercing/slashing + valid biological target + penetrating/deep-cut form → bleeding eligible;
- severe fire → burned eligible;
- poison requires toxin delivery rather than generic impact.

`DESTROYED` should be rare and generally require a severely damaged part plus devastating/catastrophic trauma.

## Action Resolution

Java resolves a validated `ActionIntent` into a `ResolvedOutcome` (see `ARCHITECTURE.md`). Every rolled check is a Stage 3 check (`d20 + stat modifier` vs final DC), and every hit uses the Damage and Trauma rules above; resolution never repeats that arithmetic.

### What resolves now

| Action | Rolled | Stat | Base DC | Result |
|---|---|---|---|---|
| `ATTACK` with purpose `DAMAGE` on a visible creature | yes | by weapon method (below) | the target's combat profile | contact, damage, trauma |
| `DEFEND` against the intent's incoming attack | yes | by defense method (below) | the incoming attack's difficulty | incoming contact, damage, trauma |
| `MOVE` (`ADVANCE`/`REPOSITION`) to a zone | no | — | — | automatic (see Movement below) |
| `MOVE` `HOLD_POSITION` | no | — | — | automatic success, no movement |
| `COMMUNICATE` | no | — | — | automatic success: the words were said, with no social consequence |

Everything else is a valid action whose mechanics are not designed yet. It is reported as **mechanics-unavailable**: no roll, no effect, and not a failure. This covers:
- attacks on objects or hazards, and attacks with any purpose other than `DAMAGE`;
- movement through exits, relative to entities or objects, or into cover, and `CLOSE_DISTANCE`, `RETREAT`, `CIRCLE`, `CLIMB` and `DISENGAGE`;
- `INTERACT`, `OBSERVE`, `USE_ABILITY` and `USE_ITEM`.

### Attack stat by weapon method

| Method | Stat | Basis (Core Stats) |
|---|---|---|
| `SLASH`, `THRUST`, `HOOK` | `AGILITY` | finesse attacks |
| `SMASH`, `POMMEL_STRIKE` | `MIGHT` | heavy physical actions |
| `PROJECT` | `ARCANA` | controlling supernatural force |

### Defense resolution

| Method | Stat | Basis (Core Stats) |
|---|---|---|
| `EVADE`, `PARRY`, `TAKE_COVER` | `AGILITY` | dodging, parries, reflexes |
| `BLOCK`, `BRACE` | `MIGHT` | blocks and bracing |

The DC is the difficulty the backend supplied with the incoming attack. The defender's degree decides how the attack connects:

| Defender's degree | Incoming contact |
|---|---|
| `CRITICAL_SUCCESS`, `SUCCESS` | `NONE` |
| `PARTIAL_SUCCESS` | `GLANCING` |
| `FAILURE` | `SOLID` |

Damage and trauma then use:
- the incoming attack's base damage, weapon trauma, effectiveness, attack form and anatomy interaction;
- the player's existing injury at the targeted part, or 0 when no part is targeted;
- player protection 0 and trauma protection 0.

Differences between defense methods, and defensive mitigation, are deferred.

An intent defends a given incoming attack at most once. A second defense after one has resolved is mechanics-unavailable; a cancelled or unavailable defense does not use the attack up. An incoming attack with no defending step is not resolved here, because the turn loop is deferred.

### Baselines

These are explicit placeholders, not designed values. Each check or calculation records them:
- **Suitability**: `FAIR` (0 DC) for every rolled check. No other DC adjustment is produced yet.
- **Attack-form modifier** for player attacks: 0.
- **Player protection and trauma protection**: 0 (see Protection).
- **Defensive mitigation** for the player: 0.

A player attack's DC, effectiveness, protection, trauma protection, existing injury, anatomy interaction and defensive mitigation all come from a backend-supplied combat profile for the exact target: the creature as a whole, or the named body part.
- A named part never falls back to another part or to the whole creature. Without a profile for the exact target, the attack is mechanics-unavailable.
- When no part is named, none is invented.
- Naming a part adds no targeting adjustment yet.

### Movement

- A zone joined to the player's current zone by a connection the player knows of (not hidden): automatic success, and the player moves.
- The current zone: automatic success, with no movement.
- Any other zone, including one joined only by an undiscovered passage: automatic failure, with no movement and nothing revealed.

Movement in an earlier step changes the zone that later steps start from.

### Multi-step intents

Steps resolve in order:
1. Once the player is down, every later step is **cancelled** (`PLAYER_DOWN`). A player already at 0 HP resolves nothing and draws no rolls.
2. A `WHILE` step is mechanics-unavailable, because simultaneous actions are not designed yet.
3. An `IF_PREVIOUS_SUCCEEDS` step runs only when the previous step resolved as a success: a critical success, a success or an automatic success. After a partial success, a failure, a cancelled step or an unavailable step, it is cancelled (`PREVIOUS_STEP_NOT_SUCCESSFUL`).
4. `START` and `THEN` steps always run.

The player is down once the damage from failed defenses in this intent reaches their current HP. Resolution reports this; it does not change HP itself.

### Overall result

1. If any step was cancelled because the player is down, the result is `INTERRUPTED`.
2. Otherwise, if no step resolved, it is `MECHANICS_UNAVAILABLE`.
3. Otherwise, if every step resolved as a success, it is `COMPLETE_SUCCESS`.
4. Otherwise, if every step that resolved is a failure, it is `FAILURE`.
5. Otherwise it is `PARTIAL_SUCCESS`.

### Randomness

Only checks draw randomness: one d20 per rolled step, in step order, from the generator the caller supplies. Automatic, cancelled and unavailable steps draw nothing, so the same intent, state and seed always resolve the same way.
