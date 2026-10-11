# Narrator benchmark

Prompt **A** is the production Outcome Narrator v5, unchanged. Prompt **B** is v5 with the DM-style guidance appended (`src/test/resources/narrator-benchmark/dm-style-addendum.txt`). Every request is the production request (`OpenAiProvider.textParams`, 400 max output tokens, `store=false`); every response goes through the production extraction (`OpenAiProvider.interpret`) and the narrator's acceptance rule (non-empty, at most 1200 characters).

Word counts are pacing guides, not targets. Automatic flags are prompts for review, not verdicts.

## Production compatibility

- gpt-4.1-mini: answered
- gpt-5.4-mini: answered with reasoning effort `none`
- gpt-5.4: answered with reasoning effort `none`

| Configuration | Displayed | Rejected or failed | Notes |
|---|---|---|---|
| gpt-4.1-mini · A (production v5) | 8/8 | 0 | — |
| gpt-4.1-mini · B (DM style) | 8/8 | 0 | — |
| gpt-5.4-mini · A (production v5) | 8/8 | 0 | — |
| gpt-5.4-mini · B (DM style) | 8/8 | 0 | — |
| gpt-5.4 · A (production v5) | 8/8 | 0 | — |
| gpt-5.4 · B (DM style) | 8/8 | 0 | — |

## Summary by model and prompt

| Configuration | Median latency | Mean latency | Mean input tok | Mean output tok | Mean reasoning tok | Mean words | Within pace guide | Review flags |
|---|---|---|---|---|---|---|---|---|
| gpt-4.1-mini · A (production v5) | 1629 ms | 1683 ms | 1764 | 99 | 0 | 79 | 2/8 | 12 |
| gpt-4.1-mini · B (DM style) | 1696 ms | 1971 ms | 2166 | 109 | 0 | 86 | 5/8 | 9 |
| gpt-5.4-mini · A (production v5) | 1377 ms | 1496 ms | 1763 | 80 | 0 | 62 | 3/8 | 10 |
| gpt-5.4-mini · B (DM style) | 1300 ms | 1367 ms | 2165 | 71 | 0 | 56 | 5/8 | 9 |
| gpt-5.4 · A (production v5) | 1828 ms | 1976 ms | 1763 | 87 | 0 | 69 | 4/8 | 10 |
| gpt-5.4 · B (DM style) | 2215 ms | 2164 ms | 2165 | 88 | 0 | 69 | 5/8 | 10 |

### Repetition within each configuration

- **gpt-4.1-mini · A (production v5)**: phrase "the central aisle" in 4 of 8; phrase "broken through the" in 3 of 8; phrase "cracked flagstones and" in 3 of 8; phrase "a wooden crate" in 3 of 8; phrase "west end of" in 3 of 8; phrase "behind you the" in 3 of 8
- **gpt-4.1-mini · B (DM style)**: phrase "out to the" in 3 of 8; phrase "the central aisle" in 3 of 8; phrase "behind you the" in 3 of 8; phrase "a gap broken" in 3 of 8; phrase "holy water stoup" in 3 of 8
- **gpt-5.4-mini · A (production v5)**: phrase "the west end" in 4 of 8; phrase "west end of" in 4 of 8; phrase "end of the" in 4 of 8; phrase "aisle a broad" in 3 of 8; phrase "the sagging west" in 3 of 8; phrase "the central aisle" in 3 of 8
- **gpt-5.4-mini · B (DM style)**: phrase "the central aisle" in 4 of 8; phrase "broken through the" in 3 of 8; phrase "a gap broken" in 3 of 8; phrase "aisle a broad" in 3 of 8; phrase "the chapel road" in 3 of 8; phrase "through the curved" in 3 of 8
- **gpt-5.4 · A (production v5)**: ends with "the last lantern" in 2 of 8; phrase "the central aisle" in 4 of 8; phrase "open length of" in 3 of 8; phrase "broken through the" in 3 of 8; phrase "back out to" in 3 of 8; phrase "out to the" in 3 of 8; phrase "wall of the" in 3 of 8
- **gpt-5.4 · B (DM style)**: phrase "broken through the" in 3 of 8; phrase "out to the" in 3 of 8; phrase "wall of the" in 3 of 8; phrase "the sagging west" in 3 of 8; phrase "the central aisle" in 3 of 8; phrase "the way to" in 3 of 8

### Words per scenario

| Scenario (pace guide) | gpt-4.1-mini · A (production v5) | gpt-4.1-mini · B (DM style) | gpt-5.4-mini · A (production v5) | gpt-5.4-mini · B (DM style) | gpt-5.4 · A (production v5) | gpt-5.4 · B (DM style) |
|---|---|---|---|---|---|---|
| 1. New major scene (100–180) | 68 | 83 | 74 | 67 | 72 | 89 |
| 2. New major scene (100–180) | 57 | 86 | 66 | 56 | 63 | 68 |
| 3. Short movement (15–45) | 62 | 51 | 65 | 41 | 28 | 33 |
| 4. Opening a container (10–35) | 72 | 52 | 9 | 14 | 22 | 15 |
| 5. Taking an item, with its look (15–50) | 41 | 38 | 20 | 20 | 25 | 26 |
| 6. Answering where to go (25–80) | 147 | 146 | 124 | 96 | 149 | 134 |
| 7. Repeated look, unchanged (5–25) | 146 | 175 | 105 | 114 | 153 | 150 |
| 8. Ordinary combat action (20–60) | 41 | 57 | 36 | 37 | 36 | 36 |

## Manual review

_To be completed after reading every narration against its facts: invented objects, discoveries, state changes, environmental properties or consequences, as distinct from harmless literary description of confirmed events._

## 1. Entering the Hollow Chapel for the first time

- Player wording: "I go in through the west doors"
- Produced by: seed 10, command `/move exit_1`
- Pace guide: New major scene, about 100–180 words

<details><summary>Confirmed facts (the exact narrator input)</summary>

