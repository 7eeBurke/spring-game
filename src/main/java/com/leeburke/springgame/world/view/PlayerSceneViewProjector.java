package com.leeburke.springgame.world.view;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleEntity;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleHazard;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleObject;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/**
 * Projects authoritative {@link SceneState} into a safe {@link PlayerSceneView}.
 * <p>
 * Deciding which zones are currently visible (line of sight) is the caller's responsibility; this
 * class owns only the safe filtering of what may be exposed given those zones:
 * <ul>
 * <li>hidden zones are never shown, even if the caller lists them as visible;</li>
 * <li>hidden content is never shown;</li>
 * <li>content located in a zone that is not effectively visible is never shown;</li>
 * <li>a connection is shown only if it is not hidden and both its zones are visible;</li>
 * <li>exits never reveal their destination scene.</li>
 * </ul>
 */
public final class PlayerSceneViewProjector {

	private PlayerSceneViewProjector() {
	}

	/**
	 * @param visibleZoneIds zones the caller has determined are currently visible
	 * @throws IllegalArgumentException if a listed zone does not exist, or the current zone does not
	 *                                  exist, is hidden or is not among the visible zones
	 */
	public static PlayerSceneView project(SceneState state, String currentZoneId, Set<String> visibleZoneIds) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(currentZoneId, "currentZoneId");
		Objects.requireNonNull(visibleZoneIds, "visibleZoneIds");

		for (String zoneId : visibleZoneIds) {
			if (!state.hasZone(zoneId)) {
				throw new IllegalArgumentException("Visible zone " + zoneId + " does not exist in the scene");
			}
		}
		if (!state.hasZone(currentZoneId)) {
			throw new IllegalArgumentException("Current zone " + currentZoneId + " does not exist in the scene");
		}
		if (state.isHidden(HiddenContentKind.ZONE, currentZoneId)) {
			throw new IllegalArgumentException("Current zone " + currentZoneId + " is hidden");
		}
		if (!visibleZoneIds.contains(currentZoneId)) {
			throw new IllegalArgumentException("Current zone " + currentZoneId + " is not among the visible zones");
		}

		Set<String> visible = new HashSet<>();
		for (SceneZone zone : state.zones()) {
			if (visibleZoneIds.contains(zone.id()) && !state.isHidden(HiddenContentKind.ZONE, zone.id())) {
				visible.add(zone.id());
			}
		}

		List<VisibleZone> zones = state.zones().stream()
				.filter(zone -> visible.contains(zone.id()))
				.map(zone -> new VisibleZone(zone.id(), zone.displayName()))
				.toList();
		List<VisibleConnection> connections = state.connections().stream()
				.filter(c -> !state.isHidden(HiddenContentKind.CONNECTION, c.id()))
				.filter(c -> visible.contains(c.zoneA()) && visible.contains(c.zoneB()))
				.map(c -> new VisibleConnection(c.zoneA(), c.zoneB()))
				.toList();
		List<VisibleEntity> entities = state.entities().stream()
				.filter(e -> !state.isHidden(HiddenContentKind.ENTITY, e.id()) && visible.contains(e.zoneId()))
				.map(e -> new VisibleEntity(e.id(), e.definitionCode(), e.zoneId()))
				.toList();
		List<VisibleObject> objects = state.objects().stream()
				.filter(o -> !state.isHidden(HiddenContentKind.OBJECT, o.id()) && visible.contains(o.zoneId()))
				.map(o -> new VisibleObject(o.id(), o.definitionCode(), o.zoneId()))
				.toList();
		List<VisibleHazard> hazards = state.hazards().stream()
				.filter(h -> !state.isHidden(HiddenContentKind.HAZARD, h.id()) && visible.contains(h.zoneId()))
				.map(h -> new VisibleHazard(h.id(), h.definitionCode(), h.zoneId()))
				.toList();
		List<KnownExit> exits = state.exits().stream()
				.filter(x -> !state.isHidden(HiddenContentKind.EXIT, x.id()) && visible.contains(x.zoneId()))
				.map(x -> new KnownExit(x.id(), x.zoneId()))
				.toList();

		return new PlayerSceneView(currentZoneId, zones, connections, entities, objects, hazards, exits,
				state.discoveredFacts());
	}
}
