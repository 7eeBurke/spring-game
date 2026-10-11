package com.leeburke.springgame.game.view;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a client may show about a run, built only from player-visible state. References are
 * the same request-scoped aliases the interpreter and slash commands accept for this state version.
 * Never included: seeds, scene or region UUIDs, hidden content, exit destinations, enemy HP, stats,
 * traits or weights, difficulties, backend references or AI internals. Nullable fields are absent
 * information, not hidden information: {@code introduction} before it is generated,
 * {@code pendingAttack} when nothing is incoming, {@code lastTurn} before the first turn.
 *
 * @param awaiting   ACTION, DEFENSE (an attack is pending) or NONE (the run is over or not ready)
 * @param finalizing the last turn's mechanics are committed but its narration is not stored yet
 * @param objective  the player's opening direction, from the lore (a narrative direction, not a tracker)
 */
public record GameView(UUID runId, String status, long stateVersion, String awaiting, boolean finalizing,
		NarrationView introduction, String objective, CharacterView character, LocationView location, SceneView scene,
		PendingAttackView pendingAttack, LastTurnView lastTurn) {

	public record NarrationView(String text, String source) {
	}

	public record CharacterView(String name, int hp, int maxHp, Map<String, Integer> stats, int fated, String fatedBand,
			List<BodyPartView> body, List<OwnedView> weapons, List<OwnedView> items, List<OwnedView> abilities, String passive) {
	}

	public record BodyPartView(String part, String severity) {
	}

	public record OwnedView(String alias, String name) {
	}

	/** @param region the region's name, or null outside any region (the Last Lantern hub) */
	public record LocationView(String region, String scene, ZoneView zone) {
	}

	public record ZoneView(String alias, String name) {
	}

	/**
	 * @param leads what the player knows is left to explore here (never anything unseen or hidden)
	 */
	public record SceneView(List<ZoneView> zones, List<ConnectionView> connections, List<CreatureView> creatures,
			List<ThingView> objects, List<ThingView> hazards, List<ExitView> exits, LeadsView leads) {
	}

	/**
	 * @param unexploredExits known ways out (aliases) whose destination the player has not discovered
	 * @param unvisitedZones  known places (aliases) the player has not stood in; empty when visits are
	 *                        not recorded for this scene ({@code visitsRecorded} false)
	 */
	public record LeadsView(List<String> unexploredExits, List<String> unvisitedZones, boolean visitsRecorded) {
	}

	public record ConnectionView(String zoneA, String zoneB) {
	}

	/** @param condition ACTIVE or FALLEN */
	public record CreatureView(String alias, String name, String zone, String condition) {
	}

	/**
	 * A known object or hazard. {@code container}: whether a container is open and what it shows
	 * ("closed", "open, holding a Bandage", "open and empty"), null for anything else; {@code reach}:
	 * "here", "one step away", "two steps away", "farther" or "no known way".
	 */
	public record ThingView(String alias, String name, String zone, String container, String reach) {
	}

	/** @param leadsTo where the exit leads as far as the player knows; undiscovered places are never named */
	public record ExitView(String alias, String zone, String leadsTo) {
	}

	/**
	 * The incoming attack to defend against. {@code cueText} is Java's mandatory telegraph and is
	 * always present; {@code narration} may be null until the turn that created it is finalised.
	 */
	public record PendingAttackView(String alias, String attacker, String cueText, NarrationView narration) {
	}

	/** @param narration null while the turn is being finalised */
	public record LastTurnView(int turnNumber, NarrationView narration) {
	}
}
