# Blind comparison

Six narrations per scenario, labelled A–F in a different order for each scenario, with no model or prompt names. The key is in `blind-key.md`.

---

## Scenario 1: Entering the Hollow Chapel for the first time

Player: "I go in through the west doors"

<details><summary>Confirmed facts</summary>

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

### A

_(no text: the game would show its fallback)_

### B

_(no text: the game would show its fallback)_

### C

_(no text: the game would show its fallback)_

### D

_(no text: the game would show its fallback)_

### E

_(no text: the game would show its fallback)_

### F

_(no text: the game would show its fallback)_

---

## Scenario 2: Arriving in a different new scene: the Bell Passage

Player: "I go through the low door behind the altar rail"

<details><summary>Confirmed facts</summary>

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

### A

_(no text: the game would show its fallback)_

### B

_(no text: the game would show its fallback)_

### C

_(no text: the game would show its fallback)_

### D

_(no text: the game would show its fallback)_

### E

_(no text: the game would show its fallback)_

### F

_(no text: the game would show its fallback)_

---

## Scenario 3: Moving one zone within a known scene

Player: "I walk up the central aisle"

<details><summary>Confirmed facts</summary>

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

### A

_(no text: the game would show its fallback)_

### B

_(no text: the game would show its fallback)_

### C

_(no text: the game would show its fallback)_

### D

_(no text: the game would show its fallback)_

### E

_(no text: the game would show its fallback)_

### F

_(no text: the game would show its fallback)_

---

## Scenario 4: Opening the wooden crate (Restorative Salve inside)

Player: "I open the wooden crate"

<details><summary>Confirmed facts</summary>

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

### A

_(no text: the game would show its fallback)_

### B

_(no text: the game would show its fallback)_

### C

_(no text: the game would show its fallback)_

### D

_(no text: the game would show its fallback)_

### E

_(no text: the game would show its fallback)_

### F

_(no text: the game would show its fallback)_

---

## Scenario 5: Taking the salve and examining it

Player: "I take the salve and examine it"

<details><summary>Confirmed facts</summary>

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

### A

_(no text: the game would show its fallback)_

### B

_(no text: the game would show its fallback)_

### C

_(no text: the game would show its fallback)_

### D

_(no text: the game would show its fallback)_

### E

_(no text: the game would show its fallback)_

### F

_(no text: the game would show its fallback)_

---

## Scenario 6: Asking which unexplored ways remain

Player: "Is there a way I haven't gone?"

<details><summary>Confirmed facts</summary>

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

### A

_(no text: the game would show its fallback)_

### B

_(no text: the game would show its fallback)_

### C

_(no text: the game would show its fallback)_

### D

_(no text: the game would show its fallback)_

### E

_(no text: the game would show its fallback)_

### F

_(no text: the game would show its fallback)_

---

## Scenario 7: Looking around again when nothing has changed

Player: "I look around again"

<details><summary>Confirmed facts</summary>

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

### A

_(no text: the game would show its fallback)_

### B

_(no text: the game would show its fallback)_

### C

_(no text: the game would show its fallback)_

### D

_(no text: the game would show its fallback)_

### E

_(no text: the game would show its fallback)_

### F

_(no text: the game would show its fallback)_

---

## Scenario 8: A confirmed combat action (a slash that lands)

Player: "I slash at the hollow acolyte with my weapon"

<details><summary>Confirmed facts</summary>

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

### A

_(no text: the game would show its fallback)_

### B

_(no text: the game would show its fallback)_

### C

_(no text: the game would show its fallback)_

### D

_(no text: the game would show its fallback)_

### E

_(no text: the game would show its fallback)_

### F

_(no text: the game would show its fallback)_

