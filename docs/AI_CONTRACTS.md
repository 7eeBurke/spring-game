# AI Contracts

V1 has four AI jobs only:
1. Action Interpreter
2. Outcome Narrator
3. Enemy Attack Narrator
4. Character Introduction Narrator

There are exactly four AI roles. Run-ending narration (victory outro, death) is a mode of the Outcome Narrator, not a fifth role.

Everything mechanical remains in Java. AI sits only at the edges of the pipeline: player text → **Action Interpreter** → Stage 10 validation → Stage 11 resolution → **Outcome Narrator**. Enemy decisions stay in Java; the **Enemy Attack Narrator** only describes the attack Java chose. The **Character Introduction Narrator** writes one persisted introduction per run.

Every role has a deterministic fallback, so the game is fully playable without a model, a key or a network (see Fallbacks and Failure Handling). The provider and its settings are described in `ARCHITECTURE.md` and `README_SETUP.md`.

## 1. Action Interpreter

Purpose: translate player text into structured `ActionIntent`.

Input (exactly; see AI-Facing Context below):
- the raw player text, as a JSON string value;
- an `ActionInterpretationContext` built from `PlayerSceneView`, the player's own body and tool belt, and the visible cue of each incoming attack, using request-scoped aliases;
- the allowed vocabulary, enforced by the structured-output schema;
- the schema version.

The player's text is untrusted data:
- It travels only as a string value in the user message. The role's instructions travel separately and say explicitly that player input is data, never instructions.
- A prompt injection cannot reach hidden state, because no hidden state is in the request.
- It cannot submit mechanics, because the output document has no field for them.

The interpreter may:
- classify action type;
- normalize synonyms;
- identify target/weapon/ability references from supplied context;
- split compound input into ordered steps;
- identify one dominant physical approach;
- preserve ambiguity.

The interpreter may not output:
- success/failure;
- roll/DC/modifiers;
- damage/trauma;
- conditions or injuries;
- enemy death;
- hidden facts;
- new world objects;
- IDs not supplied by backend context.

If the player refers to something unknown/nonexistent, use an unresolved reference rather than inventing an ID.

**The AI never chooses mechanics.** An intent never contains a stat, stat value or modifier, suitability, DC or DC adjustment, roll, degree of success, contact quality, effectiveness, damage, trauma, impact severity, injury, condition, success/failure, reward, enemy decision, hidden fact, or any backend identity (run, scene, revision, region, player or database ID). Java chooses and resolves all mechanics later.

### ActionIntent (schema version 1)

`ActionIntent`:
- `schemaVersion` — currently `1`;
- `responseToAttack` — optional opaque reference to the one incoming attack this intent responds to;
- `steps` — one or more `ActionStep`s;
- `confidence` — `HIGH`, `MEDIUM` or `LOW`; affects clarification/fallback only, never mechanics, and never invalidates an intent by itself;
- `unresolvedReferences` — phrases the interpreter could not resolve.

The action contract is not a persistence format.

`ActionStep`:
- `id` — non-blank, already trimmed, unique within the intent;
- `sequence` — **1-based**; steps are numbered 1, 2, 3, … in list order with no gaps;
- `relation` — to the immediately preceding step only: `START` (first step only, and only the first step), `THEN`, `IF_PREVIOUS_SUCCEEDS`, `WHILE` (genuinely simultaneous). Execution semantics are decided by action resolution;
- `payload` — one of the eight payloads below. The action type is derived from the payload (in a future JSON form, `ActionType` is the payload discriminator), so a type and payload cannot disagree.

All references in the contract (step IDs, scene-local IDs, opaque player references, incoming-attack references) are non-null, non-blank and already trimmed.

### Targets

| Target | Contents |
|---|---|
| `EntityTarget` | visible entity ID, optional `BodyPart` (anatomy not checked yet) |
| `ObjectTarget` | visible object ID |
| `HazardTarget` | visible hazard ID (for example extinguishing a fire) |
| `ZoneTarget` | visible zone ID |
| `ExitTarget` | known exit's local ID only — never its destination |
| `SelfTarget` | the player character, optional `BodyPart` (for example "use the bandage on my left arm"); not a scene entity |
| `Unspecified` | no target; holds no reference |

Specificity is `EXPLICIT` or `INFERRED` for every target that names something; only `Unspecified` is `UNSPECIFIED`.

### Player-owned references

Weapons, abilities and items the player currently has are referred to by **opaque references** supplied with the interpreter's context, valid only for that action context. They are not definition codes and not persistent instance IDs; two references may name the same definition (two bandages). They are minted per request as aliases (see Safe Aliases): `weapon_1`, `item_2`, `ability_1` and so on, and the alias *is* the opaque reference. A definition existing in the content catalogue does not make it usable; only a reference the player currently holds does.

### Payloads