```json
{
  "outcome" : {
    "mode" : "NORMAL",
    "currentZone" : "the west end of the nave",
    "overall" : "COMPLETE_SUCCESS",
    "facts" : [ {
      "fact" : "CrossedInto",
      "step" : 1,
      "attempt" : {
        "action" : "MOVE",
        "manner" : "ADVANCE",
        "template" : null,
        "approach" : "NORMAL",
        "purpose" : null,
        "targetKind" : "EXIT",
        "target" : "the road to the Hollow Chapel",
        "bodyPart" : null,
        "using" : null,
        "spokenWords" : null
      },
      "through" : "the Hollow Chapel's sagging west doors",
      "scene" : "Ruined Nave",
      "sceneDescription" : "The long body of the church, open to the sky where the roof has fallen in.",
      "arrival" : {
        "label" : "Nave Entrance",
        "phrase" : "the west end of the nave",
        "description" : "Cracked flagstones and a stone holy-water stoup, gone dry, just inside the doorway."
      },
      "behind" : "the sagging west doors you came in by, back out to the chapel road",
      "behindLeadsTo" : "the way to The Last Lantern"
    } ],
    "terminal" : null,
    "untrustedPlayerWording" : "I go in through the west doors"
  }
}
```
</details>

Production fallback (what the game shows when no narration is accepted):

> You pass through the Hollow Chapel's sagging west doors. The long body of the church, open to the sky where the roof has fallen in. Cracked flagstones and a stone holy-water stoup, gone dry, just inside the doorway. Behind you is the sagging west doors you came in by, back out to the chapel road, the way to The Last Lantern.

### gpt-4.1-mini · A (production v5)

> You pass through the Hollow Chapel's sagging west doors and arrive at the west end of the nave. The long church body stretches before you, its roof collapsed, leaving it open to the sky. Beneath your feet lie cracked flagstones, and nearby stands a dry stone holy-water stoup. Behind you, the sagging west doors lead back out to the chapel road and the way to The Last Lantern.

- Production: SUCCESS, displayed
- 1472 ms · input 1697 · output 84 · 68 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

> You push open the sagging west doors of the Hollow Chapel and step inside. The long body of the church stretches before you, open to the sky where the roof has tumbled away. At the west end of the nave, you stand on cracked flagstones beside a stone holy-water stoup, now bone dry. Behind you, the heavy doors lean crookedly, leading back out to the road toward The Last Lantern. The air here carries the weight of forgotten prayers beneath bruised, open skies.

- Production: SUCCESS, displayed
- 2874 ms · input 2099 · output 102 · 83 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

> You pass through the Hollow Chapel’s sagging west doors and come to the west end of the nave. Cracked flagstones lie underfoot beside a stone holy-water stoup, dry and idle just inside the doorway. Above you, the church’s long body stands open to the sky where the roof has fallen in. Behind you are the sagging west doors you came in by, back out to the chapel road, the way to The Last Lantern.

- Production: SUCCESS, displayed
- 1603 ms · input 1696 · output 95 · 74 words · effort none
- Review flags: none

### gpt-5.4-mini · B (DM style)

> You pass through the Hollow Chapel’s sagging west doors and stand at the west end of the nave, on cracked flagstones beside a stone holy-water stoup gone dry. Above you, the church’s long body opens to the sky where the roof has fallen in. Behind you are the sagging west doors you came in by, back out to the chapel road, the way to The Last Lantern.

- Production: SUCCESS, displayed
- 1300 ms · input 2098 · output 85 · 67 words · effort none
- Review flags: none

### gpt-5.4 · A (production v5)

> You pass through the Hollow Chapel's sagging west doors into the long body of the church, open to the sky where the roof has fallen in. You stand at the west end of the nave, on cracked flagstones beside a stone holy-water stoup gone dry just inside the doorway. Behind you hang the sagging west doors you came in by, back out to the chapel road, the way to The Last Lantern.

- Production: SUCCESS, displayed
- 1828 ms · input 1696 · output 89 · 72 words · effort none
- Review flags: none

### gpt-5.4 · B (DM style)

> You pass through the Hollow Chapel's sagging west doors and come to the west end of the nave. The church’s long body stretches before you, open to the sky where the roof has fallen away, the air bare and exposed above the ruin. Just inside the doorway, cracked flagstones lie underfoot, and beside them stands a stone holy-water stoup, long since gone dry.
> 
> At your back are the sagging west doors you came in by, leading back out to the chapel road and the way to The Last Lantern.

- Production: SUCCESS, displayed
- 2352 ms · input 2098 · output 111 · 89 words · effort none
- Review flags: none

## 2. Arriving in a different new scene: the Bell Passage

- Player wording: "I go through the low door behind the altar rail"
- Produced by: seed 10, command `/move exit_2`
- Pace guide: New major scene, about 100–180 words

<details><summary>Confirmed facts (the exact narrator input)</summary>

```json
{
  "outcome" : {
    "mode" : "NORMAL",
    "currentZone" : "the bell landing",
    "overall" : "COMPLETE_SUCCESS",
    "facts" : [ {
      "fact" : "CrossedInto",
      "step" : 1,
      "attempt" : {
        "action" : "MOVE",
        "manner" : "ADVANCE",
        "template" : null,
        "approach" : "NORMAL",
        "purpose" : null,
        "targetKind" : "EXIT",
        "target" : "an unexplored way",
        "bodyPart" : null,
        "using" : null,
        "spokenWords" : null
      },
      "through" : "a gap broken through the curved wall of the apse",
      "scene" : "Bell Passage",
      "sceneDescription" : "A climbing passage toward the bell tower, cold air spilling down from above.",
      "arrival" : {
        "label" : "Bell Landing",
        "phrase" : "the bell landing",
        "description" : "A small landing where a frayed bell rope hangs down the stairwell."
      },
      "behind" : "a stairwell leading down",
      "behindLeadsTo" : "the way to the Ruined Nave"
    } ],
    "terminal" : null,
    "untrustedPlayerWording" : "I go through the low door behind the altar rail"
  }
}
```
</details>

