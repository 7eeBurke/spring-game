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

> You pass through the Hollow Chapel's sagging west doors into the long body of the church, open to the sky where the roof has fallen in. You stand at the west end of the nave, on cracked flagstones beside a stone holy-water stoup gone dry just inside the doorway. Behind you hang the sagging west doors you came in by, back out to the chapel road, the way to The Last Lantern.

### B

> You pass through the Hollow Chapel’s sagging west doors and stand at the west end of the nave, on cracked flagstones beside a stone holy-water stoup gone dry. Above you, the church’s long body opens to the sky where the roof has fallen in. Behind you are the sagging west doors you came in by, back out to the chapel road, the way to The Last Lantern.

### C

> You push open the sagging west doors of the Hollow Chapel and step inside. The long body of the church stretches before you, open to the sky where the roof has tumbled away. At the west end of the nave, you stand on cracked flagstones beside a stone holy-water stoup, now bone dry. Behind you, the heavy doors lean crookedly, leading back out to the road toward The Last Lantern. The air here carries the weight of forgotten prayers beneath bruised, open skies.

### D

> You pass through the Hollow Chapel's sagging west doors and come to the west end of the nave. The church’s long body stretches before you, open to the sky where the roof has fallen away, the air bare and exposed above the ruin. Just inside the doorway, cracked flagstones lie underfoot, and beside them stands a stone holy-water stoup, long since gone dry.
> 
> At your back are the sagging west doors you came in by, leading back out to the chapel road and the way to The Last Lantern.

### E

> You pass through the Hollow Chapel’s sagging west doors and come to the west end of the nave. Cracked flagstones lie underfoot beside a stone holy-water stoup, dry and idle just inside the doorway. Above you, the church’s long body stands open to the sky where the roof has fallen in. Behind you are the sagging west doors you came in by, back out to the chapel road, the way to The Last Lantern.

### F

> You pass through the Hollow Chapel's sagging west doors and arrive at the west end of the nave. The long church body stretches before you, its roof collapsed, leaving it open to the sky. Beneath your feet lie cracked flagstones, and nearby stands a dry stone holy-water stoup. Behind you, the sagging west doors lead back out to the chapel road and the way to The Last Lantern.

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

> You move forward through a gap broken through the curved wall of the apse. You arrive at the bell landing, a small space where a frayed bell rope hangs down the stairwell. Above, cold air spills down from the climbing passage you just left. Behind you, the stairwell leads down to the way to the Ruined Nave.

### B

> You pass through a gap broken through the curved wall of the apse and come out into the bell landing. The space is small and close, a little shelf in the climb, with a frayed bell rope hanging down the stairwell and cold air spilling steadily from above through the passage toward the tower.
> 
> At your back, a stairwell leads down toward the way to the Ruined Nave.

### C

> You pass through a gap broken through the curved wall of the apse and come out onto the bell landing, a small landing where a frayed bell rope hangs down the stairwell. Behind you, a stairwell leads down to the way to the Ruined Nave. Cold air spills down from above, and the passage climbs toward the bell tower in a hush of stone and rope.

### D

> You push through a gap broken through the curved wall of the apse and come onto the bell landing, a small perch in the climb where cold air spills down from above. A frayed bell rope hangs into the stairwell beside you, and behind you the stairwell leads down to the way to the Ruined Nave.

### E

> You push through a gap broken in the curved wall of the apse, stepping into a narrow climbing passage where cold air spills down from above. At its end lies the bell landing, a cramped, dimly lit platform crowned by a frayed bell rope hanging down the stairwell. Behind you, the stairwell descends steadily, leading back to the way to the Ruined Nave. The air here is chillier, carrying the faint echo of far-off ringing, as if the tower’s ancient bell waits just out of reach.

### F

> You pass through a gap broken through the curved wall of the apse and come out into the bell landing, a small landing where a frayed bell rope hangs down the stairwell. Around you rises a climbing passage toward the bell tower, and cold air spills down from above. At your back, a stairwell leads down toward the way to the Ruined Nave.

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

