# MVP Scope

## Goal

Deliver one complete playable roguelike loop that proves the architecture:

new run
→ generated character
→ AI introduction
→ The Last Lantern hub (brief, skippable)
→ procedural Hollow Chapel
→ free-form exploration/combat
→ boss
→ victory or death
→ new run with meaningful variation

## Regions

Implement:
- hub: `THE_LAST_LANTERN` — a small fixed (non-procedural) hub scene between the introduction and Hollow Chapel; the player can leave almost immediately (see `WORLD_GENERATION.md`)
- one full procedural region: `HOLLOW_CHAPEL`

Do not implement other full regions yet.

## Scene Content

Normal scene archetypes:
1. Ruined Nave
2. Cloister
3. Sacristy
4. Ossuary
5. Bell Passage
6. Reliquary

Boss arena:
- Guardian Sanctum, with a few possible environmental layouts

## Enemies

Normal enemies:
- `HOLLOW_ACOLYTE`: fast/light melee, opportunistic/aggressive
- `BONE_WARDEN`: slow/heavy melee, high Might, hammer-focused
- `ASHBOUND_PENITENT`: cautious ranged/fire/Arcana enemy

Boss:
- `CHAPEL_GUARDIAN`

Enemy behaviour remains Java weighted utility.

## Weapons

- Longsword
- Dagger
- War Hammer
- Ember Rod

## Player Passives

- Light Foot
- Iron Grip
- Grave Sense
- Clear Mind
- Improviser
- Ash Touched

## Active Abilities

- Stoneblood
- Ember Edge
- Shadow Step
- Warding Sigil

## Recovery Items

- Bandage
- Restorative Salve

## Utility Tools

- Rope
- Torch
- Crowbar
- Lockpicks

## Environment Objects

Initial reusable definitions:
- Wooden Pew
- Stone Pillar
- Altar
- Wooden Door
- Chain
- Corpse
- optional generic Crate/Container if needed

## Hazards

- Unstable Ceiling
- Fire
- Pressure Plate
- Collapsing Floor

## Events

- Ferryman of Ash
- Wounded Pilgrim
- False Blessing
- Hidden Prayer Shard
- Reliquary Bargain

Initial Fated variations may include roughly three special cases such as:
- Bleeding Statue
- Broken Veteran enemy variant
- Cursed Relic

## Supported Player Vocabulary

Attack methods:
- slash, thrust, smash, hook, pommel strike, project

Defense:
- evade, parry, block, brace, take cover

Movement:
- advance, retreat, close distance, reposition (including toward cover), circle, climb, disengage, hold position

"Take cover" as an immediate response to an incoming attack is a defense (`TAKE_COVER`); moving toward cover proactively is `REPOSITION` with `relativeGoal = COVER`.

Observation:
- search, inspect, listen, watch

Interaction:
- push, pull, break, open, close, pick up, drop, place, jam, ignite, extinguish

Communication:
- say, ask, threaten, persuade, deceive, bargain

## Run Ending

Death:
- HP <= 0 ends the run;
- death narration via the Outcome Narrator (run-death mode) with deterministic fallback;
- show a run summary;
- new run generates a new character and new region instance.

Victory:
- defeat Chapel Guardian;
- receive `ASHEN_SIGIL`;
- short outro narrated by the Outcome Narrator (run-completion mode) with deterministic fallback;
- mark run complete.

## Persistence

Support one active run initially.
Persist run state in PostgreSQL so closing/reopening the client does not reset the run.

## Frontend

Keep simple:
- narrative area;
- free-form action input;
- submit action;
- collapsible character panel;
- tool belt;
- body state;
- current scene;
- optional mechanics/roll details.

No 3D/map-heavy UI required for MVP.

## AI Jobs

Only:
1. Action Interpreter
2. Outcome Narrator
3. Enemy Attack Narrator
4. Character Introduction Narrator

Run-ending narration (victory outro, death) is a mode of the Outcome Narrator, not an additional AI job.

## Explicitly Out of Scope

Do not add for MVP:
- multiplayer;
- classes;
- XP/levels;
- skill trees;
- meta progression;
- economy/shops;
- crafting;
- armour-equipment system;
- huge spell catalogue;
- quest system;
- companions;
- multiple complete procedural regions;
- procedural boss generation;
- AI-generated mechanics;
- grid combat;
- equipment durability;
- stamina/mana/hunger systems;
- day/night cycle;
- reputation;
- full NPC simulation;
- PvP.