Production fallback (what the game shows when no narration is accepted):

> You pass through a gap broken through the curved wall of the apse. A climbing passage toward the bell tower, cold air spilling down from above. A small landing where a frayed bell rope hangs down the stairwell. Behind you is a stairwell leading down, the way to the Ruined Nave.

### gpt-4.1-mini · A (production v5)

> You move forward through a gap broken through the curved wall of the apse. You arrive at the bell landing, a small space where a frayed bell rope hangs down the stairwell. Above, cold air spills down from the climbing passage you just left. Behind you, the stairwell leads down to the way to the Ruined Nave.

- Production: SUCCESS, displayed
- 1556 ms · input 1675 · output 70 · 57 words
- Review flags: map label used as a name: "Bell Landing"

### gpt-4.1-mini · B (DM style)

> You push through a gap broken in the curved wall of the apse, stepping into a narrow climbing passage where cold air spills down from above. At its end lies the bell landing, a cramped, dimly lit platform crowned by a frayed bell rope hanging down the stairwell. Behind you, the stairwell descends steadily, leading back to the way to the Ruined Nave. The air here is chillier, carrying the faint echo of far-off ringing, as if the tower’s ancient bell waits just out of reach.

- Production: SUCCESS, displayed
- 1696 ms · input 2077 · output 108 · 86 words
- Review flags: map label used as a name: "Bell Landing"

### gpt-5.4-mini · A (production v5)

> You pass through a gap broken through the curved wall of the apse and come out onto the bell landing, a small landing where a frayed bell rope hangs down the stairwell. Behind you, a stairwell leads down to the way to the Ruined Nave. Cold air spills down from above, and the passage climbs toward the bell tower in a hush of stone and rope.

- Production: SUCCESS, displayed
- 1212 ms · input 1674 · output 81 · 66 words · effort none
- Review flags: map label used as a name: "Bell Landing"

### gpt-5.4-mini · B (DM style)

> You push through a gap broken through the curved wall of the apse and come onto the bell landing, a small perch in the climb where cold air spills down from above. A frayed bell rope hangs into the stairwell beside you, and behind you the stairwell leads down to the way to the Ruined Nave.

- Production: SUCCESS, displayed
- 1264 ms · input 2076 · output 69 · 56 words · effort none
- Review flags: map label used as a name: "Bell Landing"

### gpt-5.4 · A (production v5)

> You pass through a gap broken through the curved wall of the apse and come out into the bell landing, a small landing where a frayed bell rope hangs down the stairwell. Around you rises a climbing passage toward the bell tower, and cold air spills down from above. At your back, a stairwell leads down toward the way to the Ruined Nave.

- Production: SUCCESS, displayed
- 1542 ms · input 1674 · output 78 · 63 words · effort none
- Review flags: map label used as a name: "Bell Landing"

### gpt-5.4 · B (DM style)

> You pass through a gap broken through the curved wall of the apse and come out into the bell landing. The space is small and close, a little shelf in the climb, with a frayed bell rope hanging down the stairwell and cold air spilling steadily from above through the passage toward the tower.
> 
> At your back, a stairwell leads down toward the way to the Ruined Nave.

- Production: SUCCESS, displayed
- 2215 ms · input 2076 · output 83 · 68 words · effort none
- Review flags: map label used as a name: "Bell Landing"

## 3. Moving one zone within a known scene

- Player wording: "I walk up the central aisle"
- Produced by: seed 10, command `/move zone_1`
- Pace guide: Short movement, about 15–45 words

<details><summary>Confirmed facts (the exact narrator input)</summary>

```json
{
  "outcome" : {
    "mode" : "NORMAL",
    "currentZone" : "the central aisle",
    "overall" : "COMPLETE_SUCCESS",
    "facts" : [ {
      "fact" : "WalkedTo",
      "step" : 1,
      "attempt" : {
        "action" : "MOVE",
        "manner" : "REPOSITION",
        "template" : null,
        "approach" : "NORMAL",
        "purpose" : null,
        "targetKind" : "ZONE",
        "target" : "Central Aisle",
        "bodyPart" : null,
        "using" : null,
        "spokenWords" : null
      },
      "from" : {
        "label" : "Nave Entrance",
        "phrase" : "the west end of the nave",
        "description" : "Cracked flagstones and a stone holy-water stoup, gone dry, just inside the doorway."
      },
      "to" : {
        "label" : "Central Aisle",
        "phrase" : "the central aisle",
        "description" : "A broad aisle running the length of the nave, littered with fallen roof slates."
      },
      "passage" : "the open length of the nave"
    } ],
    "terminal" : null,
    "untrustedPlayerWording" : "I walk up the central aisle"
  }
}
```
</details>

Production fallback (what the game shows when no narration is accepted):

> You make your way through the open length of the nave to the central aisle. A broad aisle running the length of the nave, littered with fallen roof slates.

### gpt-4.1-mini · A (production v5)

> You step from the cracked flagstones and empty holy-water stoup at the west end of the nave into the central aisle. This broad aisle stretches before you, cluttered with fallen roof slates that speak of neglect and decay. The open expanse of the nave greets you, its breath thick with dust and shadow, but nothing blocks your path as you move forward.

- Production: SUCCESS, displayed
- 1970 ms · input 1668 · output 76 · 62 words
- Review flags: map label used as a name: "Central Aisle"

### gpt-4.1-mini · B (DM style)

> You step through the open length of the nave and enter the broad central aisle. The floor beneath you is strewn with fallen roof slates, crunching softly underfoot. This long stretch runs the full length of the nave, a path marred by decay, the stillness hanging heavy around the scattered debris.

- Production: SUCCESS, displayed
- 1467 ms · input 2070 · output 64 · 51 words
- Review flags: map label used as a name: "Central Aisle"

### gpt-5.4-mini · A (production v5)

