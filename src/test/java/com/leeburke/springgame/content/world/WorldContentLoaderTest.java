package com.leeburke.springgame.content.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.leeburke.springgame.content.ContentLoadException;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.ZoneConnection;

class WorldContentLoaderTest {

	private static final String FIXTURES = "content-fixtures/world";

	private static WorldContentCatalog bundled;

	@BeforeAll
	static void load() {
		bundled = WorldContentLoader.loadBundled();
	}

	@Test
	void bundledHollowChapelDefinition() {
		RegionDefinition chapel = bundled.findRegion("HOLLOW_CHAPEL").orElseThrow();
		assertThat(chapel.displayName()).isEqualTo("Hollow Chapel");
		assertThat(chapel.normalArchetypes())
				.containsExactly("RUINED_NAVE", "CLOISTER", "SACRISTY", "OSSUARY", "BELL_PASSAGE", "RELIQUARY");
		assertThat(chapel.bossArchetype()).isEqualTo("GUARDIAN_SANCTUM");
		assertThat(chapel.bossEntity()).isEqualTo("CHAPEL_GUARDIAN");
		assertThat(chapel.requiredScenes()).isEqualTo(new IntRange(5, 7));
		assertThat(chapel.optionalScenes()).isEqualTo(new IntRange(0, 2));
		assertThat(chapel.branches()).isEqualTo(new IntRange(1, 2));
	}

	@Test
	void bundledArchetypesHaveTheirAuthoredZones() {
		Map<String, List<String>> zones = Map.of(
				"RUINED_NAVE", List.of("nave_entrance", "central_aisle", "collapsed_pews", "apse"),
				"CLOISTER", List.of("cloister_walk", "overgrown_garth", "broken_arcade"),
				"SACRISTY", List.of("vestry_door", "vestment_racks", "locked_alcove"),
				"OSSUARY", List.of("bone_stair", "skull_niches", "charnel_pit", "warden_post"),
				"BELL_PASSAGE", List.of("bell_landing", "rope_gallery", "cracked_belfry"),
				"RELIQUARY", List.of("reliquary_gate", "relic_shelves", "sealed_reliquary", "side_chapel"),
				"GUARDIAN_SANCTUM", List.of("sanctum_threshold", "guardian_dais", "pillared_flank", "ember_choir"));
		assertThat(bundled.archetypes()).hasSize(7);
		zones.forEach((code, expected) -> assertThat(bundled.findArchetype(code).orElseThrow().zones())
				.extracting(SceneZone::id).as(code).containsExactlyElementsOf(expected));
	}

	@Test
	void bossArchetypeAlwaysPlacesVisibleGuardianOnDais() {
		ContentSlot guardian = bundled.findArchetype("GUARDIAN_SANCTUM").orElseThrow().slots().getFirst();
		assertThat(guardian).isEqualTo(new ContentSlot("guardian", WorldElementKind.ENTITY, List.of("CHAPEL_GUARDIAN"),
				List.of("guardian_dais"), 100, 0));
	}

	@ParameterizedTest
	@CsvSource({
			"HOLLOW_ACOLYTE,ENTITY", "BONE_WARDEN,ENTITY", "ASHBOUND_PENITENT,ENTITY", "CHAPEL_GUARDIAN,ENTITY",
			"WOODEN_PEW,OBJECT", "STONE_PILLAR,OBJECT", "ALTAR,OBJECT", "WOODEN_DOOR,OBJECT", "CHAIN,OBJECT",
			"CORPSE,OBJECT", "CRATE,OBJECT",
			"UNSTABLE_CEILING,HAZARD", "FIRE,HAZARD", "PRESSURE_PLATE,HAZARD", "COLLAPSING_FLOOR,HAZARD",
			"FERRYMAN_OF_ASH,EVENT", "WOUNDED_PILGRIM,EVENT", "FALSE_BLESSING,EVENT", "HIDDEN_PRAYER_SHARD,EVENT",
			"RELIQUARY_BARGAIN,EVENT" })
	void bundledWorldElements(String code, WorldElementKind kind) {
		assertThat(bundled.findElement(code).orElseThrow().kind()).isEqualTo(kind);
	}

	@Test
	void bundledElementCount() {
		assertThat(bundled.elements()).hasSize(20);
	}

	@Test
	void bundledLastLantern() {
		FixedSceneDefinition lantern = bundled.findFixedScene("THE_LAST_LANTERN").orElseThrow();
		assertThat(lantern.displayName()).isEqualTo("The Last Lantern");
		assertThat(lantern.zones()).containsExactly(
				new SceneZone("lantern_hearth", "Lantern Hearth"), new SceneZone("chapel_road", "Chapel Road"));
		assertThat(lantern.connections()).containsExactly(new ZoneConnection("hearth_road", "lantern_hearth", "chapel_road"));
		assertThat(lantern.startZone()).isEqualTo("lantern_hearth");
		assertThat(lantern.exit()).isEqualTo(new FixedSceneDefinition.FixedSceneExit("road_to_chapel", "chapel_road", "HOLLOW_CHAPEL"));
	}

	@ParameterizedTest
	@CsvSource({ "malformed.json", "unknown-property.json", "missing-field.json" })
	void invalidElementFilesFailWithResourceName(String fixture) {
		String path = FIXTURES + "/" + fixture;
		assertThatThrownBy(() -> new WorldContentLoader(FIXTURES).readDefinitions(path, WorldElementDefinition.class))
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining(path);
	}

	@Test
	void wrongTypeFailsWithResourceName() {
		String path = FIXTURES + "/wrong-type-slot.json";
		assertThatThrownBy(() -> new WorldContentLoader(FIXTURES).readDefinitions(path, ContentSlot.class))
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining(path);
	}

	@Test
	void unknownElementReferenceFailsFullLoad() {
		assertThatThrownBy(() -> new WorldContentLoader(FIXTURES + "/unknown-element").load())
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining("GHOST_BARREL");
	}

	@Test
	void missingResourceFailsClearly() {
		assertThatThrownBy(() -> new WorldContentLoader(FIXTURES + "/does-not-exist").load())
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining("world-elements.json");
	}
}
