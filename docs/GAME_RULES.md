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
| `OBSERVE` (search, inspect, listen, watch) | no | — | — | automatic success: the player takes in what is already visible (below) |
| `MOVE` to an object (`REPOSITION`, `ADVANCE`, `CLOSE_DISTANCE`) | no | — | — | automatic: one step toward it (see Movement below) |
| `INTERACT` `OPEN` / `PICK_UP` on a container | no | — | — | automatic (see Containers below) |

Everything else is a valid action whose mechanics are not designed yet. It is reported as **mechanics-unavailable**: no roll, no effect, and not a failure. This covers:
- attacks on objects or hazards, and attacks with any purpose other than `DAMAGE`;
- movement relative to entities, or into cover, `CLOSE_DISTANCE` to anything but an object, and `RETREAT`, `CIRCLE`, `CLIMB` and `DISENGAGE`;
- `INTERACT` other than opening or taking from a container (including opening or taking from anything else), `USE_ABILITY` and `USE_ITEM`.

### Observation

Looking around, searching, inspecting, listening and watching resolve automatically, with no roll and no effect. An observation **reveals nothing hidden**. It describes only what is perceivable now (World Generation, PlayerSceneView): the zone, the zones joined to it, the creatures, objects and hazards in them, and the known ways out, each named only as far as the player knows where it leads. Searching for hidden content is not designed yet. Observation does not provoke an enemy (Enemy turns).

