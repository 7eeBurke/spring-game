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
- Exact `WHILE` resolution semantics (beyond: genuinely simultaneous, subject to simultaneous-action complexity).
- Detailed social (`COMMUNICATE`) resolution rules.

## Body, Conditions and Recovery

- Condition behaviour (`BLEEDING`, `FRACTURED`, `BURNED`, `POISONED`).
- Body-part severity escalation thresholds.
- Existing-injury trauma modifier for a `DESTROYED` part (the `GAME_RULES.md` table covers healthy to crippled only).
- Healing and recovery-item effects.

## Enemies

- Exact enemy numbers: stats, HP, protection.
- Enemy anatomy (which body parts each enemy has).
- Enemy utility weights.

## Content Mechanics

- Passive effects.
- Ability effects (beyond prototype ~2 uses per run).
- Utility tool effects.
- Hazard mechanics.
- Event mechanics and Fated variations.
- Rare Fated starting modifications.
- Fated consequences: use of the qualitative bands, rare events, enemy variants, rewards (only the numeric value is generated and stored).
- Weapon definition extensions: supported attack methods, suitability, tags, handedness, stat affinities, attack-form modifiers.
- Runtime item/weapon instance ID format (distinct from static definition codes).

## Generation

- Starting-content weighting: weapon stat affinity, passive/ability/item weighting, loadout coherence (`LOW`/`MEDIUM`/`HIGH`) and its effects, redundancy weighting. Character generation currently uses uniform independent selection within each pool as a baseline; this is not a decision to drop weighting.
- Scene-role → archetype compatibility.
- Scene content budgets.
- Boss-gate progression route mechanics.
- Anti-repeat history window and weighting strength.
- Cross-version deterministic replay and static-content versioning (reproduction currently assumes the same rules/content version). Persisted runs store definition codes; a run referencing a code that was later removed or renamed fails to load clearly. Migrating stored codes is deferred.

## Presentation and Contracts

- Run summary contents.
- Entity/object ID format exposed to AI roles.
