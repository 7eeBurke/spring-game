# Narrator benchmark

> **Dry run.** No model was called; the narrations below are placeholders. The scenarios, facts and report layout are real.

Prompt **A** is the production Outcome Narrator v5, unchanged. Prompt **B** is v5 with the DM-style guidance appended (`src/test/resources/narrator-benchmark/dm-style-addendum.txt`). Every request is the production request (`OpenAiProvider.textParams`, 400 max output tokens, `store=false`); every response goes through the production extraction (`OpenAiProvider.interpret`) and the narrator's acceptance rule (non-empty, at most 1200 characters).

Word counts are pacing guides, not targets. Automatic flags are prompts for review, not verdicts.

## Production compatibility

- gpt-4.1-mini: dry run, not called
- gpt-5.4-mini: dry run, not called
- gpt-5.4: dry run, not called

| Configuration | Displayed | Rejected or failed | Notes |
|---|---|---|---|
| gpt-4.1-mini · A (production v5) | 0/8 | 8 | #1 DRY_RUN, #2 DRY_RUN, #3 DRY_RUN, #4 DRY_RUN, #5 DRY_RUN, #6 DRY_RUN, #7 DRY_RUN, #8 DRY_RUN |
| gpt-4.1-mini · B (DM style) | 0/8 | 8 | #1 DRY_RUN, #2 DRY_RUN, #3 DRY_RUN, #4 DRY_RUN, #5 DRY_RUN, #6 DRY_RUN, #7 DRY_RUN, #8 DRY_RUN |
| gpt-5.4-mini · A (production v5) | 0/8 | 8 | #1 DRY_RUN, #2 DRY_RUN, #3 DRY_RUN, #4 DRY_RUN, #5 DRY_RUN, #6 DRY_RUN, #7 DRY_RUN, #8 DRY_RUN |
| gpt-5.4-mini · B (DM style) | 0/8 | 8 | #1 DRY_RUN, #2 DRY_RUN, #3 DRY_RUN, #4 DRY_RUN, #5 DRY_RUN, #6 DRY_RUN, #7 DRY_RUN, #8 DRY_RUN |
| gpt-5.4 · A (production v5) | 0/8 | 8 | #1 DRY_RUN, #2 DRY_RUN, #3 DRY_RUN, #4 DRY_RUN, #5 DRY_RUN, #6 DRY_RUN, #7 DRY_RUN, #8 DRY_RUN |
| gpt-5.4 · B (DM style) | 0/8 | 8 | #1 DRY_RUN, #2 DRY_RUN, #3 DRY_RUN, #4 DRY_RUN, #5 DRY_RUN, #6 DRY_RUN, #7 DRY_RUN, #8 DRY_RUN |

## Summary by model and prompt

| Configuration | Median latency | Mean latency | Mean input tok | Mean output tok | Mean reasoning tok | Mean words | Within pace guide | Review flags |
|---|---|---|---|---|---|---|---|---|
| gpt-4.1-mini · A (production v5) | 0 ms | 0 ms | — | — | — | 0 | 0/8 | 0 |
| gpt-4.1-mini · B (DM style) | 0 ms | 0 ms | — | — | — | 0 | 0/8 | 0 |
| gpt-5.4-mini · A (production v5) | 0 ms | 0 ms | — | — | — | 0 | 0/8 | 0 |
| gpt-5.4-mini · B (DM style) | 0 ms | 0 ms | — | — | — | 0 | 0/8 | 0 |
| gpt-5.4 · A (production v5) | 0 ms | 0 ms | — | — | — | 0 | 0/8 | 0 |
| gpt-5.4 · B (DM style) | 0 ms | 0 ms | — | — | — | 0 | 0/8 | 0 |

### Repetition within each configuration

- **gpt-4.1-mini · A (production v5)**: no repeated openings, closings or phrases
- **gpt-4.1-mini · B (DM style)**: no repeated openings, closings or phrases
- **gpt-5.4-mini · A (production v5)**: no repeated openings, closings or phrases
- **gpt-5.4-mini · B (DM style)**: no repeated openings, closings or phrases
- **gpt-5.4 · A (production v5)**: no repeated openings, closings or phrases
- **gpt-5.4 · B (DM style)**: no repeated openings, closings or phrases

### Words per scenario

| Scenario (pace guide) | gpt-4.1-mini · A (production v5) | gpt-4.1-mini · B (DM style) | gpt-5.4-mini · A (production v5) | gpt-5.4-mini · B (DM style) | gpt-5.4 · A (production v5) | gpt-5.4 · B (DM style) |
|---|---|---|---|---|---|---|
| 1. New major scene (100–180) | 0 | 0 | 0 | 0 | 0 | 0 |
| 2. New major scene (100–180) | 0 | 0 | 0 | 0 | 0 | 0 |
| 3. Short movement (15–45) | 0 | 0 | 0 | 0 | 0 | 0 |
| 4. Opening a container (10–35) | 0 | 0 | 0 | 0 | 0 | 0 |
| 5. Taking an item, with its look (15–50) | 0 | 0 | 0 | 0 | 0 | 0 |
| 6. Answering where to go (25–80) | 0 | 0 | 0 | 0 | 0 | 0 |
| 7. Repeated look, unchanged (5–25) | 0 | 0 | 0 | 0 | 0 | 0 |
| 8. Ordinary combat action (20–60) | 0 | 0 | 0 | 0 | 0 | 0 |

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

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
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

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

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

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

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

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
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

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
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

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

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

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

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

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-4.1-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4-mini · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · A (production v5)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

### gpt-5.4 · B (DM style)

_(no text)_

- Production: DRY_RUN, **not displayed** (the fallback would be shown)
- 0 ms · input — · output — · 0 words
- Review flags: none