- **A general look** in the same turn as an arrival adds only what the arrival did not already say. A general look that sees exactly what the previous committed look saw is marked unchanged, and told briefly.
- **A look for somewhere to go** ("I look for a way I haven't gone", "where can I go from here?", "is there a way deeper?", or the `/search` command; recognised by Java from the player's words, `ExplorationQuestion`) is answered from what the player knows of the scene, after this turn's moves: the unexplored ways they know of (and where each leaves from, and which place to go through), the places they know but have not stood in, where they have already been, and the ways they have already used. When none is left, it says that they know of no way they have not tried, never that no other way exists. It never moves the player and reveals nothing hidden or unseen. Looking at one particular thing or way out stays a targeted inspection.
- **A targeted inspection** (of an object, a zone or a way out) always gives that thing's authored description, and a container's state (closed; open and what it holds), when it is perceivable. Out of sight, it says only that it cannot be made out from here. It never moves the player and is never replaced by a general look.

### Turns where nothing could happen

If no step of an intent reaches RESOLVED (every step was mechanics-unavailable or cancelled), nothing is committed: no turn number, no state change, no chronicle entry and no narration. The request is refused as `ACTION_NOT_SUPPORTED` with reason `NOT_POSSIBLE_YET` and a hint written by Java (`HintWriter`) from the known view, in place phrases rather than labels: where the player is, what is within reach and nearby, and the ways on. The player is told that no turn was spent. When what could not happen was reaching for something out of reach, the reason is `OUT_OF_REACH` instead, with where that thing is (Containers). A resolved step that fails (a missed attack, a failed defense, a blocked move) is gameplay, not a non-event: it commits as usual.

A `REPOSITION` or `ADVANCE` to the zone the player is already in is **idle** (`IdleSteps`). Resolution still counts it as a success, so a following `IF_PREVIOUS_SUCCEEDS` step runs as if the player had just arrived. But it is not meaningful. If an intent has no resolved step other than idle ones, nothing is committed, and the request is refused as `ACTION_NOT_SUPPORTED` with reason `ALREADY_THERE` and the same grounded hint. Holding position (`HOLD_POSITION`) is a deliberate wait and is never idle. An idle step never provokes an enemy.

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

- **Toward an object:** one move along the shortest path of visible connections through known zones toward the object's zone, never more; a longer approach takes one move per step. Already in its zone: success with no move. No known path: automatic failure, with no movement and nothing revealed.

Movement in an earlier step changes the zone that later steps start from.

A free-text journey is grounded before resolution: the destination is checked against the player's own words, and the one step needed to reach an exit in a connected zone is added (AI Contracts, Route grounding). **Crossing into another scene happens only when the player asks for it**; walking forward or approaching stays in the scene, taking at most one local step. Going to an object is one step for "toward"/"closer", and at most two for "go to" or "all the way", stopping early at a zone holding a living creature or a hazard. Commands are resolved exactly as typed.

After a step leaves the scene, the steps after it are cancelled, because they refer to the place left behind. The exception is an untargeted look around: it carries on in the new place, is described from what the player can see there, has no effect and draws no roll.

### Containers

Opening and taking are the only interactions designed. Nothing is rolled: this slice has no locks, stuck lids or forcing, so there is no DC to roll against.

| Action | Where | Result |
|---|---|---|
| `OPEN` a closed container | its zone | success: it opens, and what it holds is revealed and persisted |
| `OPEN` an open container | its zone | success, nothing changes |
| `OPEN` from another zone | — | cannot begin: unavailable `OUT_OF_REACH` (below) |
| `PICK_UP` (take) from an open container holding an item | its zone | success: the item moves to the first free tool-belt slot and leaves the container |
| `PICK_UP` from a closed container | its zone | failure `CLOSED` (opening is never implied) |
| `PICK_UP` from an empty container | its zone | failure `EMPTY` |
| `PICK_UP` with a full tool belt (5) | its zone | failure `NO_ROOM` |
| `PICK_UP` from another zone | — | cannot begin: unavailable `OUT_OF_REACH` (below) |
| `OPEN` or `PICK_UP` on anything else | — | mechanics-unavailable |

**Out of reach is not a failure.** Reaching for a container in another zone cannot physically begin, so the step is unavailable (`OUT_OF_REACH`), not failed:
- On its own (or with nothing else meaningful in the intent), the request is refused before anything is committed: `ACTION_NOT_SUPPORTED` with reason `OUT_OF_REACH`, a message saying where the thing is in the world's words ("The crate is in the vestment racks, out of reach from here. Go there first."), and the usual surroundings hint. No turn is spent and no enemy acts. The message uses only the player's known view: an object the player has not seen gets no name or place.
- After real progress in the same intent ("move toward the crate and see if I can open it" with the crate two passages away), the progress stands: the move commits with its normal consequences, and the open is told as the crate still being out of reach, never as an attempt. Where one step does put the crate in reach (the first find is one passage from the arrival), the same words open it in that turn.

Closed, empty and no room are resolved failures: the character is beside the container and engages with it. A failure commits like a blocked move. Taking an item is told from the item itself (its authored description), and any later step of the same turn sees the container as the take left it. "Take what's inside" targets the container. A container holds at most one item, so taking needs no item choice. Opening and taking are meaningful steps: an enemy may act after them as after any other step. Their effects (`ContainerOpened`, `ItemTaken`) are applied in the turn's mechanics transaction, together with the scene revision check, so a replayed request never opens or grants twice.

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

## Enemies

Enemy definitions are static content (see `CONTENT.md`). Each placed enemy gets its own mechanical state when the run's world is generated (see `WORLD_GENERATION.md`).

### Enemy stats

Enemies use the same five stats, each 3 to 10.
- **Normal enemies:** one draw picks a 27-point shape from the player's `SPECIALIZED` shapes followed by its `EXTREME` shapes, in catalogue order. The values are sorted from high to low and assigned along the enemy's authored stat priority. Every normal enemy therefore totals 27, its strongest stat follows its role, and its weakest stat is at least 3 below its strongest.
- **The boss** (`CHAPEL_GUARDIAN`) uses its fixed authored block: MIGHT 10, AGILITY 9, RESOLVE 8, PERCEPTION 5, ARCANA 4 (36). It draws nothing.

### Enemy HP

`maxHp = baseHp + (RESOLVE - 6)`, and an enemy starts at full HP with every body part `HEALTHY`. Base HP: acolyte 12, warden 20, penitent 14, guardian 40 (so 42 with RESOLVE 8).

### Defense DC (a player attacking an enemy)

`defenseDc = 10 + modifier(the enemy's authored defensive stat)`. The defensive stat is AGILITY for the acolyte and the penitent, and MIGHT for the warden and the guardian. The modifier is the check stat-modifier table.

### Enemy attack difficulty (the player defending)

`difficulty = 10 + modifier(the enemy's stat for the attack's method)`, using the same method-to-stat table as player attacks (Action Resolution). For example, the penitent's `PROJECT` ember bolt uses ARCANA, and its rod `SMASH` uses its weak MIGHT. Base damage and weapon trauma come from the enemy's weapon definition.

### Stage 12 baselines

These are explicit, replaceable placeholders, like the Action Resolution baselines:
- effectiveness `NORMAL`, both for player attacks on enemies and for enemy attacks on the player;
- enemy protection, trauma protection and defensive mitigation 0;
- anatomy interaction 0 in both directions;
- enemy attack-form modifier 0;
- an enemy attack names no body part: no hit location is invented.

### Combat profile of an enemy

A player attack on an enemy uses a profile for the exact target:
- the DC is the enemy's defense DC, and the baselines above supply everything except the existing injury;
- **whole enemy (no part named):** existing-injury modifier 0, as on the defense side;
- **a named part:** the existing-injury modifier of that part's current severity (Trauma);
- **a part the enemy's anatomy lacks, or a `DESTROYED` part:** there is no profile, so the attack is mechanics-unavailable without a roll. It never falls back to another part or the whole enemy.

### Enemy behaviour

Enemies decide by Java weighted utility, never by AI and never using Fated. An enemy considers its own state and its own recent choices only, never the player's hidden stats, passive, ability or inventory.

**Candidates:** each of the enemy's attack options, in authored order, then `HOLD`. Holding produces no attack. An enemy at 0 HP does not act. Movement, retreat, cover and enemy defense are not candidates yet.

**Final weight** = `max(0, base + trait + pressure - repetition)`, calculated once, in whole numbers:

| Part | Rule |
|---|---|
| Base | the option's weight; `HOLD` uses the enemy's hold weight |
| `AGGRESSIVE` | `HOLD` −10 |
| `CAUTIOUS` | `HOLD` +10 |
| Pressure | when **desperate** (`2 × currentHp <= maxHp`), `HOLD` + `5 × max(0, 7 − RESOLVE)`: RESOLVE 3 adds 20, 5 adds 10, 6 adds 5, 7 or more adds nothing |
| `RECKLESS` | ignores pressure |
| Repetition | the caller passes the enemy's recent choices. In the last 2, each occurrence of a candidate costs it 15 |
| `ADAPTIVE` | repetition costs 30 per occurrence |
| `RELENTLESS` | no repetition penalty |
| `OPPORTUNISTIC` | no effect yet: it needs an observable player weakness |

**Selection:** one draw of a whole number in `[0, total)`. The chosen candidate is the first whose running total exceeds the draw, so a candidate with weight 0 is never chosen. If every weight is 0, the enemy holds without a draw. A chosen attack becomes the incoming attack the player defends against (Action Resolution); the enemy subsystem never rolls the defense or computes damage.

Bundled base weights:

| Enemy | Weights |
|---|---|
| Acolyte | quick slash 55, stab 45, hold 0 (−10 for AGGRESSIVE, clamped to 0) |
| Warden | overhead smash 45, heavy smash 35, haft hook 20, hold 0 |
| Penitent | ember bolt 70, rod strike 20, hold 10 + 10 (CAUTIOUS) = 20 |
| Guardian | sweeping cut 40, overhead cleave 35, lunge 25, hold 0 |

## Turn Effects and the Encounter (Stage 14)

### Applying outcomes

`EffectApplier` turns the confirmed `ResolvedOutcome` effects into state. It never reads the player's text. Effects are applied exactly once, in the turn's locked mechanics transaction.
- **Enemy and player damage:** HP is reduced by the final damage and clamped at 0.
- **Impact severity** is reported to narration, but **never changes body-part severity**. Injury escalation, conditions, healing, loot and XP are deferred.
- **Moves** change the zone within the scene.
- **Leaving through an exit** moves the player to the destination scene, which is then marked discovered.

### Fallen enemies

- An enemy at 0 HP is **FALLEN**: it stays visible (creature condition `FALLEN`) but has no combat profile and never acts.
- An attack on it, including a later step on a target felled earlier in the same intent, is cancelled with `TARGET_DEFEATED` and no roll.
- It leaves no loot or objects.

### Enemy turns

**When an enemy acts.** At most one enemy acts after a committed turn, and only if all of these hold:
- the run is still ACTIVE;
- the player did not leave the scene;
- no attack is pending;
- the turn has at least one RESOLVED step that is **neither DEFEND nor OBSERVE**, and not idle (a move to the zone the player is already in).

So a defense-only or observation-only turn never provokes another attack, while "defend, then counterattack" or "look, then move" does. A turn in which nothing resolved lets no enemy act; it is not committed at all (Turns where nothing could happen).

**Who can act.** Only an enemy standing in the **player's zone after this turn's moves** may act. There is no enemy movement or attack range yet, so a creature in another zone (beside it, farther away, or not yet seen) cannot reach the player and never acts; nothing is revealed to explain an attack. Walking into an occupied zone is always possible (route grounding stops *in* the first zone holding a creature), and an enemy there may act in that same turn. An attack that is already pending stays pending and must still be defended, wherever the attacker now is.

**Which enemy acts.** The actor is chosen round-robin among those enemies, in entity-ID order:
- it is the next one after the persisted cursor, wrapping around;
- a cursor from another scene resets to the first;
- if the enemy the cursor names cannot act now (it is elsewhere), the next one by ID acts, or the first;
- HOLD advances the cursor like an attack.

The decision uses `EnemyBehavior` with the actor's two most recent choices and the turn's enemy random stream.

**When it attacks.** The attack is persisted as the run's pending attack together with its Java cue (`Incoming: ...`). It is never rerolled or re-telegraphed on reload.

**Known limitation.** Player attacks have no range check yet: the player can still attack any visible enemy in the scene, while enemies can only answer from the same zone. Range for both sides, enemy movement and ranged attacks belong to the combat-range work (see `DEFERRED_DECISIONS.md`).

### Mandatory defense

While an attack is pending:
- the turn's first step must be a DEFEND responding to it (`DEFENSE_REQUIRED` otherwise);
- that DEFEND must actually reach RESOLVED (`DEFENSE_NOT_RESOLVED` otherwise, and nothing from the turn is committed);
- a failed defense check is still RESOLVED: its damage applies and the attack is consumed;
- follow-up steps are allowed;
- there is no option to ignore an attack.

A rejected or stale request consumes no turn.

### Exits

- `ADVANCE` through a known exit in the player's current zone automatically succeeds and leaves the scene; from another zone it fails.
- Steps after leaving are cancelled with `LEFT_SCENE`.
- The player arrives in the destination's zone that holds its exit back to the origin.
- Enemies do not follow, and no enemy acts on the arrival turn.

### End of a run

- **DEAD:** the player's HP reaches 0.
- **VICTORIOUS:** the Chapel Guardian reaches 0 HP while the player lives.
- If both happen in one turn, death takes precedence.
- No reward is granted, and a finished run accepts no further turns.

### Turn randomness

Each turn draws from fixed streams derived from the run seed: domain 20 for the player's checks and domain 21 for the enemy's decision, both indexed by turn number. The character uses domain 10. The run seed comes from `SecureRandom` and is never exposed.