| Action type | Payload fields | Rules |
|---|---|---|
| `ATTACK` | `weaponRef`, `method`, `template`, `target`, `approach`, `purpose` | Owned weapon required (unarmed attacks are not supported); target must be an entity, object or hazard. Method/template compatibility is not checked. |
| `DEFEND` | `method`, `evadeType`, `parryContact`, `cover` | `evadeType` only with `EVADE`; `parryContact` only with `PARRY`; `cover` (object or unspecified) only with `TAKE_COVER`. The attack defended against is `responseToAttack`. |
| `MOVE` | `movementType`, `target`, `goal`, `approach` | `HOLD_POSITION` has no target or goal; goal `COVER` only with `REPOSITION`; `CLOSE_DISTANCE` needs an entity or object; an exit target only with `ADVANCE`, `RETREAT` or `DISENGAGE`; never the player. |
| `INTERACT` | `kind`, `target`, `carried`, `approach` | `carried` names an owned weapon or item used (for example jamming with a crowbar); required for `DROP` and `PLACE`. `PICK_UP` needs an object; every kind except `DROP` needs a target; never the player. |
| `OBSERVE` | `kind`, `target` | `INSPECT` needs a target (it may be the player); `SEARCH`, `LISTEN`, `WATCH` may be unspecified and cannot target the player. |
| `USE_ABILITY` | `abilityRef`, `target` | Owned ability; any target, the player, or none. |
| `USE_ITEM` | `itemRef`, `target` | Owned item; any target, the player, or none. |
| `COMMUNICATE` | `kind`, `content`, `target` | Non-blank spoken content; addresses an entity or no one in particular. No stat is implied. |

The exact vocabulary for each field is listed in `GAME_RULES.md` (Action Vocabulary).

Cover mapping:
- an immediate defensive response to an incoming attack uses `DEFEND` with `DefenseMethod.TAKE_COVER`;
- proactive movement toward cover uses `MOVE` with `MovementType.REPOSITION` and `goal = COVER`.

`TAKE_COVER` is not a `MovementType`.

Unarmed attacks (punches, kicks) are outside the current action vocabulary because `ATTACK` requires an owned weapon. The interpreter must not silently turn an unarmed attack into an armed one (for example a pommel strike); unsupported actions are handled interpreter-side.

### Unresolved references

`UnresolvedReference` holds an optional step ID and the player's original phrase. It never holds a guessed ID. Any unresolved reference makes the intent invalid until it is reinterpreted; validation reports it and attempts no lookup.

### Java Validation

Validation is pure Java and deterministic. It consults only an `ActionValidationContext`:
- the `PlayerSceneView` — **never** authoritative `SceneState`;
- the player's current owned references;
- the currently known incoming-attack references.

It never sees hidden content, scene or exit-destination identities, or persistence state, and it never mutates, repairs or reinterprets the intent.

Order:
1. **Schema version.** An unsupported version is reported alone.
2. **Incoming attack.** `responseToAttack`, if present, must be a known incoming attack.
3. **Unresolved references,** in order.
4. **Each step, in order, through four layers:**
   1. structure — target kinds required by the payload;
   2. scene references — resolved only against `PlayerSceneView`;
   3. ownership — weapon, ability, item and carried references against the player's current references;
   4. physical plausibility — consulted only for a step with no earlier errors and no unresolved reference (an unresolved reference without a step blocks this layer for every step).

All errors are collected so an intent can be repaired.

Error codes:

| Code | Meaning |
|---|---|
| `UNSUPPORTED_SCHEMA_VERSION` | The intent's schema version is not supported. |
| `SCHEMA_INVALID` | The action is internally inconsistent, such as a missing or wrong kind of target. |
| `UNRESOLVED_REFERENCE` | The interpreter could not resolve a phrase. |
| `UNKNOWN_SCENE_REFERENCE` | A targeted entity, object, hazard, zone or exit is not in the player's view. |
| `UNKNOWN_PLAYER_REFERENCE` | A weapon, ability, item or carried reference is not among the player's current references. |
| `UNKNOWN_INCOMING_ATTACK` | The incoming attack responded to is not currently known. |
| `ACTION_PHYSICALLY_IMPOSSIBLE` | The action is provably impossible; no check is rolled. |

**Validation errors never reveal hidden state.** A hidden object and a nonexistent one are both simply absent from the view, so they produce the same code and the same message (which names only what the intent supplied). There is no "hidden" or "not visible" error.

`ACTION_PHYSICALLY_IMPOSSIBLE` is distinct from poor suitability: a strange but physically possible action passes validation and later receives poor suitability, down to `TERRIBLE`, which still rolls. Impossibility is declared only when known facts prove it. The player view currently holds only identity, zone placement and visibility, so the baseline plausibility policy proves nothing impossible; action resolution adds real rules through the plausibility seam.

Earlier draft codes map as follows: `UNKNOWN_ENTITY_REFERENCE` and `TARGET_NOT_VISIBLE` are `UNKNOWN_SCENE_REFERENCE` (a separate "not visible" code would leak hidden state); `UNKNOWN_ITEM_REFERENCE`, `ITEM_NOT_OWNED` and `ABILITY_NOT_KNOWN` are `UNKNOWN_PLAYER_REFERENCE`; `AMBIGUOUS_REFERENCE` is `UNRESOLVED_REFERENCE`. `ABILITY_NO_USES`, `BODY_PART_NOT_PRESENT` and `ZONE_NOT_REACHABLE` need mechanics that do not exist yet. `ACTION_NOT_SUPPORTED` and `INTERPRETATION_FAILED` are interpreter-side results, never validation codes or mechanical failures (see Interpretation Result).

### From ActionIntent to ResolvedOutcome

`ActionIntent` → Java validation → Java resolution → `ResolvedOutcome`.

Only an intent that passes validation is resolved. Resolution is entirely Java and is described in `GAME_RULES.md` "Action Resolution". It chooses the stat, DC and baseline suitability, rolls, and computes contact, damage, trauma and movement. Nothing in the intent can supply or influence any of these.