> You walk through the open length of the nave and come to the central aisle, a broad aisle running the length of the nave, littered with fallen roof slates. Behind you lies the west end of the nave, where cracked flagstones and a stone holy-water stoup, gone dry, stood just inside the doorway. The ruin is quiet around you, the roof’s broken slate scattered underfoot.

### B

> You make your way along the open length of the nave and come to the central aisle, a broad stretch running the length of the church, its floor littered with fallen roof slates.

### C

> You walk through the open length of the nave and come to the central aisle, a broad run of stone stretched under the broken roof. Fallen slates lie scattered across the flagstones, and the old church hush presses close around you.

### D

> You walk the open length of the nave and come to the central aisle, a broad stretch running the length of the church, littered with fallen roof slates.

### E

> You step from the cracked flagstones and empty holy-water stoup at the west end of the nave into the central aisle. This broad aisle stretches before you, cluttered with fallen roof slates that speak of neglect and decay. The open expanse of the nave greets you, its breath thick with dust and shadow, but nothing blocks your path as you move forward.

### F

> You step through the open length of the nave and enter the broad central aisle. The floor beneath you is strewn with fallen roof slates, crunching softly underfoot. This long stretch runs the full length of the nave, a path marred by decay, the stillness hanging heavy around the scattered debris.

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

> You open the crate, and inside lies Restorative Salve.

### B

> The crate yields with a rough wooden scrape. Inside lies exactly one thing: Restorative Salve.

### C

> You pry open the crate in the central aisle. Inside lies a Restorative Salve.

### D

> You pry open the wooden crate, its rough boards creaking against the strain. Inside, nestled against the splintered interior, lies a small jar of Restorative Salve. The air in the central aisle feels still around you, the faint scent of dust and ancient wood settling back as you consider the newfound remedy.

### E

> You find yourself standing in the central aisle, a narrow path lined with shadowed shelves and relics. A wooden crate rests here, its lid firmly shut until now. With a steady effort, you pry it open, revealing the humble contents inside—a small jar of Restorative Salve, its scent faint but promising relief to wounds and weariness. The quiet air holds no other disturbances as you prepare to take what this crate offers.

### F

> The crate yields under your hand and opens. Inside lies a Restorative Salve, waiting in the dim hush of the central aisle.

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

> From the crate, you take the Restorative Salve and set it on your belt. It is a small stoppered clay vial filled with thick, bitter-smelling ointment.

### B

> You seize the small stoppered clay vial from the crate, its thick, bitter-smelling ointment firm and unyielding beneath the stopper. In your hand, the Restorative Salve sits ready—a promise of healing from the darkness that coils around you.

### C

> You lift a small stoppered clay vial of thick, bitter-smelling ointment from the crate and settle it on your belt.

### D

> You take a small stoppered clay vial of thick, bitter-smelling ointment from the crate and set it on your belt.

### E

> From the crate, you take the Restorative Salve and set it on your belt. It is a small stoppered clay vial of thick, bitter-smelling ointment.

### F

> You reach into the crate and take out a small stoppered clay vial. The thick, bitter-smelling ointment within is a Restorative Salve, its scent sharp and medicinal. Holding it carefully, you consider the aid it might offer in the trials ahead.

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

> You stand in the central aisle, a broad stretch running the length of the nave, strewn with fallen roof slates. Above, cracked ceiling stones sag ominously, threatening collapse. Nearby, a wooden crate lies open and empty at your feet. To the east, three worn steps lead up to the apse, its rounded end beneath a cracked half-dome where plaster saints stand worn and faceless. Between rows of pews crushed flat by a fallen roof beam, splinters scatter the ground. Behind you, the sagging west doors mark the way back out to the chapel road.
> 
> You see two unexplored ways from the apse itself—a low door behind where the altar rail once stood and a gap broken through the curved wall. The collapsed pews and the apse remain unvisited, while the west end of the nave is behind you, already known ground. Several ways lie before you, waiting.

### B