> You walk through the open length of the nave and come to the central aisle, a broad aisle running the length of the nave, littered with fallen roof slates. Behind you lies the west end of the nave, where cracked flagstones and a stone holy-water stoup, gone dry, stood just inside the doorway. The ruin is quiet around you, the roof’s broken slate scattered underfoot.

- Production: SUCCESS, displayed
- 1377 ms · input 1667 · output 85 · 65 words · effort none
- Review flags: map label used as a name: "Central Aisle"

### gpt-5.4-mini · B (DM style)

> You walk through the open length of the nave and come to the central aisle, a broad run of stone stretched under the broken roof. Fallen slates lie scattered across the flagstones, and the old church hush presses close around you.

- Production: SUCCESS, displayed
- 1357 ms · input 2069 · output 51 · 41 words · effort none
- Review flags: map label used as a name: "Central Aisle"

### gpt-5.4 · A (production v5)

> You walk the open length of the nave and come to the central aisle, a broad stretch running the length of the church, littered with fallen roof slates.

- Production: SUCCESS, displayed
- 1326 ms · input 1667 · output 37 · 28 words · effort none
- Review flags: map label used as a name: "Central Aisle"

### gpt-5.4 · B (DM style)

> You make your way along the open length of the nave and come to the central aisle, a broad stretch running the length of the church, its floor littered with fallen roof slates.

- Production: SUCCESS, displayed
- 1483 ms · input 2069 · output 42 · 33 words · effort none
- Review flags: map label used as a name: "Central Aisle"

## 4. Opening the wooden crate (Restorative Salve inside)

- Player wording: "I open the wooden crate"
- Produced by: seed 10, command `/open object_1`
- Pace guide: Opening a container, about 10–35 words

<details><summary>Confirmed facts (the exact narrator input)</summary>

```json
{
  "outcome" : {
    "mode" : "NORMAL",
    "currentZone" : "the central aisle",
    "overall" : "COMPLETE_SUCCESS",
    "facts" : [ {
      "fact" : "ContainerOpened",
      "step" : 1,
      "attempt" : {
        "action" : "INTERACT",
        "manner" : "OPEN",
        "template" : null,
        "approach" : "NORMAL",
        "purpose" : null,
        "targetKind" : "OBJECT",
        "target" : "Crate",
        "bodyPart" : null,
        "using" : null,
        "spokenWords" : null
      },
      "name" : "Crate",
      "contents" : [ "Restorative Salve" ],
      "alreadyOpen" : false
    } ],
    "terminal" : null,
    "untrustedPlayerWording" : "I open the wooden crate"
  }
}
```
</details>

Production fallback (what the game shows when no narration is accepted):

> You open the Crate. Inside is a Restorative Salve.

### gpt-4.1-mini · A (production v5)

> You find yourself standing in the central aisle, a narrow path lined with shadowed shelves and relics. A wooden crate rests here, its lid firmly shut until now. With a steady effort, you pry it open, revealing the humble contents inside—a small jar of Restorative Salve, its scent faint but promising relief to wounds and weariness. The quiet air holds no other disturbances as you prepare to take what this crate offers.

- Production: SUCCESS, displayed
- 1629 ms · input 1597 · output 89 · 72 words
- Review flags: stock phrase: "you prepare"; long for its importance: 72 words (guide 10–35)

### gpt-4.1-mini · B (DM style)

> You pry open the wooden crate, its rough boards creaking against the strain. Inside, nestled against the splintered interior, lies a small jar of Restorative Salve. The air in the central aisle feels still around you, the faint scent of dust and ancient wood settling back as you consider the newfound remedy.

- Production: SUCCESS, displayed
- 1291 ms · input 1999 · output 66 · 52 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

> You open the crate, and inside lies Restorative Salve.

- Production: SUCCESS, displayed
- 985 ms · input 1596 · output 18 · 9 words · effort none
- Review flags: none

### gpt-5.4-mini · B (DM style)

> You pry open the crate in the central aisle. Inside lies a Restorative Salve.

- Production: SUCCESS, displayed
- 1021 ms · input 1998 · output 23 · 14 words · effort none
- Review flags: none

### gpt-5.4 · A (production v5)

> The crate yields under your hand and opens. Inside lies a Restorative Salve, waiting in the dim hush of the central aisle.

- Production: SUCCESS, displayed
- 1567 ms · input 1596 · output 32 · 22 words · effort none
- Review flags: none

### gpt-5.4 · B (DM style)

> The crate yields with a rough wooden scrape. Inside lies exactly one thing: Restorative Salve.

- Production: SUCCESS, displayed
- 1552 ms · input 1998 · output 25 · 15 words · effort none
- Review flags: none

## 5. Taking the salve and examining it

- Player wording: "I take the salve and examine it"
- Produced by: seed 10, command `/take object_1`
- Pace guide: Taking an item, with its look, about 15–50 words

<details><summary>Confirmed facts (the exact narrator input)</summary>

```json
{
  "outcome" : {
    "mode" : "NORMAL",
    "currentZone" : "the central aisle",
    "overall" : "COMPLETE_SUCCESS",
    "facts" : [ {
      "fact" : "TookItem",
      "step" : 1,
      "attempt" : {
        "action" : "INTERACT",
        "manner" : "PICK_UP",
        "template" : null,
        "approach" : "NORMAL",
        "purpose" : null,
        "targetKind" : "OBJECT",
        "target" : "Crate",
        "bodyPart" : null,
        "using" : null,
        "spokenWords" : null
      },
      "item" : "Restorative Salve",
      "description" : "A small stoppered clay vial of thick, bitter-smelling ointment.",
      "from" : "Crate"
    } ],
    "terminal" : null,
    "untrustedPlayerWording" : "I take the salve and examine it"
  }
}
```
</details>

Production fallback (what the game shows when no narration is accepted):

> You take the Restorative Salve from the Crate and tuck it into your belt. A small stoppered clay vial of thick, bitter-smelling ointment.

### gpt-4.1-mini · A (production v5)