`ResolvedOutcome` is confirmed backend truth and is never AI output. Its vocabulary:
- overall result: `COMPLETE_SUCCESS`, `PARTIAL_SUCCESS`, `FAILURE`, `INTERRUPTED`, `MECHANICS_UNAVAILABLE`;
- step status: `RESOLVED`, `CANCELLED`, `MECHANICS_UNAVAILABLE`.

`MECHANICS_UNAVAILABLE` means a valid action whose mechanics are not designed yet. It is not a failure: nothing happened mechanically, so nothing may be narrated as happening. Spoken content from `COMMUNICATE` is not echoed into the outcome. A successful communication only confirms the words were said; it never confirms that anyone reacted.

### Safe Aliases

The model never sees a backend reference: no scene-local ID such as `acolyte_1`, no UUID, no exit destination and no backend incoming-attack reference. Each request mints typed aliases:

- The format is `<kind>_<n>`, matching `^(zone|entity|object|hazard|exit|weapon|item|ability|attack)_[1-9][0-9]*$`.
- Aliases are request-scoped, and deterministic for an identical situation:
  - scene content is numbered in ascending order of local ID;
  - weapons and items are numbered in tool-belt slot order;
  - the ability is `ability_1`;
  - incoming attacks are numbered in ascending order of their backend reference.
- Aliases are unique within the request and typed: an `object_` alias can never resolve as an entity.

A backend-only `AliasTable` maps them back:
- scene aliases map to scene-local IDs;
- player-owned aliases map to themselves, because they are the Stage 10 opaque references;
- attack aliases map to the backend incoming-attack reference.

A malformed alias, an alias of the wrong kind and an unknown alias are each rejected by name.

### AI-Facing Context

`ActionInterpretationContext` (context version 2) contains only:
- the current zone alias, the known zones (alias, display name, `phrase`) and the visible connections between them (alias pairs);
- known entities, objects and hazards (alias, catalogue display name, zone alias), and for objects and hazards `container` ("closed", "open, holding a Bandage", "open and empty"; null if not a container) and `reach` ("here", "one step away", "two steps away", "farther", "no known way"); known exits (alias, zone alias, `leadsTo`);
- discovered fact codes;
- the player's body (each part's severity) and the weapons, items and ability the player owns (alias, display name);
- each incoming attack: its alias, the attacker's alias if visible, and its cue phrase (see Attack Cues).

An exit's `leadsTo` is the Java label from `ExitLabels` (World Generation, Exit labels). The hub's road names its region, an exit to an already discovered scene names that scene, and any other exit is "an unexplored way". It never names an undiscovered destination.

It never contains `SceneState`, hidden content, scene or run UUIDs, seeds, revisions, exit destination IDs, target combat profiles, player stats, enemy stats, HP, traits, behaviour weights or attack difficulty. A reflection test proves the type graph cannot reach them.

### Request versions

The request names its context's version `contextVersion` (currently 2). The answer document's own `schemaVersion` is a different number (1), and the response schema pins it to that single value. The model therefore never sees a `schemaVersion` it could copy.

A context field named `schemaVersion: 2` once made the model copy 2 into every answer. Each answer was then rejected and repaired, which doubled the cost of every interpretation.

### Route grounding

The interpreter says where the player means to go; Java (`RouteGrounding`, applied to free text only) decides how to get there, using only the player-visible zones, connections and exit labels. The defense gate is checked first: a turn that must open with a defense is refused before any movement is grounded.