> From the central aisle, the useful paths are clear enough. To the east, in the apse, there are two ways you have not taken: a low door behind where the altar rail once stood, and a gap broken through the curved wall of the apse. You have not yet stood in the apse itself, nor among the collapsed pews. The one way already known leads back from the west end of the nave through the sagging west doors you came in by, out to the chapel road.
> 
> Around you, the nave shows its ruin plainly: fallen roof slates underfoot, an open and empty wooden crate close at hand, a heavy wooden pew visible over in the collapsed pews, and above you an unstable ceiling of cracked stone, sagging as if it might give way.

### C

> You stand in the central aisle, a broad run of stone strewn with fallen roof slates. Ahead, three worn steps rise to the apse, where a low door behind where the altar rail once stood and a gap broken through the curved wall both offer unexplored ways. The collapsed pews lie to one side, crushed under a fallen beam; the west end of the nave is behind you, back toward the chapel road. A wooden crate with a loose lid sits here, open and empty, while cracked ceiling stones hang above, sagging and ready to fall.

### D

> You stand in the central aisle, a broad run of stone littered with fallen roof slates, with cracked ceiling stones hanging above like a threat. To one side lie the apse, three worn steps up to the east end; the collapsed pews, reached by a gap between the broken rows; and the west end of the nave with its dry stoup and cracked flagstones. A wooden crate here stands open and empty, while a heavy pew lies beyond in the collapsed pews. You can see a low door behind where the altar rail once stood and a gap broken through the curved wall of the apse, both still unexplored. The sagging west doors are known to you, leading back out to the chapel road.

### E

> You stand in the central aisle, a broad swath running beneath a roof pocked with fallen slates. Cracked ceiling stones sag overhead, threatening collapse. Nearby, a wooden crate lies open but empty, and beyond, crushed rows of pews squatted under a heavy fallen beam, their wood splintered. To your east, three worn steps lead up to the apse, crowned by a cracked half-dome and plaster saints with faces worn away. The apse conceals two unexplored paths: a low door behind where the altar rail once stood, and a gap broken through its curved wall. To the west stretches the nave you entered, marked by cracked flagstones and a dry holy-water stoup, with sagging doors that lead out to the chapel road. These unexplored ways at the apse remain at your disposal; the west exit is well known, but the apse still holds doors awaiting your passage.

### F

> From the central aisle, you take stock of the church. The ways not yet tried lie at the apse: a low door behind where the altar rail once stood, and a gap broken through the curved wall of the apse. You have not yet stood in the apse or among the collapsed pews. Behind you, at the west end of the nave, the sagging west doors lead back out to the chapel road.
> 
> Around you, fallen roof slates litter the broad aisle. Three worn steps rise to the apse; a gap between the broken pew rows leads toward the collapsed pews; the open length of the nave runs to the west end. Here within reach sits a wooden crate, open and empty. In the collapsed pews stands a heavy wooden pew with worn saints carved along its back. Overhead, the ceiling stones are cracked and sagging, ready to fall.

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

> You stand in the central aisle, a broad run of nave strewn with fallen roof slates. Nothing has changed.
> 
> Three worn steps rise to the apse at the east end, where a cracked half-dome hangs over faceless saints. A gap between the broken pew rows leads into the collapsed pews. The open length of the nave runs back to the west end of the nave.
> 
> Within reach sits a wooden crate with a loose lid, already open and empty. In the collapsed pews, a heavy wooden pew lies under ruin. Overhead, the unstable ceiling sags with cracked stones ready to fall.
> 
> Beyond you, there is a low door behind where the altar rail once stood in the apse, and a gap broken through the curved wall of the apse. At the west end of the nave, the sagging west doors lead back out to the chapel road, the way to The Last Lantern.

### B

> You stand in the central aisle, a broad run of stone under a cracked roof, littered with fallen slates. To one side lie the apse with its worn, faceless saints, the collapsed pews split and splintered under a broken beam, and the west end of the nave by the doorway. A wooden crate with a loose lid sits here, open and empty. Above you, cracked ceiling stones sag, ready to drop. You can go by three worn steps up to the east end, by a gap between the broken pew rows, or back through the sagging west doors to the chapel road. Nothing has changed.

### C