> You reach into the crate and take out a small stoppered clay vial. The thick, bitter-smelling ointment within is a Restorative Salve, its scent sharp and medicinal. Holding it carefully, you consider the aid it might offer in the trials ahead.

- Production: SUCCESS, displayed
- 1213 ms · input 1614 · output 55 · 41 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

> You seize the small stoppered clay vial from the crate, its thick, bitter-smelling ointment firm and unyielding beneath the stopper. In your hand, the Restorative Salve sits ready—a promise of healing from the darkness that coils around you.

- Production: SUCCESS, displayed
- 1133 ms · input 2016 · output 54 · 38 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

> You take a small stoppered clay vial of thick, bitter-smelling ointment from the crate and set it on your belt.

- Production: SUCCESS, displayed
- 1250 ms · input 1613 · output 30 · 20 words · effort none
- Review flags: none

### gpt-5.4-mini · B (DM style)

> You lift a small stoppered clay vial of thick, bitter-smelling ointment from the crate and settle it on your belt.

- Production: SUCCESS, displayed
- 1147 ms · input 2015 · output 30 · 20 words · effort none
- Review flags: none

### gpt-5.4 · A (production v5)

> From the crate, you take the Restorative Salve and set it on your belt. It is a small stoppered clay vial of thick, bitter-smelling ointment.

- Production: SUCCESS, displayed
- 1307 ms · input 1613 · output 40 · 25 words · effort none
- Review flags: none

### gpt-5.4 · B (DM style)

> From the crate, you take the Restorative Salve and set it on your belt. It is a small stoppered clay vial filled with thick, bitter-smelling ointment.

- Production: SUCCESS, displayed
- 1581 ms · input 2015 · output 41 · 26 words · effort none
- Review flags: none

## 6. Asking which unexplored ways remain

- Player wording: "Is there a way I haven't gone?"
- Produced by: seed 10, command `/search`
- Pace guide: Answering where to go, about 25–80 words

<details><summary>Confirmed facts (the exact narrator input)</summary>

```json
{
  "outcome" : {
    "mode" : "NORMAL",
    "currentZone" : "the central aisle",
    "overall" : "COMPLETE_SUCCESS",
    "facts" : [ {
      "fact" : "SoughtWays",
      "step" : 1,
      "attempt" : {
        "action" : "OBSERVE",
        "manner" : "SEARCH",
        "template" : null,
        "approach" : null,
        "purpose" : null,
        "targetKind" : null,
        "target" : null,
        "bodyPart" : null,
        "using" : null,
        "spokenWords" : null
      },
      "perception" : {
        "here" : {
          "label" : "Central Aisle",
          "phrase" : "the central aisle",
          "description" : "A broad aisle running the length of the nave, littered with fallen roof slates."
        },
        "beside" : [ {
          "place" : {
            "label" : "Apse",
            "phrase" : "the apse",
            "description" : "A rounded east end beneath a cracked half-dome, its plaster saints worn faceless."
          },
          "passage" : "three worn steps up to the east end"
        }, {
          "place" : {
            "label" : "Collapsed Pews",
            "phrase" : "the collapsed pews",
            "description" : "Rows of pews crushed flat under a fallen roof beam, splinters everywhere."
          },
          "passage" : "a gap between the broken pew rows"
        }, {
          "place" : {
            "label" : "Nave Entrance",
            "phrase" : "the west end of the nave",
            "description" : "Cracked flagstones and a stone holy-water stoup, gone dry, just inside the doorway."
          },
          "passage" : "the open length of the nave"
        } ],
        "things" : [ {
          "name" : "Crate",
          "description" : "A wooden crate with a loose lid, the kind used to store church candles.",
          "where" : "here",
          "here" : true,
          "state" : "open and empty"
        }, {
          "name" : "Wooden Pew",
          "description" : "A heavy wooden pew, its back carved with worn saints.",
          "where" : "the collapsed pews",
          "here" : false,
          "state" : null
        } ],
        "hazards" : [ {
          "name" : "Unstable Ceiling",
          "description" : "Cracked ceiling stones, sagging and ready to fall.",
          "where" : "here",
          "here" : true,
          "state" : null
        } ],
        "creatures" : [ ],
        "ways" : [ {
          "leadsTo" : "an unexplored way",
          "passage" : "a low door behind where the altar rail once stood",
          "where" : "the apse",
          "here" : false
        }, {
          "leadsTo" : "an unexplored way",
          "passage" : "a gap broken through the curved wall of the apse",
          "where" : "the apse",
          "here" : false
        }, {
          "leadsTo" : "the way to The Last Lantern",
          "passage" : "the sagging west doors you came in by, back out to the chapel road",
          "where" : "the west end of the nave",
          "here" : false
        } ]
      },
      "unexplored" : [ {
        "what" : "a low door behind where the altar rail once stood",
        "where" : "the apse",
        "steps" : 1,
        "via" : null,
        "leadsTo" : "an unexplored way"
      }, {
        "what" : "a gap broken through the curved wall of the apse",
        "where" : "the apse",
        "steps" : 1,
        "via" : null,
        "leadsTo" : "an unexplored way"
      } ],
      "unvisited" : [ {
        "what" : "the apse",
        "where" : "the apse",
        "steps" : 1,
        "via" : null,
        "leadsTo" : null
      }, {
        "what" : "the collapsed pews",
        "where" : "the collapsed pews",
        "steps" : 1,
        "via" : null,
        "leadsTo" : null
      } ],
      "visited" : [ "the west end of the nave" ],
      "known" : [ {
        "what" : "the sagging west doors you came in by, back out to the chapel road",
        "where" : "the west end of the nave",
        "steps" : 1,
        "via" : null,
        "leadsTo" : "the way to The Last Lantern"
      } ],
      "visitsKnown" : true,
      "nothingKnownLeft" : false
    } ],
    "terminal" : null,
    "untrustedPlayerWording" : "Is there a way I haven't gone?"
  }
}
```
</details>

