package com.leeburke.springgame.ai.narration;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * Player-visible names for narration: display names of visible entities, objects and hazards
 * (numbered "Hollow Acolyte 2" when several share a kind, in order of local ID) and of visible
 * zones. Content the player cannot see has no name here, so it can never be described.
 */
public final class NarrationNames {

	private final Map<String, String> entities;
	private final Map<String, String> objects;
	private final Map<String, String> hazards;
	private final Map<String, String> zones;

	private NarrationNames(Map<String, String> entities, Map<String, String> objects, Map<String, String> hazards,
			Map<String, String> zones) {
		this.entities = entities;
		this.objects = objects;
		this.hazards = hazards;
		this.zones = zones;
	}

	public static NarrationNames of(PlayerSceneView view, WorldContentCatalog world) {
		Map<String, String> zones = view.zones().stream()
				.collect(Collectors.toMap(PlayerSceneView.VisibleZone::id, PlayerSceneView.VisibleZone::displayName));
		return new NarrationNames(
				named(view.entities(), PlayerSceneView.VisibleEntity::id, PlayerSceneView.VisibleEntity::definitionCode, world),
				named(view.objects(), PlayerSceneView.VisibleObject::id, PlayerSceneView.VisibleObject::definitionCode, world),
				named(view.hazards(), PlayerSceneView.VisibleHazard::id, PlayerSceneView.VisibleHazard::definitionCode, world),
				Map.copyOf(zones));
	}

	private static <T> Map<String, String> named(List<T> items, Function<T, String> id, Function<T, String> code,
			WorldContentCatalog world) {
		Map<String, String> names = new HashMap<>();
		items.stream().collect(Collectors.groupingBy(code)).forEach((definitionCode, group) -> {
			String name = world.findElement(definitionCode)
					.orElseThrow(() -> new IllegalArgumentException("Unknown world element " + definitionCode)).displayName();
			List<T> ordered = group.stream().sorted(Comparator.comparing(id)).toList();
			for (int i = 0; i < ordered.size(); i++) {
				names.put(id.apply(ordered.get(i)), ordered.size() == 1 ? name : name + " " + (i + 1));
			}
		});
		return Map.copyOf(names);
	}

	public Optional<String> entity(String entityId) {
		return Optional.ofNullable(entities.get(entityId));
	}

	public Optional<String> object(String objectId) {
		return Optional.ofNullable(objects.get(objectId));
	}

	public Optional<String> hazard(String hazardId) {
		return Optional.ofNullable(hazards.get(hazardId));
	}

	public Optional<String> zone(String zoneId) {
		return Optional.ofNullable(zones.get(zoneId));
	}
}
