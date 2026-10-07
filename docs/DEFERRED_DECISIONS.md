# Deferred Decisions

These mechanics and details are deliberately unresolved.

A deferred decision is not a blocker unless the current implementation stage depends on it.
Do not resolve deferred items pre-emptively, and do not invent values merely to make the documentation appear complete.
If a stage requires one of these, flag it and get a decision before implementing.

## Combat and Resolution

- Combat turn loop: ordering of player and enemy actions, when an enemy decides, and how its incoming attack is presented to the player.
- Multi-enemy combat details and initiative.
- Resolving an incoming attack the player does not defend against, and when the enemy's attack happens relative to the player's other steps.
- Defensive mitigation, and mechanical differences between defense methods (evade type, parry contact, the cover object for `TAKE_COVER`) beyond the stat each uses. The defense stat, DC and the defense-to-`ContactQuality` mapping are in `GAME_RULES.md` "Action Resolution".
- Mapping from canonical attack methods to universal attack templates.
- Attack geometry: attack-property shift/override values (height, width, speed, force, reach, commitment) and how they affect defenses.
- Effectiveness determination (weapon/damage type vs target). The levels and their multipliers are defined in `GAME_RULES.md`; only how a level is chosen is deferred. Resolution currently takes it from the backend-supplied target profile or incoming attack.
- Attack-form modifier values for trauma (for example deep-cut, penetrating, crushing forms) and how a form is derived. Player attacks currently use the baseline 0.
- Anatomy interaction modifier values for trauma (currently the baseline 0 in both directions).
- `WHILE` resolution semantics (beyond: genuinely simultaneous, subject to simultaneous-action complexity). A `WHILE` step is currently mechanics-unavailable.
- Detailed social (`COMMUNICATE`) resolution rules: when speech needs a check, which stat, and its consequences. Communication currently resolves as an automatic success with no consequence.
- Suitability tables beyond the `FAIR` baseline, and the other DC adjustments (injury, position, environment, passive, targeting, complexity).
- Stats and DCs for actions that do not resolve yet: interaction, observation, ability use, item use, climbing and other movement.
- Hit location when an attack names no body part. None is currently invented.
- Range bands and positioning: `CLOSE_DISTANCE`, `RETREAT`, `CIRCLE`, `DISENGAGE`, proactive cover, range-band changes and movement costs.
- Exit traversal: the zone the player arrives in, and when a scene change happens.
- Mechanics for attacks on objects or hazards, and for attack purposes other than `DAMAGE` (disarm, trip, push and others).
- Applying `ResolvedOutcome` effects to state (HP, body severity, enemy HP, location) and persisting them.
- Physical-plausibility rules: which actions are provably impossible (the validation seam exists; the baseline proves nothing impossible).
- Unarmed attacks (punches, kicks): method, template, damage and trauma. They are not part of the current action vocabulary.

## Body, Conditions and Recovery

- Condition behaviour (`BLEEDING`, `FRACTURED`, `BURNED`, `POISONED`).
- Body-part severity escalation thresholds.
- Existing-injury trauma modifier for a `DESTROYED` part (the `GAME_RULES.md` table covers healthy to crippled only). Defending against an attack aimed at a destroyed part is currently mechanics-unavailable.
- Healing and recovery-item effects.

## Enemies

Enemy stats, HP, defense DC, attack difficulty, anatomy for the four MVP enemies, and behaviour weights are defined in `GAME_RULES.md` "Enemies". Still deferred:
- Enemy protection, trauma protection and defensive mitigation beyond the Stage 12 baseline of 0.
- Non-humanoid anatomies, anatomy-specific values (vital parts, missing parts), visible anatomy in `PlayerSceneView`, and the `BODY_PART_NOT_PRESENT` validation code. An attack on a part an enemy lacks is currently mechanics-unavailable through the missing profile.
- Hit location of enemy attacks (an enemy attack currently names no body part).
- Body-part requirements of enemy attacks (for example a destroyed arm preventing a swing).
- Enemy movement, retreat, cover and defense, and range or positioning as behaviour inputs.
- What an enemy may observe about the player, and therefore Perception-based exploitation and the `OPPORTUNISTIC` trait (identity only for now).
- Applying damage to enemies, enemy death and removal.
- Fated effects on enemies (generation, variants such as the Broken Veteran, behaviour). Fated has no effect on enemies.
- Persisting enemy decision history; callers currently supply recent choices.