> You stand in the central aisle, a broad run of flagging strewn with fallen roof slates. Close at hand sits a wooden crate, already open and empty, while above you the cracked ceiling sags with stones that look ready to come down.
> 
> To one side, a gap between the broken pew rows leads into the collapsed pews, where a heavy wooden pew lies among the wreckage. Ahead, three worn steps rise to the apse beneath its cracked half-dome. From there, you can make out two ways onward: a low door behind where the altar rail once stood, and a gap broken through the curved wall of the apse, both still unexplored. Behind you, the open length of the nave runs back to the west end, where the sagging west doors lead out to the chapel road and the way to The Last Lantern.
> 
> Nothing has changed since your last look.

### D

> You stand in the central aisle, a broad run of stone littered with fallen roof slates. Above you, cracked ceiling stones sag with the threat of a fall. Three worn steps climb to the apse beneath a cracked half-dome, where plaster saints have been worn faceless; beyond that lie a low door behind where the altar rail once stood and a gap broken through the curved wall. To one side, the collapsed pews lie crushed under a fallen roof beam, splinters everywhere. Near you sits a wooden crate with a loose lid, open and empty. Back the way you came are the sagging west doors, leading out to the chapel road. Nothing has changed.

### E

> You stand in the central aisle, a wide stretch running the length of the nave, littered with fallen roof slates that crunch softly underfoot. To the east, three worn steps lead up into the apse, a rounded chamber beneath a cracked half-dome where plaster saints gaze with worn, faceless eyes. To the side, a gap between broken pew rows reveals the collapsed pews, their wooden backs carved with faded saints but crushed flat beneath a fallen roof beam, splinters scattered across the floor. Behind you, the nave’s west end marks the cracked flagstones and a dry stone holy-water stoup just inside the open doorway from which you entered. Near you sits a wooden crate with its lid loose, empty now, and overhead the ceiling stones groan, cracked and sagging, threatening collapse. From the apse, a low door behind the altar rail and a gap broken through the curved wall hint at unexplored passages. The sagging west doors stand as the way back out to the chapel road. Nothing has changed here since you last looked.

### F

> You stand in the central aisle, beneath a cracked ceiling that seems ready to fall. Fallen roof slates cover the broad pathway stretching before you. To the east, three worn steps lead up to the apse beneath a cracked half-dome where faceless plaster saints stare blankly. Nearby, a wooden crate used for storing church candles sits open and empty right here. To the side, a gap between broken pew rows reveals pews crushed flat under a fallen roof beam, their carved backs still faintly visible. Behind the altar rail in the apse, a low door and a gap broken through the curved wall hint at unexplored ways forward. To the west is the nave entrance, marked by cracked flagstones and a dry holy-water stoup, leading back to the chapel road through sagging doors. The air is heavy with silence and the weight of ruin around you.

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

> You lash out with the Ember Rod in a hard horizontal sweep, striking the Hollow Acolyte solidly. The blow lands and bites for all its worth, the heat and force of it cutting through the dark.

### B

> You bring the Ember Rod down in a solid horizontal swing at the Hollow Acolyte. The blow lands true, cleaving into the creature and drawing forth a sharp impact that rends its hollow form. The Ember Rod slices through with a flame-tinged edge, the attack inflicting a significant wound that leaves the acolyte reeling from the damage.

### C

> You sweep the Ember Rod in a hard horizontal cut and it bites into the Hollow Acolyte with a solid crack of impact. The blow lands cleanly, jolting the creature under the force of the strike.

### D

> You sweep the Ember Rod in a hard horizontal slash at the Hollow Acolyte. The blow lands solidly, wood or flesh meeting with a blunt, certain impact, and the strike bites deep enough to hurt it.

### E

> You swing the Ember Rod in a flat slash, and it bites solidly into the Hollow Acolyte. The blow lands clean and hard, five wounds worth of pain driven into the creature by the rod’s burning edge.

### F

> You swing the Ember Rod in a sharp horizontal arc at the Hollow Acolyte. The weapon strikes solidly, slicing into the creature and drawing a wound. The acolyte reels from the blow, its fragile form showing the impact of your attack.