Production fallback (what the game shows when no narration is accepted):

> A broad aisle running the length of the nave, littered with fallen roof slates. Three worn steps up to the east end leads to the apse. A gap between the broken pew rows leads to the collapsed pews. The open length of the nave leads to the west end of the nave. A crate is here, open and empty. A wooden pew is in the collapsed pews. Cracked ceiling stones, sagging and ready to fall here. A low door behind where the altar rail once stood, reached from the apse: an unexplored way. A gap broken through the curved wall of the apse, reached from the apse: an unexplored way. The sagging west doors you came in by, back out to the chapel road, reached from the west end of the nave: the way to The Last Lantern. Not yet visited: the apse; and the collapsed pews. Already walked: the west end of the nave.

### gpt-4.1-mini · A (production v5)

> You stand in the central aisle, a broad stretch running the length of the nave, strewn with fallen roof slates. Above, cracked ceiling stones sag ominously, threatening collapse. Nearby, a wooden crate lies open and empty at your feet. To the east, three worn steps lead up to the apse, its rounded end beneath a cracked half-dome where plaster saints stand worn and faceless. Between rows of pews crushed flat by a fallen roof beam, splinters scatter the ground. Behind you, the sagging west doors mark the way back out to the chapel road.
> 
> You see two unexplored ways from the apse itself—a low door behind where the altar rail once stood and a gap broken through the curved wall. The collapsed pews and the apse remain unvisited, while the west end of the nave is behind you, already known ground. Several ways lie before you, waiting.

- Production: SUCCESS, displayed
- 2278 ms · input 2211 · output 186 · 147 words
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"; long for its importance: 147 words (guide 25–80)

### gpt-4.1-mini · B (DM style)

> You stand in the central aisle, a broad swath running beneath a roof pocked with fallen slates. Cracked ceiling stones sag overhead, threatening collapse. Nearby, a wooden crate lies open but empty, and beyond, crushed rows of pews squatted under a heavy fallen beam, their wood splintered. To your east, three worn steps lead up to the apse, crowned by a cracked half-dome and plaster saints with faces worn away. The apse conceals two unexplored paths: a low door behind where the altar rail once stood, and a gap broken through its curved wall. To the west stretches the nave you entered, marked by cracked flagstones and a dry holy-water stoup, with sagging doors that lead out to the chapel road. These unexplored ways at the apse remain at your disposal; the west exit is well known, but the apse still holds doors awaiting your passage.

- Production: SUCCESS, displayed
- 2277 ms · input 2613 · output 189 · 146 words
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; long for its importance: 146 words (guide 25–80)

### gpt-5.4-mini · A (production v5)

> You stand in the central aisle, a broad run of stone littered with fallen roof slates, with cracked ceiling stones hanging above like a threat. To one side lie the apse, three worn steps up to the east end; the collapsed pews, reached by a gap between the broken rows; and the west end of the nave with its dry stoup and cracked flagstones. A wooden crate here stands open and empty, while a heavy pew lies beyond in the collapsed pews. You can see a low door behind where the altar rail once stood and a gap broken through the curved wall of the apse, both still unexplored. The sagging west doors are known to you, leading back out to the chapel road.

- Production: SUCCESS, displayed
- 2694 ms · input 2210 · output 152 · 124 words · effort none
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"; long for its importance: 124 words (guide 25–80)

### gpt-5.4-mini · B (DM style)

> You stand in the central aisle, a broad run of stone strewn with fallen roof slates. Ahead, three worn steps rise to the apse, where a low door behind where the altar rail once stood and a gap broken through the curved wall both offer unexplored ways. The collapsed pews lie to one side, crushed under a fallen beam; the west end of the nave is behind you, back toward the chapel road. A wooden crate with a loose lid sits here, open and empty, while cracked ceiling stones hang above, sagging and ready to fall.

- Production: SUCCESS, displayed
- 1915 ms · input 2612 · output 119 · 96 words · effort none
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"

### gpt-5.4 · A (production v5)

> From the central aisle, you take stock of the church. The ways not yet tried lie at the apse: a low door behind where the altar rail once stood, and a gap broken through the curved wall of the apse. You have not yet stood in the apse or among the collapsed pews. Behind you, at the west end of the nave, the sagging west doors lead back out to the chapel road.
> 
> Around you, fallen roof slates litter the broad aisle. Three worn steps rise to the apse; a gap between the broken pew rows leads toward the collapsed pews; the open length of the nave runs to the west end. Here within reach sits a wooden crate, open and empty. In the collapsed pews stands a heavy wooden pew with worn saints carved along its back. Overhead, the ceiling stones are cracked and sagging, ready to fall.

- Production: SUCCESS, displayed
- 2949 ms · input 2210 · output 184 · 149 words · effort none
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"; long for its importance: 149 words (guide 25–80)

### gpt-5.4 · B (DM style)

> From the central aisle, the useful paths are clear enough. To the east, in the apse, there are two ways you have not taken: a low door behind where the altar rail once stood, and a gap broken through the curved wall of the apse. You have not yet stood in the apse itself, nor among the collapsed pews. The one way already known leads back from the west end of the nave through the sagging west doors you came in by, out to the chapel road.
> 
> Around you, the nave shows its ruin plainly: fallen roof slates underfoot, an open and empty wooden crate close at hand, a heavy wooden pew visible over in the collapsed pews, and above you an unstable ceiling of cracked stone, sagging as if it might give way.

- Production: SUCCESS, displayed
- 2876 ms · input 2612 · output 165 · 134 words · effort none
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"; long for its importance: 134 words (guide 25–80)

## 7. Looking around again when nothing has changed

- Player wording: "I look around again"
- Produced by: seed 10, command `/watch`
- Pace guide: Repeated look, unchanged, about 5–25 words

<details><summary>Confirmed facts (the exact narrator input)</summary>

