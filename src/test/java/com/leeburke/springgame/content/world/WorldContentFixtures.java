package com.leeburke.springgame.content.world;

import java.util.List;

import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.ZoneConnection;

/** Small hand-built world content for catalogue tests. */
final class WorldContentFixtures {

	private WorldContentFixtures() {
	}

	static WorldElementDefinition element(String code, WorldElementKind kind) {
		return new WorldElementDefinition(code, "Name " + code, kind);
	}

	static List<WorldElementDefinition> elements() {
		return List.of(
				element("GRUNT", WorldElementKind.ENTITY),
				element("BOSS", WorldElementKind.ENTITY),
				element("BARREL", WorldElementKind.OBJECT),
				element("SPIKES", WorldElementKind.HAZARD),
				element("OMEN", WorldElementKind.EVENT));
	}

	static ContentSlot slot(String id, WorldElementKind kind, List<String> candidates, int chance, int hiddenChance) {
		return new ContentSlot(id, kind, candidates, List.of("a"), chance, hiddenChance);
	}

	/** Two connected zones {@code a} and {@code b}. */
	static SceneArchetypeDefinition archetype(String code, List<ContentSlot> slots) {
		return new SceneArchetypeDefinition(code, "Name " + code,
				List.of(new SceneZone("a", "A"), new SceneZone("b", "B")),
				List.of(new ZoneConnection("a_b", "a", "b")),
				List.of("a"), slots);
	}

	static SceneArchetypeDefinition bossArchetype() {
		return archetype("ARENA", List.of(slot("boss", WorldElementKind.ENTITY, List.of("BOSS"), 100, 0)));
	}

	static List<SceneArchetypeDefinition> archetypes() {
		return List.of(
				archetype("ROOM_A", List.of(slot("grunt", WorldElementKind.ENTITY, List.of("GRUNT"), 50, 10))),
				archetype("ROOM_B", List.of(slot("barrel", WorldElementKind.OBJECT, List.of("BARREL"), 60, 0))),
				archetype("ROOM_C", List.of()),
				bossArchetype());
	}

	static RegionDefinition region(String code) {
		return new RegionDefinition(code, "Name " + code, List.of("ROOM_A", "ROOM_B", "ROOM_C"), "ARENA", "BOSS",
				new IntRange(5, 7), new IntRange(0, 2), new IntRange(1, 2));
	}

	static FixedSceneDefinition hub(String destinationRegion) {
		return new FixedSceneDefinition("HUB", "Hub",
				List.of(new SceneZone("hearth", "Hearth"), new SceneZone("road", "Road")),
				List.of(new ZoneConnection("hearth_road", "hearth", "road")),
				"hearth", new FixedSceneDefinition.FixedSceneExit("to_region", "road", destinationRegion));
	}
}
