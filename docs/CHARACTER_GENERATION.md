# Character Generation

## Generated Character

A run starts by creating a mechanical character in Java, then asking the introduction narrator to render a short background from those confirmed facts.

Conceptual fields:
- name
- five stats
- Fated
- max/current HP
- one starting passive
- one starting weapon
- one active ability for MVP
- one recovery item
- one utility tool
- five-slot tool belt
- body state
- narrative-safe derived traits

## Stat Budget

Five stats, total 27 points.
Each stat is between 3 and 10 inclusive.

Profiles:

### BALANCED — 20%
- max <= 7
- min >= 4
- max - min <= 2

### SPECIALIZED — 70%
- max <= 9
- max - min >= 3
- not balanced

### EXTREME — 10%
- at least one stat is 10

Generation should select from valid 27-point spreads satisfying the chosen profile, then randomly assign values across the five stats.

Spread selection:
1. choose a profile by the weights above;
2. choose one canonical shape (the five values as an unordered multiset, duplicates allowed) uniformly from the shapes matching that profile;
3. shuffle the shape's values across the five stats.

Selection is uniform over canonical shapes, not over already-ordered stat blocks. Choosing uniformly over ordered blocks would make shapes with more distinct values disproportionately common, because they have more permutations.

The three profiles are exhaustive and mutually exclusive over valid 27-point shapes.

A 10 should be genuinely uncommon and meaningful.

## Fated

Separate from stat points.
Range 0–5.
Suggested starting distribution:
- 0: 25%
- 1: 25%
- 2: 22%
- 3: 15%
- 4: 9%
- 5: 4%

Fated is not luck. Higher Fated increases unusual/consequential events, whether beneficial, harmful or mixed.

Keep Fated independent of the stat-profile roll.

Fated is visible to the player as a 0–5 character attribute. The internal Fated probabilities and how Fated influences generation remain hidden.

Qualitative narration bands may be:
- 0 `ORDINARY`
- 1–2 `TOUCHED`
- 3 `UNUSUAL`
- 4 `OMINOUS`
- 5 `DEEPLY_FATED`

## HP

Prototype starting point:
- base HP ~26
- Resolve contributes a small modifier using the same -3..+4 stat modifier scale
- expected ordinary range roughly 23–30

Resolve must not become the sole dominant survivability stat; its main identity remains composure/will.

## Starting Weapon

MVP pool:
- Longsword
- Dagger
- War Hammer
- Ember Rod

Use weighted randomness, not hard matching.
Suggested affinity direction:
- Longsword: Agility primary, Might secondary
- Dagger: Agility primary, Perception secondary
- War Hammer: Might primary, Resolve secondary
- Ember Rod: Arcana primary, Perception secondary

All valid weapons retain a non-zero base chance unless a true mechanical incompatibility exists.

## Loadout Coherence

Hidden generation variable:
- `LOW` ~20%
- `MEDIUM` ~60%
- `HIGH` ~20%

High coherence creates stronger synergy between stats and loadout.
Medium mixes synergy and sideways options.
Low permits unusual combinations but should never intentionally create unusable characters.

## Starting Passive

Exactly one.
Initial MVP pool:
- `LIGHT_FOOT`
- `IRON_GRIP`
- `GRAVE_SENSE`
- `CLEAR_MIND`
- `IMPROVISER`
- `ASH_TOUCHED`

Passives should not always reinforce the strongest stat. Prefer a mixture of:
- reinforcing;
- compensating;
- sideways/new-option passives.

## Starting Ability

Exactly one for MVP to exercise the ability system.
Initial pool:
- `STONEBLOOD`
- `EMBER_EDGE`
- `SHADOW_STEP`
- `WARDING_SIGIL`

Abilities use loose stat affinity, not hard class locking.
Prototype limited uses may be ~2 per run until balanced.

## Tool Belt

Capacity: 5 slots.
Start with:
- weapon (1 slot)
- one recovery item
- one utility tool
- two empty slots

Recovery pool:
- Bandage
- Restorative Salve

Utility pool:
- Rope
- Torch
- Crowbar
- Lockpicks

Avoid strong redundancy through mild weighting, not hard bans.

## Initial Body State

All normal body parts start `HEALTHY`.
Rare Fated starting modifications may later alter this, but should not be routine or purely punitive.

## Character Introduction

Generation flow:
1. create session/run;
2. generate stat profile and stat spread;
3. roll Fated;
4. calculate HP;
5. select weapon;
6. select passive;
7. select ability;
8. select starting items;
9. initialise body state;
10. apply rare valid Fated start modifications;
11. generate/select name;
12. persist character;
13. build `CharacterIntroductionContext`;
14. call narrator;
15. persist introduction;
16. present introduction + character sheet + first scene.

The introduction should be brief dark-fantasy prose, usually second person, using soft memories/background rather than binding world-state facts.