```json
{
  "outcome" : {
    "mode" : "NORMAL",
    "currentZone" : "the central aisle",
    "overall" : "COMPLETE_SUCCESS",
    "facts" : [ {
      "fact" : "Perceived",
      "step" : 1,
      "attempt" : {
        "action" : "OBSERVE",
        "manner" : "WATCH",
        "template" : null,
        "approach" : null,
        "purpose" : null,
        "targetKind" : null,
        "target" : null,
        "bodyPart" : null,
        "using" : null,
        "spokenWords" : null
      },
      "perception" : {
        "here" : {
          "label" : "Central Aisle",
          "phrase" : "the central aisle",
          "description" : "A broad aisle running the length of the nave, littered with fallen roof slates."
        },
        "beside" : [ {
          "place" : {
            "label" : "Apse",
            "phrase" : "the apse",
            "description" : "A rounded east end beneath a cracked half-dome, its plaster saints worn faceless."
          },
          "passage" : "three worn steps up to the east end"
        }, {
          "place" : {
            "label" : "Collapsed Pews",
            "phrase" : "the collapsed pews",
            "description" : "Rows of pews crushed flat under a fallen roof beam, splinters everywhere."
          },
          "passage" : "a gap between the broken pew rows"
        }, {
          "place" : {
            "label" : "Nave Entrance",
            "phrase" : "the west end of the nave",
            "description" : "Cracked flagstones and a stone holy-water stoup, gone dry, just inside the doorway."
          },
          "passage" : "the open length of the nave"
        } ],
        "things" : [ {
          "name" : "Crate",
          "description" : "A wooden crate with a loose lid, the kind used to store church candles.",
          "where" : "here",
          "here" : true,
          "state" : "open and empty"
        }, {
          "name" : "Wooden Pew",
          "description" : "A heavy wooden pew, its back carved with worn saints.",
          "where" : "the collapsed pews",
          "here" : false,
          "state" : null
        } ],
        "hazards" : [ {
          "name" : "Unstable Ceiling",
          "description" : "Cracked ceiling stones, sagging and ready to fall.",
          "where" : "here",
          "here" : true,
          "state" : null
        } ],
        "creatures" : [ ],
        "ways" : [ {
          "leadsTo" : "an unexplored way",
          "passage" : "a low door behind where the altar rail once stood",
          "where" : "the apse",
          "here" : false
        }, {
          "leadsTo" : "an unexplored way",
          "passage" : "a gap broken through the curved wall of the apse",
          "where" : "the apse",
          "here" : false
        }, {
          "leadsTo" : "the way to The Last Lantern",
          "passage" : "the sagging west doors you came in by, back out to the chapel road",
          "where" : "the west end of the nave",
          "here" : false
        } ]
      },
      "unchanged" : true
    } ],
    "terminal" : null,
    "untrustedPlayerWording" : "I look around again"
  }
}
```
</details>

Production fallback (what the game shows when no narration is accepted):

> Nothing has changed around you.

### gpt-4.1-mini · A (production v5)

> You stand in the central aisle, beneath a cracked ceiling that seems ready to fall. Fallen roof slates cover the broad pathway stretching before you. To the east, three worn steps lead up to the apse beneath a cracked half-dome where faceless plaster saints stare blankly. Nearby, a wooden crate used for storing church candles sits open and empty right here. To the side, a gap between broken pew rows reveals pews crushed flat under a fallen roof beam, their carved backs still faintly visible. Behind the altar rail in the apse, a low door and a gap broken through the curved wall hint at unexplored ways forward. To the west is the nave entrance, marked by cracked flagstones and a dry holy-water stoup, leading back to the chapel road through sagging doors. The air is heavy with silence and the weight of ruin around you.

- Production: SUCCESS, displayed
- 2229 ms · input 2000 · output 177 · 146 words
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Nave Entrance"; long for its importance: 146 words (guide 5–25)

### gpt-4.1-mini · B (DM style)

> You stand in the central aisle, a wide stretch running the length of the nave, littered with fallen roof slates that crunch softly underfoot. To the east, three worn steps lead up into the apse, a rounded chamber beneath a cracked half-dome where plaster saints gaze with worn, faceless eyes. To the side, a gap between broken pew rows reveals the collapsed pews, their wooden backs carved with faded saints but crushed flat beneath a fallen roof beam, splinters scattered across the floor. Behind you, the nave’s west end marks the cracked flagstones and a dry stone holy-water stoup just inside the open doorway from which you entered. Near you sits a wooden crate with its lid loose, empty now, and overhead the ceiling stones groan, cracked and sagging, threatening collapse. From the apse, a low door behind the altar rail and a gap broken through the curved wall hint at unexplored passages. The sagging west doors stand as the way back out to the chapel road. Nothing has changed here since you last looked.

- Production: SUCCESS, displayed
- 3677 ms · input 2402 · output 217 · 175 words
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"; long for its importance: 175 words (guide 5–25)

### gpt-5.4-mini · A (production v5)

> You stand in the central aisle, a broad run of stone under a cracked roof, littered with fallen slates. To one side lie the apse with its worn, faceless saints, the collapsed pews split and splintered under a broken beam, and the west end of the nave by the doorway. A wooden crate with a loose lid sits here, open and empty. Above you, cracked ceiling stones sag, ready to drop. You can go by three worn steps up to the east end, by a gap between the broken pew rows, or back through the sagging west doors to the chapel road. Nothing has changed.

- Production: SUCCESS, displayed
- 1668 ms · input 1999 · output 133 · 105 words · effort none
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"; long for its importance: 105 words (guide 5–25)

### gpt-5.4-mini · B (DM style)

> You stand in the central aisle, a broad run of stone littered with fallen roof slates. Above you, cracked ceiling stones sag with the threat of a fall. Three worn steps climb to the apse beneath a cracked half-dome, where plaster saints have been worn faceless; beyond that lie a low door behind where the altar rail once stood and a gap broken through the curved wall. To one side, the collapsed pews lie crushed under a fallen roof beam, splinters everywhere. Near you sits a wooden crate with a loose lid, open and empty. Back the way you came are the sagging west doors, leading out to the chapel road. Nothing has changed.