## Content Mechanics

- Passive effects.
- Ability effects (beyond prototype ~2 uses per run), charges and the `ABILITY_NO_USES` validation code.
- Utility tool effects.
- Hazard mechanics.
- Event mechanics and Fated variations.
- Reward granting, including the Ashen Sigil from the Chapel Guardian.
- Rare Fated starting modifications.
- Fated consequences: use of the qualitative bands, rare events, enemy variants, rewards (only the numeric value is generated and stored).
- Weapon definition extensions: supported attack methods, suitability, tags, handedness, stat affinities, attack-form modifiers.
- Runtime item/weapon instance ID format (distinct from static definition codes).

## Generation

- Starting-content weighting: weapon stat affinity, passive/ability/item weighting, loadout coherence (`LOW`/`MEDIUM`/`HIGH`) and its effects, redundancy weighting. Character generation currently uses uniform independent selection within each pool as a baseline; this is not a decision to drop weighting.
- Thematic scene roles (combat, event, resource, danger, …) and their compatibility with archetypes. Generation currently uses structural roles only (entry, route, pre-boss, optional, boss).
- Scene content budgets beyond per-slot chances (for example region-wide limits on enemies or hazards).
- Objective and gate mechanics, including boss-gate routes with multiple solution categories. The generated graph is currently fully traversable with no locks.
- Validation of recovery opportunities before the boss and of threat pacing between scenes.
- Anti-repetition across runs beyond the opening scene (events, enemies, layouts). The opening anti-repeat window (3 recent openers) and its weights are defined in `WORLD_GENERATION.md`.
- Event uniqueness rules beyond the current baseline that every event is unique within a region.
- Building the generation-context snapshot from a player's recent runs (needs an account/orchestration layer); callers currently supply it.
- Fated effects on generation (counts, weights, events, hazards, rewards, openers). Fated has no effect on generation.
- Future regions beyond Hollow Chapel.
- Cross-version deterministic replay and static-content versioning (reproduction currently assumes the same rules/content version). Persisted runs store definition codes; a run referencing a code that was later removed or renamed fails to load clearly. Migrating stored codes is deferred.
- Migration of persisted generation-context documents between `schema_version`s.

## World and Scenes

- Scene history: the entry schema for recent changes/action history (not yet part of `SceneState`).
- Movement execution, reveal and discovery mutations.
- Visibility / line-of-sight algorithm that produces the set of currently visible zones (the projector only filters safely given that set).
- How active events and environment flags become player-perceivable in `PlayerSceneView`.
- Per-object and per-zone state (for example burned, open) and zone tags.
- Migration of persisted scene-state JSON documents between `state_schema_version`s.

## Presentation and Contracts

- Run summary contents.
- Narration facts: the reduced, narration-safe facts derived from a `ResolvedOutcome` for the narrator.
- Entity/object ID format exposed to AI roles.
- How opaque player-owned action references (weapons, abilities, items) are minted and presented to the Action Interpreter; the validation contract accepts any non-blank opaque references.
- AI provider integration and structured-output schema/JSON codec for `ActionIntent`, including interpreter-side `ACTION_NOT_SUPPORTED` / `INTERPRETATION_FAILED` handling.
- Action history persistence. `PlayerSceneView` currently exposes scene-local IDs and never UUIDs (no scene, exit-destination or run identities); the final AI-facing format is still undecided.
