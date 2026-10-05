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

V1 weapons:

### Longsword
- primary type: slashing
- base damage ~6
- trauma ~3
- flexible attack/defense weapon

### Dagger
- primary type: piercing
- base damage ~4
- trauma ~2
- light, precise, short reach

### War Hammer
- primary type: blunt
- base damage ~7
- trauma ~6
- high local trauma, strong vs bone/structures

### Ember Rod
- fire/arcane focus
- base damage ~5 placeholder
- trauma ~3 placeholder
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

Contact multipliers:
- glancing 0.5
- solid 1.0
- clean 1.25

Effectiveness multipliers:
- `VERY_LOW` 0.25
- `LOW` 0.50
- `NORMAL` 1.0
- `HIGH` 1.25
- `VERY_HIGH` 1.5

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
