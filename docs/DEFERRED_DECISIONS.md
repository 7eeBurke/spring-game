# Deferred Decisions

These mechanics and details are deliberately unresolved.

A deferred decision is not a blocker unless the current implementation stage depends on it.
Do not resolve deferred items pre-emptively, and do not invent values merely to make the documentation appear complete.
If a stage requires one of these, flag it and get a decision before implementing.

## Combat and Resolution

- Combat turn loop: ordering of player and enemy actions.
- Multi-enemy combat details.
- Enemy attack resolution: how the player's defensive response is checked against an incoming attack.
- How a defense result maps to `ContactQuality` and `defensiveMitigation`.
- Mapping from canonical attack methods to universal attack templates.
- Attack-property shift/override values.
- Effectiveness determination (weapon/damage type vs target). The levels and their multipliers are defined in `GAME_RULES.md`; only how a level is chosen is deferred.
- Attack-form modifier values for trauma (for example deep-cut, penetrating, crushing forms) and how a form is derived.
- Anatomy interaction modifier values for trauma.
- Exact `WHILE` resolution semantics (beyond: genuinely simultaneous, subject to simultaneous-action complexity), and how `THEN` and `IF_PREVIOUS_SUCCEEDS` treat each degree of success, interruption and cancellation.
- Detailed social (`COMMUNICATE`) resolution rules.
- Suitability determination and stat selection for an action in context.
- Movement resolution: zone reachability, range-band changes and movement costs (and the `ZONE_NOT_REACHABLE` validation code).
- Combat timing and response-to-attack execution, including what a `DEFEND` step means with no incoming attack.
- Physical-plausibility rules: which actions are provably impossible (the validation seam exists; the baseline proves nothing impossible).
- Unarmed attacks (punches, kicks): method, template, damage and trauma. They are not part of the current action vocabulary.

## Body, Conditions and Recovery

- Condition behaviour (`BLEEDING`, `FRACTURED`, `BURNED`, `POISONED`).
- Body-part severity escalation thresholds.
- Existing-injury trauma modifier for a `DESTROYED` part (the `GAME_RULES.md` table covers healthy to crippled only).
- Healing and recovery-item effects.

## Enemies

- Exact enemy numbers: stats, HP, protection.
- Enemy anatomy (which body parts each enemy has), and the `BODY_PART_NOT_PRESENT` validation code.
- Enemy utility weights.

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
- Entity/object ID format exposed to AI roles.
- How opaque player-owned action references (weapons, abilities, items) are minted and presented to the Action Interpreter; the validation contract accepts any non-blank opaque references.
- AI provider integration and structured-output schema/JSON codec for `ActionIntent`, including interpreter-side `ACTION_NOT_SUPPORTED` / `INTERPRETATION_FAILED` handling.
- Action history persistence. `PlayerSceneView` currently exposes scene-local IDs and never UUIDs (no scene, exit-destination or run identities); the final AI-facing format is still undecided.