- Production: SUCCESS, displayed
- 1779 ms · input 2401 · output 143 · 114 words · effort none
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"; long for its importance: 114 words (guide 5–25)

### gpt-5.4 · A (production v5)

> You stand in the central aisle, a broad run of nave strewn with fallen roof slates. Nothing has changed.
> 
> Three worn steps rise to the apse at the east end, where a cracked half-dome hangs over faceless saints. A gap between the broken pew rows leads into the collapsed pews. The open length of the nave runs back to the west end of the nave.
> 
> Within reach sits a wooden crate with a loose lid, already open and empty. In the collapsed pews, a heavy wooden pew lies under ruin. Overhead, the unstable ceiling sags with cracked stones ready to fall.
> 
> Beyond you, there is a low door behind where the altar rail once stood in the apse, and a gap broken through the curved wall of the apse. At the west end of the nave, the sagging west doors lead back out to the chapel road, the way to The Last Lantern.

- Production: SUCCESS, displayed
- 3219 ms · input 1999 · output 189 · 153 words · effort none
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"; long for its importance: 153 words (guide 5–25)

### gpt-5.4 · B (DM style)

> You stand in the central aisle, a broad run of flagging strewn with fallen roof slates. Close at hand sits a wooden crate, already open and empty, while above you the cracked ceiling sags with stones that look ready to come down.
> 
> To one side, a gap between the broken pew rows leads into the collapsed pews, where a heavy wooden pew lies among the wreckage. Ahead, three worn steps rise to the apse beneath its cracked half-dome. From there, you can make out two ways onward: a low door behind where the altar rail once stood, and a gap broken through the curved wall of the apse, both still unexplored. Behind you, the open length of the nave runs back to the west end, where the sagging west doors lead out to the chapel road and the way to The Last Lantern.
> 
> Nothing has changed since your last look.

- Production: SUCCESS, displayed
- 3393 ms · input 2401 · output 185 · 150 words · effort none
- Review flags: map label used as a name: "Central Aisle"; map label used as a name: "Apse"; map label used as a name: "Collapsed Pews"; long for its importance: 150 words (guide 5–25)

## 8. A confirmed combat action (a slash that lands)

- Player wording: "I slash at the hollow acolyte with my weapon"
- Produced by: seed 10, command `/attack entity_1 slash with weapon_1`, d20 [15]
- Pace guide: Ordinary combat action, about 20–60 words

<details><summary>Confirmed facts (the exact narrator input)</summary>

```json
{
  "outcome" : {
    "mode" : "NORMAL",
    "currentZone" : "the wall of skull niches",
    "overall" : "COMPLETE_SUCCESS",
    "facts" : [ {
      "fact" : "PlayerAttacked",
      "step" : 1,
      "attempt" : {
        "action" : "ATTACK",
        "manner" : "SLASH",
        "template" : "HORIZONTAL_SWING",
        "approach" : "NORMAL",
        "purpose" : "DAMAGE",
        "targetKind" : "CREATURE",
        "target" : "Hollow Acolyte",
        "bodyPart" : null,
        "using" : "Ember Rod",
        "spokenWords" : null
      },
      "weaponName" : "Ember Rod",
      "targetName" : "Hollow Acolyte",
      "bodyPart" : null,
      "contact" : "SOLID",
      "hpDamage" : 5,
      "impact" : "SOLID",
      "noContact" : false,
      "contactWithoutDamage" : false
    } ],
    "terminal" : null,
    "untrustedPlayerWording" : "I slash at the hollow acolyte with my weapon"
  }
}
```
</details>

Production fallback (what the game shows when no narration is accepted):

> Your Ember Rod lands a solid hit on the Hollow Acolyte, dealing 5 damage.

### gpt-4.1-mini · A (production v5)

> You swing the Ember Rod in a sharp horizontal arc at the Hollow Acolyte. The weapon strikes solidly, slicing into the creature and drawing a wound. The acolyte reels from the blow, its fragile form showing the impact of your attack.

- Production: SUCCESS, displayed
- 1114 ms · input 1647 · output 53 · 41 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

> You bring the Ember Rod down in a solid horizontal swing at the Hollow Acolyte. The blow lands true, cleaving into the creature and drawing forth a sharp impact that rends its hollow form. The Ember Rod slices through with a flame-tinged edge, the attack inflicting a significant wound that leaves the acolyte reeling from the damage.

- Production: SUCCESS, displayed
- 1352 ms · input 2049 · output 74 · 57 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

> You lash out with the Ember Rod in a hard horizontal sweep, striking the Hollow Acolyte solidly. The blow lands and bites for all its worth, the heat and force of it cutting through the dark.

- Production: SUCCESS, displayed
- 1179 ms · input 1646 · output 48 · 36 words · effort none
- Review flags: none

### gpt-5.4-mini · B (DM style)

> You swing the Ember Rod in a flat slash, and it bites solidly into the Hollow Acolyte. The blow lands clean and hard, five wounds worth of pain driven into the creature by the rod’s burning edge.

- Production: SUCCESS, displayed
- 1156 ms · input 2048 · output 50 · 37 words · effort none
- Review flags: none

### gpt-5.4 · A (production v5)

> You sweep the Ember Rod in a hard horizontal slash at the Hollow Acolyte. The blow lands solidly, wood or flesh meeting with a blunt, certain impact, and the strike bites deep enough to hurt it.

- Production: SUCCESS, displayed
- 2071 ms · input 1646 · output 49 · 36 words · effort none
- Review flags: none

### gpt-5.4 · B (DM style)

> You sweep the Ember Rod in a hard horizontal cut and it bites into the Hollow Acolyte with a solid crack of impact. The blow lands cleanly, jolting the creature under the force of the strike.

- Production: SUCCESS, displayed
- 1858 ms · input 2048 · output 48 · 36 words · effort none
- Review flags: none

