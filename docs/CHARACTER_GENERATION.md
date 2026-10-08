# Character Generation

## Generated Character

A run starts by creating a mechanical character in Java, then asking the introduction narrator to render a short background from those confirmed facts.

Fields:
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
- narrative-safe derived traits (later)

### Model vs Generation Policy

The generated-character model enforces structural validity only:
- every field present, a non-blank name;
- max HP at least 1, and a new character at full health (current HP = max HP);
- every body part has exactly one severity;
- a starting tool belt holding exactly one weapon, one recovery item and one utility item.

The exact V1 generation policy (the HP formula, an all-healthy body, the Fated distribution, uniform selection) belongs to the generator, not the model. The model can therefore represent, for example, a non-healthy body part or a different max HP, leaving room for rare Fated starting modifications without a model change.

### Generated vs Persisted Character State

`GeneratedCharacter` is a creation result only. It requires a new character's shape: full health and the starting tool-belt composition.

`PlayerCharacterState` is the ongoing character state. It is what a persisted run stores and loads, and it is created from a `GeneratedCharacter` when a run is created. It enforces only structural validity:
- every field present, a non-blank name;
- `maxHp >= 1` and `0 <= currentHp <= maxHp`;
- a valid body (any severities) and a valid tool belt (any mix of up to 5 entries).

Loading never produces a `GeneratedCharacter`, so later states such as reduced HP or changed belt contents load normally.

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

V1 starting distribution:
- 0: 25%
- 1: 25%
- 2: 22%
- 3: 15%
- 4: 9%
- 5: 4%

Fated is drawn with one integer roll in `[0, 100)`:
- 0–24 → 0
- 25–49 → 1
- 50–71 → 2
- 72–86 → 3
- 87–95 → 4
- 96–99 → 5

Fated is independent of the stat-profile roll: the Fated rule takes no stat input.

Fated is not luck. Higher Fated increases unusual/consequential events, whether beneficial, harmful or mixed.

Fated is visible to the player as a 0–5 character attribute. The internal Fated probabilities and how Fated influences generation remain hidden.

Character generation stores only the numeric value. Fated's gameplay consequences are deferred (see `DEFERRED_DECISIONS.md`).

Qualitative narration bands (`FatedBand`). They are narration-only labels given to the Character Introduction Narrator, with no mechanical effect and no probabilities:
- 0 `ORDINARY`
- 1–2 `TOUCHED`
- 3 `UNUSUAL`
- 4 `OMINOUS`
- 5 `DEEPLY_FATED`

## HP

V1 starting maximum HP:

`maxHp = 26 + (Resolve - 6)`

Resolve 3 to 10 gives max HP 23 to 30. This is a character-generation rule of its own; it is not derived from the check stat-modifier table, even though the numbers currently line up.

A new character starts at full health: `currentHp = maxHp`.

Resolve must not become the sole dominant survivability stat; its main identity remains composure/will.

## Current Selection Policy

Starting content is selected from the static content catalogue (see `CONTENT.md`):
- one weapon from all weapons;
- one passive from all passives;
- one ability from all abilities;
- one recovery item from items with category `RECOVERY`;
- one utility item from items with category `UTILITY`;
- one name from the character-name pool.

Each choice is currently **uniform and independent** within its pool. This is a baseline: the weapon affinity, loadout coherence and redundancy weighting described below are designed intent that is **not yet applied**. Their actual weighting rules are deferred (see `DEFERRED_DECISIONS.md`).

Generation fails clearly if the supplied content cannot satisfy any of these pools.

## Starting Weapon

MVP pool:
- Longsword
- Dagger
- War Hammer
- Ember Rod

Designed intent (not yet applied): use weighted randomness, not hard matching.
Suggested affinity direction:
- Longsword: Agility primary, Might secondary
- Dagger: Agility primary, Perception secondary
- War Hammer: Might primary, Resolve secondary
- Ember Rod: Arcana primary, Perception secondary

All valid weapons retain a non-zero base chance unless a true mechanical incompatibility exists.

## Loadout Coherence

Designed intent (not yet applied). Hidden generation variable:
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

Designed intent (not yet applied): passives should not always reinforce the strongest stat. Prefer a mixture of:
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

Abilities use loose stat affinity, not hard class locking (not yet applied).
Prototype limited uses may be ~2 per run until balanced.

## Tool Belt

Capacity: 5 slots.
A new character starts with:
- weapon (1 slot)
- one recovery item (1 slot)
- one utility tool (1 slot)
- two empty slots

The passive and active ability do not occupy tool-belt slots.

Recovery pool:
- Bandage
- Restorative Salve

Utility pool:
- Rope
- Torch
- Crowbar
- Lockpicks

Designed intent (not yet applied): avoid strong redundancy through mild weighting, not hard bans.

## Name

The name is chosen uniformly from the curated pool in `src/main/resources/content/character-names.json` (see `CONTENT.md`). Adding a name is a JSON-only change.

## Initial Body State

All normal body parts start `HEALTHY`.
Rare Fated starting modifications may later alter this, but should not be routine or purely punitive.

## Reproducibility

Generation takes one caller-supplied random source and draws in this fixed order:
1. stat profile, shape and shuffle;
2. Fated;
3. weapon;
4. passive;
5. ability;
6. recovery item;
7. utility item;
8. name.

HP and the initial body state involve no randomness. Changing the draw order changes seeded results.

The same seed reproduces the same character only with the same game-rules and static-content version (see `CONTENT.md`).

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
10. apply rare valid Fated start modifications (deferred);
11. select name;
12. persist character;
13. build `CharacterIntroductionContext`;
14. call narrator;
15. persist introduction;
16. present introduction + character sheet + first scene.

Steps 2–9 and 11 are in-memory generation, and step 12 is `GameRunStore.createRun`. Steps 13–15 are `CharacterIntroductionService.introductionFor(runId)`. Presentation (step 16) belongs to a later stage.

### Introduction flow and persistence

1. If the run already has a stored introduction, it is returned unchanged. The provider is never called again, and reloading never changes the text.
2. Otherwise the persisted character is loaded, and a `CharacterIntroductionContext` is built from its confirmed facts and the fixed lore (`content/lore.json`). The context holds the name, the five stats, the Fated value and band, weapons, passive, ability and items.
3. The Character Introduction Narrator is called **with no database transaction open**. On any failure it uses the deterministic fallback introduction.
4. The text is inserted if absent (`character_introduction`, Flyway V5). The table stores the text, its source (`AI` or `FALLBACK`) and the prompt version. AI and fallback introductions are stored the same way.
5. If a concurrent request stored an introduction first, that stored introduction is returned, so every caller sees the same text.

The introduction is brief dark-fantasy prose in the second person. It uses soft memories and background, never binding world-state facts, extra mechanics, items, powers, obligations or required places (see `AI_CONTRACTS.md`).