**Crossing into another scene is the player's decision.** An exit is taken only when three sources agree:
1. **The interpreted move heads for it.** That is, the move targets the exit, has no target, or heads for the exit's zone when no zone is named in full ("enter the chapel" read as a move to Chapel Road). Or the move contradicts every place the words name: the real probe read "enter the chapel" as a move back to the hearth, and Java corrects that.
2. **The exit is uniquely identifiable.** Its label is named, or it is the only known way out within reach (in the player's zone, or else one step away). The second case covers "go inside" and "step through the doorway".
3. **The player's words say so.** A handful of threshold words (enter, into, inside, through, cross, travel, "all the way") must appear, and no approach words (toward, approach, near, edge, search, seek, find). To go back to an already discovered place, a return word (back, return, retreat) is enough.

The model marking a target EXPLICIT never authorizes a crossing on its own. Several identifiable ways give a clarification, never a guess.

**Otherwise the journey stays in the scene.** Movement that is not a crossing:
- **A named place:** approached. That means its zone, or the zone a named exit leaves from, never the exit itself.
- **An unnamed "walk forward":** at most one local step, to the connected zone the single way on leaves from.
- **No local step, because the way on begins here:** a threshold refusal, `ACTION_NOT_SUPPORTED` with reason `AT_THRESHOLD`. Its message names the way by its passage and asks: "Before you: the Hollow Chapel's sagging west doors. Do you want to go through?" No turn is spent. (`ALREADY_THERE` remains for a move to the zone the player is in.)
- **Several ways:** a clarification (`UNCLEAR_DESTINATION`).
- **The intent also does something else (looking around):** the move becomes an idle stay instead, so the rest still happens.

**Going to an object.** A move that targets an object, or `CLOSE_DISTANCE`, stays in the scene and never crosses an exit:
- with approach words (toward, towards, closer, nearer, approach): **one** step toward it, so "move toward the crate and see if I can open it" moves once. If that step puts the crate in reach (the first find is one passage from the arrival), it opens in the same turn; otherwise the move stands and the open is told as the crate still being out of reach (`InteractionFailed`, `OUT_OF_REACH`), never as an attempt. An open with no progress before it is refused without a turn (Game Rules, Containers);
- otherwise ("go to the crate", "go all the way to the crate and open it"): **at most two** steps, along the shortest known path;
- the route stops early at the first zone holding a living creature or a hazard the player can see; fallen enemies do not stop it;
- "examine the crate from here" is an INSPECT with no movement, and "see if I can open it" is an OPEN, never a take.

**Destination.** The player's words name places by significant words (via `DestinationCheck`):
- lower case and accent-folded;
- at least four letters;
- common movement words removed;
- whole words only, so "chapelle" does not match "chapel".

The destination is chosen as follows:
- A place named in full beats places named only in part.
- The zone the player is in is never a destination.
- A named way back ("back to the hearth", "back to the Last Lantern") is honoured. RETREAT to a place is grounded like any journey.

**Route.**
- A zone is one REPOSITION.
- An exit in the player's zone is one ADVANCE.
- An exit in a directly connected zone is REPOSITION, then `IF_PREVIOUS_SUCCEEDS` ADVANCE.
- Nothing farther is planned: the step is left as it is, and the engine fails it.

At most one step is added, and other steps keep their order (a leading DEFEND stays first). The rewritten intent is validated again. Slash commands, intents with more than one journey, and other movement types are never touched.

Each new turn writes one diagnostic log line, `turn timing`. It gives the milliseconds spent interpreting, grounding, resolving and narrating, and the total. When grounding rewrote the journey, it also gives both shapes as action, movement and target kinds only, for example `raw[MOVE:ADVANCE:NONE,OBSERVE:WATCH]->[MOVE:REPOSITION:ZONE,OBSERVE:WATCH]`. It never includes player text, aliases, identifiers or credentials.

### Unclear answers

If the model cannot tell what part of the input refers to, it names the phrase in `unresolved`. If it cannot tell what the player wants at all, it gives no steps and the phrase.

Such an answer is `Unclear`, and it is **not repaired**: a repair could only invite a guess. Route grounding may still settle it:
- the journey step it came with;
- or, with no steps, the one place the player's words name.

Otherwise the request is refused with `INTERPRETATION_FAILED`, reason `UNCLEAR`, and the grounded surroundings as the hint. No turn is spent and no narration is made.

### Action Document (schema version 1)

The model answers with this document. A strict JSON-schema structured output enforces it, and Java then parses it again:

```
{ schemaVersion: 1, supported: bool, responseToAttack: attack alias | null, confidence: HIGH|MEDIUM|LOW,
  steps: [ { relation, action, attack|null, defend|null, move|null, interact|null, observe|null,
             useAbility|null, useItem|null, communicate|null } ],
  unresolved: [ { stepNumber: int | null, phrase } ] }
target      = { kind: ENTITY|OBJECT|HAZARD|ZONE|EXIT|SELF|NONE, alias: string|null, bodyPart: BodyPart|null, specificity }
attack      = { weapon, method, template, target, approach, purpose }
defend      = { method, evadeType, parryContact, cover: target }
move        = { movementType, target, goal, approach }
interact    = { kind, target, carried: { kind: WEAPON|ITEM, alias } | null, approach }
observe     = { kind, target }
useAbility  = { ability, target }      useItem = { item, target }
communicate = { kind, content, target }
```

- Exactly the payload matching `action` is non-null.
- Scene target kinds need an alias; `SELF` and `NONE` have none. Only `ENTITY` and `SELF` may name a body part.
- Every target kind except `NONE` identifies something, so its `specificity` is `EXPLICIT` (the player named it) or `INFERRED` (identified from context). `NONE` is always `UNSPECIFIED`. This mirrors Stage 10 and is checked during parsing, so a violation is reported with its field path (for example `steps[0].attack.target`) and repaired once like any other structure problem. The schema's property descriptions and the prompt state the same rule.
- `supported: false` means the input is not an in-world action, and the document has no steps.
- Java numbers the steps `s1..sn`. Every value uses the Stage 10 vocabulary.
- **There is no mechanical field**: no stat, suitability, DC, roll, success, damage, trauma, contact, effectiveness, HP, injury, condition or reward. A model that adds one fails strict parsing.
- The schema is generated from the Stage 10 enums. Every object lists all of its properties as required and forbids additional ones; optional values are nullable.

Strict parsing uses the project's strict JSON settings: unknown, missing and wrongly typed fields, unknown or wrongly cased enum values and trailing content all fail. An explicit `null` is accepted only in the nullable slots above. Provider schema enforcement never replaces this parsing or Stage 10 validation. The shared target shape is defined once under `$defs` and referenced with `$ref`, which keeps the schema sent with every interpretation to about 5,600 characters, down from 14,700.

### Interpretation Pipeline

1. **Slash commands and blank input.** Input starting with `/` is a slash command (see Command Fallback) and never reaches a model. Blank input is `INTERPRETATION_FAILED (EMPTY_INPUT)`.
2. **Everything else** goes through structured generation → strict parse → alias resolution → `ActionIntent` → Stage 10 validation.
3. **Repair, at most once.** If parsing, alias resolution or construction fails, or validation reports errors other than unresolved phrases (those make the answer `Unclear`, above), one repair request is sent. It carries the same instructions and context, the original player text, the previous output and model-safe problems:
   - a description of a structure problem;
   - an alias problem, naming only the model's own alias;
   - a Stage 10 code with its step number and a fixed description of that code.

   Raw validation messages contain backend references and are never forwarded. Repair reads no `SceneState`.

   The call's log line records why a repair was needed as `repairCause`. It holds categories and fixed codes only (for example `PARSE:schemaVersion`, `MAPPING` or `VALIDATION:SCHEMA_INVALID`): never player text, model output or credentials.
4. **A second failure** is `INTERPRETATION_FAILED (INVALID_OUTPUT)`. Nothing is guessed.
5. **A provider failure on either call** is `INTERPRETATION_FAILED (AI_UNAVAILABLE)`, with a hint to use slash commands. Provider failures are: disabled, not configured, timeout, authentication, rate limit, provider error, network, refusal and truncation.

At most two model calls are made per player action, plus the provider client's own transport retries.

### Interpretation Result

- **`Interpreted(ValidatedActionIntent, source)`**: the intent has passed Stage 10 validation and goes straight to Stage 11.
  - `source` is `AI`, `AI_REPAIRED` or `COMMAND`.
  - The intent's `confidence` is the model's semantic confidence (`HIGH` for commands) and has no mechanical effect.
- **`NotSupported`**: **ACTION_NOT_SUPPORTED**. The input is not an in-world action. This is not a mechanical failure.
- **`Unclear(intent?, phrases)`**: the model named what it could not place, instead of guessing. It carries the steps it gave, if any. It is never repaired; route grounding may settle it, and otherwise it becomes **INTERPRETATION_FAILED** (`UNCLEAR`; see Unclear answers). The phrases are kept for diagnostics only: never shown back or logged.
- **`Failed(reason, aiFailure, details)`**: **INTERPRETATION_FAILED**, with player-safe details. The reason is `EMPTY_INPUT`, `AI_UNAVAILABLE`, `INVALID_OUTPUT` or `INVALID_COMMAND`.

### Command Fallback

A deterministic, deliberately limited command mode. It uses the same aliases the context shows and never guesses. Each command produces an ordinary `ActionIntent` (confidence `HIGH`, approach `NORMAL`, specificity `EXPLICIT`) that still goes through Stage 10 validation. It resolves no mechanics. Verbs and enum words are case-insensitive.

| Command | Produces |
|---|---|
| `/attack <target> <method> [template <T>] [part <BodyPart>] [with <weapon>] [purpose <P>]` | `ATTACK`. The weapon defaults to the only owned weapon; otherwise `with` is required. Purpose defaults to `DAMAGE`. The template defaults by method (no mechanical effect today): SLASH→HORIZONTAL_SWING, THRUST→THRUST, SMASH→OVERHEAD_STRIKE, HOOK→HOOK_AND_PULL, PROJECT→PROJECTED_ATTACK. **POMMEL_STRIKE has no default** and needs `template <T>` |
| `/defend <method> [evade <EvadeType>] [parry <ParryContact>] [cover <object>] [against <attack>]` | `DEFEND`, responding to the only incoming attack. With several, `against` is required; with none, there is no response |
| `/move <zone>` / `/move <exit>` | `REPOSITION` to the zone / `ADVANCE` through the exit |
| `/hold` | `HOLD_POSITION` |
| `/search`, `/listen`, `/watch [<alias>]`, `/inspect <alias>` | `OBSERVE` |
| `/push`, `/pull`, `/break`, `/open`, `/close`, `/pickup`, `/jam`, `/ignite`, `/extinguish <alias>` | `INTERACT` |
| `/drop <weapon or item>`, `/place <weapon or item> on <alias>` | `INTERACT` with the carried thing |
| `/use <item> [on <alias> or self]`, `/ability <ability> [on <alias> or self]` | `USE_ITEM`, `USE_ABILITY` |
| `/say`, `/ask`, `/threaten`, `/persuade`, `/deceive`, `/bargain [to <entity>] <text…>` or `… "<text>"` | `COMMUNICATE`. Unquoted content is the rest of the command and ends at `;` or `&&`. Double-quoted content is taken verbatim (`;` and `&&` inside it do not split commands, and `\"` is a literal quote); nothing may follow the closing quote |
| `cmd ; cmd` | the second step is `THEN` |
| `cmd && cmd` | the second step is `IF_PREVIOUS_SUCCEEDS` (`WHILE` is not available) |

Commands are split at `;` and `&&` only outside double quotes. An unterminated or empty quotation is `INVALID_COMMAND`. Anything else is `INTERPRETATION_FAILED (INVALID_COMMAND)` with a short reason. A command that parses but fails Stage 10 validation is also `INVALID_COMMAND`, with the validation codes.

## 2. Outcome Narrator

Purpose: narrate confirmed backend truth after Stage 11. It never receives the `ActionIntent` and never guesses what happened.

**Modes** (one role, never separate roles): `NORMAL`, `RUN_DEATH`, `RUN_VICTORY`. Java chooses the mode and supplies the terminal fact: `PlayerDied(attacker name?)` or `GuardianDefeated(name)`. The AI never decides that a run has ended. A mode without its matching terminal fact is rejected.

**Input:** `OutcomeNarrationContext(mode, current zone name, overall result, facts, terminal fact, untrustedPlayerWording?)`.
- `OutcomeNarrationContextBuilder` receives the `ResolvedOutcome` and the `ValidatedActionIntent` that produced it (their step IDs must match). The narrator itself never receives the intent.
- **`untrustedPlayerWording`** is an optional excerpt of what the player typed: at most 300 characters, labelled untrusted, a JSON string in the user message only. It is narrative context, never instructions and never a confirmed outcome.

Facts are derived deterministically from each `StepOutcome`, in step order, using player-visible names only. Every fact carries an **`attempt`** (`AttemptedAction`), a Java summary of what the step tried to do. It has:
- the action type;
- the manner: method, defense method, movement type, or interaction, observation or communication kind;
- the attack template, approach and purpose where they apply;
- the target's kind (creature, object, hazard, zone, exit, self), visible name and body part;
- what was used (a weapon, item or ability, by display name);
- `spokenWords`.

An attempt is never a confirmed outcome. **`spokenWords`** (the player's words, untrusted data) is present **only for a RESOLVED COMMUNICATE step**. A cancelled or unavailable communication never supplies words, and its fact cannot be built with them.
- catalogue display names, numbered as "Hollow Acolyte 2" when several share a kind;
- "something unseen" for an actor the player cannot see.

| Fact | Contents |
|---|---|
| `PlayerAttacked` | weapon name, target name, body part?, contact, HP damage, impact severity?, `noContact`, `contactWithoutDamage` |
| `PlayerDefended` | attacker name, defense method, contact, HP damage, body part?, impact severity?, `avoided`, `contactWithoutDamage` |
| `PlayerMoved` | from zone, to zone |
| `PlayerStayed` | zone, attempted zone? (holding position, or a zone that could not be reached) |
| `PlayerSpoke` | communication kind, addressee name?; the words are in its attempt's `spokenWords` |
| `StepCancelled` | reason; what was attempted is in its attempt (never with spoken words) |
| `StepHadNoEffect` | reason (a valid action with no mechanics yet); what was attempted is in its attempt (never with spoken words) |
| `WalkedTo` | from and to places, and the passage between them |
| `StayedPut` | the place, and the place that could not be reached? |
| `CrossedInto` | the way taken, the new scene's name and description, the arrival place, the way back in, and the arrival's `Perception` |
| `Perceived` | a `Perception` (here; places beside it with their passages; things, hazards and creatures with where they are; ways out by passage), minus whatever an arrival in the same turn already gave; `unchanged` when it matches the previous turn's look |
| `Inspected` | the thing's name, authored description, container state?, where it is, and `inSight` |
| `ContainerOpened` | the container's name, what it holds, `alreadyOpen` |
| `InspectedPlace` | a close look at a place, or at a feature of it (a hole in its floor): the place's phrase and description, and the known ways that leave from it (passage, here or where, steps, where it leads); nothing when out of sight. It never moves the player |
| `OpenedContainer` | the container's name, what it holds (each item with its authored description), `alreadyOpen`; a closed container's contents are never told |
| `TookItem` | the item, its description (how it looks in the hand), and the container it came from (which no longer holds it) |
| `SoughtWays` | a look for somewhere to go: what is in sight (a `Perception`), then leads from the known view: `unexplored` ways (passage, where it leaves from, steps, via), `unvisited` places, `visited` places, `known` ways, `visitsKnown`, `nothingKnownLeft` |
| `InteractionFailed` | the thing's name and the reason (`CLOSED`, `EMPTY`, `NO_ROOM` after an attempt; `OUT_OF_REACH` when it could not begin, with where the thing is) |

Places are given as a `PlaceRef`: a `label` (the map name, for headings only), a `phrase` and a `description`. Narration describes places through phrase and description and never uses a label as if it were an in-world name. `PlayerMoved`, `PlayerStayed`, `PlayerLeftScene`, `PlayerArrived`, `PlayerObserved`, `ExitNotReached`, `ItemTaken` and `ContainerOpened` are no longer produced; they are kept so that summaries stored before the change still deserialise under strict JSON.

Non-events are explicit (no contact, contact without damage, staying put, a cancelled step, a step with no effect), so narration cannot imply they happened.

**May:** add sensory texture, tone, pacing, mood and metaphor.

**May not:**
- invent an interactable object, enemy, exit, loot, hazard, item, wound, condition, state change, quest or reward;
- change severity;
- contradict a non-event.

**Output** is plain text of up to 1200 characters. Narration is presentation only: it is never parsed back into mechanics or applied as state.

**Fallback:** one factual sentence per fact. For example:
- "Your Longsword lands a solid hit on the Hollow Acolyte in the head, dealing 6 damage."
- "Your Longsword misses the Hollow Acolyte."
- `You threaten the Hollow Acolyte: "Back away."` (resolved speech)
- "You do not follow through with your attempt to threaten the Hollow Acolyte, and nothing is said."
- "Nothing comes of your attempt to inspect the Fire."
- an observation, from phrases and descriptions only: where the player stands, what lies beside it and how it is reached, what is in reach, and the ways on ("A low door in the north wall, reached from the vestment racks: an unexplored way.").

The run-ending modes add a closing line: "Your strength fails. The run ends here." or "The Chapel Guardian falls. The Hollow Chapel is still at last."

## 3. Enemy Attack Narrator

Java's enemy behaviour has already chosen the attack (`IncomingAttack`). The narrator only describes it, so the player can choose a defense. It does not choose the attack, its difficulty, damage, target part or success.

### Attack Cues

Each `AttackTemplate` maps to one cue. Java always supplies the cue separately from the prose, so the player always sees it (see Output below).

| Template | Cue | Phrase | Keywords |
|---|---|---|---|
| `THRUST` | `DIRECT_THRUST` | a straight thrust driven directly at you | thrust, lunge, straight |
| `HORIZONTAL_SWING` | `HORIZONTAL_SWEEP` | a wide swing sweeping in from the side | sweep, swing, side |
| `LOW_SWEEP` | `LOW_SWEEP` | a low sweep close to the ground | low |
| `OVERHEAD_STRIKE` | `DESCENDING_STRIKE` | an overhead strike coming down from above | overhead, above, down |
| `QUICK_SLASH` | `FAST_CUT` | a quick, fast cut | quick, fast, swift |
| `HEAVY_SMASH` | `HEAVY_BLOW` | a heavy, crushing blow | heavy, crushing |
| `HOOK_AND_PULL` | `HOOKING_PULL` | a hooking motion meant to catch and pull | hook |
| `PROJECTED_ATTACK` | `PROJECTED` | a bolt hurled through the air toward you | projected, hurled, flies, bolt |

No height, width, speed or force geometry is implied beyond the template; attack geometry is deferred.

**Input:** `EnemyAttackNarrationContext(attacker name, weapon name, cue, cue phrase, required keywords)`. It has no difficulty, damage, target part, other options, weights or stats.

**Output:** `EnemyAttackNarration(prose, cue, cueText)`.
- **`cueText` is Java-generated and mandatory**, for example "Incoming: an overhead strike coming down from above." It is always available for display separately from the prose, and it cannot be replaced.
- **The prose** may use its own wording; it is not required to repeat a cue keyword. Model prose is used if it is non-blank, at most 600 characters and states no numbers. Otherwise, or on any failure, the fallback prose is used: "The Hollow Acolyte comes at you with its Dagger: an overhead strike coming down from above."
- Neither part can change the `IncomingAttack` or the cue.

## 4. Character Introduction Narrator

**Input:** `CharacterIntroductionContext(name, the five stats, Fated value, Fated band, weapon names, passive, ability, item names, lore)`.
- The Fated band is a narration-only label (see `CHARACTER_GENERATION.md`).
- No probabilities, HP or identities are given.
- The lore is the fixed premise in `content/lore.json` (see `CONTENT.md`).

**Allowed:** soft personal history, a vague former occupation, emotional impressions and fragmented memories.

**Forbidden:**
- stating stats, numbers or mechanical bonuses: strengths are described qualitatively, and the Fated number is not stated;
- any item, weapon, power or ability beyond those given;
- named people the character must find, owes or will meet;
- quests, promises or obligations;
- places the character must later visit;
- extending the lore with new canon.

**Output** is plain text of up to 2000 characters.

**Fallback:** the lore lines, then the name, the strongest and weakest stat, the weapons and items, the passive and ability, and the Fated band's phrase.

The introduction is generated once per run and persisted; reloading returns the stored text (see `CHARACTER_GENERATION.md`).

## Prompts

- **Location:** each role's instructions are a versioned classpath resource, `src/main/resources/ai/prompts/<role>-v<version>.txt`. The Action Interpreter is at version 6: v5's rules, plus approaching an object is a MOVE targeting it (CLOSE_DISTANCE for "toward"), opening is `INTERACT OPEN`, taking from a container is `INTERACT PICK_UP` targeting the container, "open it and take what's inside" is two steps, and "see if I can open it" is only an OPEN. Version 5 was: v4's rules, but an EXIT is targeted only when the player says to go through it or into where it leads. Walking forward, approaching or looking for an entrance targets a zone, and a look around stays its own OBSERVE step. Version 4 said "the game picks the way forward", which let a walk become a crossing; it and version 3 (which asked the model to plan routes) are kept for history.

The Outcome Narrator is at version 4. It keeps v3's rules:
- exact place names for movement;
- a place named in the wording but not confirmed as the arrival is never described as entered or reached;
- observation limited to the given surroundings, useful information first;
- one short sentence for a non-event.

Version 4 adds:
- the narration speaks as the world, not the game (no "repositioning", "moving through an exit" or "entering the scene called…");
- only a `PlayerLeftScene` fact means a crossing, and approaching is not entering;
- an arrival is described from its `PlayerArrived` surroundings.

**Interpreter v7 and narrator v7.** The interpreter context now gives each known zone its authored `description` and each known exit its `passage` (how the way looks), so "the hole" or "the ladder down" can be matched to the place or way they belong to; only what the player knows is included. Interpreter v7 adds: a feature of a place's description is that ZONE; a way described by its look is that EXIT; "is it safe / how deep" is an INSPECT, never a move; never substitute an unrelated object; otherwise `unresolved`. Java backs this up (`InspectionTargetCheck`, free text only): a close look the model aims at an object the player's words never name becomes a look at the place in sight whose description or ways the words do name (the player's own place first; "hole", "crack", "gap" and the like are one kind of opening), or, when nothing known matches and no pronoun points at it, the request is refused as `UNCLEAR` with no turn spent. Nothing is ever substituted. Narrator v7 (v6 kept) adds: tell an `InspectedPlace` from its description and ways; for safety, depth or distance, answer only from the facts and say when it cannot be judged, never inventing a height, landing, outcome or descent; filtered facts (a `SoughtWays` perception omits objects) never become "there is nothing"; and a lead in another place is told as one to go to, not one in front of the player. In narration, a way whose passage already names where it leads is told without repeating the destination (the hub's doors are "the sagging west doors into the Hollow Chapel"); the exit labels themselves are unchanged.

The Outcome Narrator is at **version 6** and runs on its own model, `gpt-5.4-mini` with reasoning effort `none` (`GAME_AI_NARRATOR_MODEL`, `GAME_AI_NARRATOR_REASONING_EFFORT`), chosen in a blind benchmark of three models and two prompt styles (`src/test/java/.../narratorbenchmark/`). Version 6 keeps every v5 grounding rule and adds a dark-fantasy Dungeon Master style: length follows novelty and importance (a rich introduction for a crossing; a moderate one for a place reached for the first time; a sentence or two for a familiar walk, an ordinary interaction or taking an item; more only for a significant discovery or a dramatic blow); the ways question is answered first; no stock closings; nothing invented for effect. Java sets the pace at the source:
- a walk back to a place already stood in carries no description (`WalkedTo.to.description` is empty), so it is told briefly;
- a `SoughtWays` fact carries only the leads and what bears on them (no object lists or place description);
- an opened container's items carry their authored descriptions (`OpenedContainer`);
- a look that finds nothing changed is sent to the model only as where the player stands, and when it is the turn's only fact, the model is not called at all: the game tells it itself, with narration source `DIRECT` (not a failure; shown without the "told plainly" note).

Version 5, kept for history: It adds a "Places" section: places are shown through their phrase and description, never their labels; an arrival is told first, then only what is new; an unchanged look is told in a sentence; something out of reach is described as out of reach; a closed container's contents are never guessed; and no interaction is presented as available unless a fact confirms it. A `SoughtWays` fact is answered from its lists first, never inventing a way, and honestly when `nothingKnownLeft`; a taken item is described from its description and never as still inside its container. Facts describe containers as the turn's earlier steps left them. The interpreter (v6) reads questions about where to go as an untargeted OBSERVE SEARCH, and "take the vial and examine it" as the take alone (the item is not addressable yet), never as an inspection of the container in its place. The Character Introduction Narrator is at version 2 (it may close on the lore's direction). The Enemy Attack Narrator is at version 1. Superseded prompt files are kept for history; only the current version is loaded.
- **Versions** are operational metadata for prompt evolution, separate from rules, content and schema versions. The persisted introduction records the prompt version that produced it.
- **Prompts are application configuration**, not game content, and are never stored in the database.
- **Request shape:** each request sends the role's instructions separately from a single JSON user message. Player text, when present, is only a string value inside that message.

## Fallbacks and Failure Handling

| Role | On provider failure or unusable output |
|---|---|
| Action Interpreter | one repair for unusable output, then `INTERPRETATION_FAILED`. A provider failure is `INTERPRETATION_FAILED (AI_UNAVAILABLE)`. Slash commands always work |
| Outcome Narrator | deterministic factual narration |
| Enemy Attack Narrator | deterministic prose, also used for blank, overlong or numeric prose; the Java `cueText` is always present |
| Character Introduction Narrator | deterministic introduction, persisted like AI text |

Failures are classified as `DISABLED`, `NOT_CONFIGURED`, `TIMEOUT`, `AUTHENTICATION`, `RATE_LIMITED`, `PROVIDER_ERROR`, `NETWORK`, `REFUSED`, `TRUNCATED` or `MALFORMED_RESPONSE`, and returned with the result; they are never silently treated as success. No game state ever depends on partially parsed model output, and narration never mutates state.

Each call writes one log line with the role, prompt version, model, latency, attempts, outcome and the token usage the provider reported (input, cached input, output and reasoning tokens; the interpreter sums its attempts). Usage is operational metadata only: logged, never persisted, never game state. Keys, headers, player text, prompts, model output and game state are never logged, and model reasoning is never requested or stored. The exact wording of accepted actions is persisted (`run_turn.player_input`, Stage 15A) so the run's chronicle can show it. It is readable only with the run's token, and it is not given to any model beyond the bounded excerpt described for the Outcome Narrator.

The game remains mechanically playable without a live model.

## Orchestration and Cost (Stage 14)

**When each role runs:**
- **The Action Interpreter** runs only for a new free-text turn whose view is current. It never runs for slash commands, replays or stale requests.
- **The Outcome Narrator** runs once per committed turn.
- **The Enemy Attack Narrator** runs once per new pending attack.
- **The Character Introduction** is generated once per run, at creation.

**No transaction during model calls.** Every model call happens with no database transaction open.

**Stored narration.**
- Narrations are stored with the turn's response, and attack narration on `pending_attack`. Replays and `GET` reuse them and never call a model.
- While a turn's narration is pending, the view shows its mechanics with `finalizing: true` and no narration. The pending attack's Java `cueText` is always present.

**One narration per turn.** A narration lease ensures that concurrent requests narrate a committed turn once.

**Transient failures.** A transient interpreter failure (`AI_UNAVAILABLE`) releases the request's idempotency key, so the same request can be retried. The answer is `INTERPRETATION_FAILED` with reason `AI_UNAVAILABLE` and a slash-command hint.

**Daily budget.**
- `BudgetedAiProvider` wraps a configured provider with a global daily cap: `game.ai.daily-call-limit`, default 500 calls per UTC day, kept in memory.
- Past the cap, calls fail as `RATE_LIMITED` without reaching the provider. The interpreter then reports `AI_UNAVAILABLE`, and the narrators fall back.

**NPC scope.** COMMUNICATE addressed to an enemy is narrated as spoken. There is no reply, reaction or persuasion, and no dialogue role; NPC dialogue is deferred.
