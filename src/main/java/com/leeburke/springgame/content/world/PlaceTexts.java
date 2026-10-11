package com.leeburke.springgame.content.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * Authored physical descriptions, kept apart from the generated structure so that stored scenes
 * never change: looked up by scene code (archetype or fixed scene) and local ID when the world is
 * shown. A zone's {@code phrase} is how the world refers to it in prose ("the narrow alcove"), its
 * {@code description} what it physically is; a connection's text is the passage between two zones;
 * an exit zone has a list of distinct passages, one per way out that leaves from it (in exit-ID
 * order), so that two ways out of the same place never read alike. {@code entrances}, by scene
 * code, for the archetypes that may open a region: how the region's exterior doors look from
 * inside, the way back out to the road. Labels (display names) stay for headings and the map.
 */
public record PlaceTexts(Map<String, SceneTexts> scenes, Map<String, String> entrances, Map<String, String> elements) {

	public static final PlaceTexts NONE = new PlaceTexts(Map.of(), Map.of(), Map.of());

	public PlaceTexts {
		scenes = Map.copyOf(Objects.requireNonNull(scenes, "scenes"));
		entrances = Map.copyOf(Objects.requireNonNull(entrances, "entrances"));
		elements = Map.copyOf(Objects.requireNonNull(elements, "elements"));
	}

	public Optional<SceneTexts> scene(String code) {
		return Optional.ofNullable(scenes.get(code));
	}

	/** How the region's exterior doors look from inside this scene, when it may open a region. */
	public Optional<String> entrance(String sceneCode) {
		return Optional.ofNullable(entrances.get(sceneCode));
	}

	public Optional<String> element(String code) {
		return Optional.ofNullable(elements.get(code));
	}

	/** One scene's texts. */
	public record SceneTexts(String code, String description, Map<String, ZoneText> zones, Map<String, String> connections,
			Map<String, List<String>> exits) {
		public SceneTexts {
			DefinitionCodes.requireCode(code, "Place texts scene code");
			requireText(description, code + " description");
			zones = Map.copyOf(Objects.requireNonNull(zones, "zones"));
			connections = Map.copyOf(Objects.requireNonNull(connections, "connections"));
			exits = Map.copyOf(Objects.requireNonNull(exits, "exits"));
			connections.forEach((id, text) -> requireText(text, code + " connection " + id));
			exits.forEach((id, texts) -> {
				if (texts == null || texts.isEmpty()) {
					throw new IllegalArgumentException("Missing text: " + code + " exit " + id);
				}
				texts.forEach(text -> requireText(text, code + " exit " + id));
				if (texts.stream().distinct().count() != texts.size()) {
					throw new IllegalArgumentException("Repeated passage text: " + code + " exit " + id);
				}
			});
			exits = exits.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
					e -> List.copyOf(e.getValue())));
		}
	}

	/** How the world refers to a zone, and what it physically is. */
	public record ZoneText(String phrase, String description) {
		public ZoneText {
			requireText(phrase, "zone phrase");
			requireText(description, "zone description");
		}
	}

	/** The stored document shape: scenes as a list with their codes. */
	public record Document(List<SceneTexts> scenes, Map<String, String> entrances, Map<String, String> elements) {
		public PlaceTexts toTexts() {
			Map<String, SceneTexts> byCode = new LinkedHashMap<>();
			for (SceneTexts scene : scenes) {
				if (byCode.put(scene.code(), scene) != null) {
					throw new IllegalArgumentException("Duplicate place texts for scene " + scene.code());
				}
			}
			elements.forEach((code, text) -> requireText(text, "element " + code));
			entrances.forEach((code, text) -> {
				requireText(text, "entrance of " + code);
				if (!byCode.containsKey(code)) {
					throw new IllegalArgumentException("Entrance text for unknown scene " + code);
				}
			});
			return new PlaceTexts(byCode, entrances, elements);
		}
	}

	static void requireText(String text, String what) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Missing text: " + what);
		}
	}
}
