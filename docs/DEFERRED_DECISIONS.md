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
- Effectiveness determination (weapon/damage type vs target).
- Exact `WHILE` resolution semantics (beyond: genuinely simultaneous, subject to simultaneous-action complexity).
- Detailed social (`COMMUNICATE`) resolution rules.

## Body, Conditions and Recovery

- Condition behaviour (`BLEEDING`, `FRACTURED`, `BURNED`, `POISONED`).
- Body-part severity escalation thresholds.
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

## Generation

- Content weights: weapon affinity, passive, ability, starting items, loadout coherence effects.
- Name generation source.
- Scene-role → archetype compatibility.
- Scene content budgets.
- Boss-gate progression route mechanics.
- Anti-repeat history window and weighting strength.

## Presentation and Contracts

- Run summary contents.
- Entity/object ID format exposed to AI roles.
