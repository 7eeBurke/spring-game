package com.leeburke.springgame.world.generation;

import static com.leeburke.springgame.world.generation.GenerationTestSupport.CONTENT;
import static com.leeburke.springgame.world.generation.GenerationTestSupport.RUN;
import static com.leeburke.springgame.world.generation.GenerationTestSupport.sequentialIds;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.leeburke.springgame.ai.narration.PlaceDescriber;
import com.leeburke.springgame.content.world.RegionDefinition;
import com.leeburke.springgame.content.world.SceneArchetypeDefinition;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneViewProjector;

/**
 * The Hollow Chapel's generated geography agrees with how it is described: the west doors open into
 * a place that can lie behind them, at its entrance; every way back toward the doors leaves from a
 * scene's entrance and every way deeper from elsewhere; and no two ways out of a scene read alike.
 */
class ChapelGeometryTest {

	private static final int SEEDS = 80;
	private static final RegionDefinition CHAPEL = CONTENT.findRegion("HOLLOW_CHAPEL").orElseThrow();
	private static List<GeneratedRunWorld> worlds;

	@BeforeAll
	static void generate() {
		worlds = new ArrayList<>();
		for (long seed = 0; seed < SEEDS; seed++) {
			worlds.add(new RunWorldGenerator(CONTENT, sequentialIds()).generate(RUN, seed, GenerationContextSnapshot.empty()));
		}
	}

	private static SceneInstance entry(GeneratedRunWorld world) {
		return scene(world, world.region().entrySceneId());
	}

	private static SceneInstance scene(GeneratedRunWorld world, UUID id) {
		return world.region().scenes().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
	}

	private static SceneArchetypeDefinition archetype(SceneInstance scene) {
		return CONTENT.findArchetype(scene.definitionCode()).orElseThrow();
	}

	/** Each scene's distance in passages from the region's first scene. */
	private static Map<UUID, Integer> depths(GeneratedRunWorld world) {
		Map<UUID, Integer> depth = new HashMap<>();
		java.util.ArrayDeque<UUID> queue = new java.util.ArrayDeque<>();
		depth.put(world.region().entrySceneId(), 0);
		queue.add(world.region().entrySceneId());
		while (!queue.isEmpty()) {
			SceneInstance at = scene(world, queue.poll());
			for (SceneExit exit : at.state().exits()) {
				boolean inRegion = world.region().scenes().stream().anyMatch(s -> s.id().equals(exit.destinationSceneId()));
				if (inRegion && !depth.containsKey(exit.destinationSceneId())) {
					depth.put(exit.destinationSceneId(), depth.get(at.id()) + 1);
					queue.add(exit.destinationSceneId());
				}
			}
		}
		return depth;
	}

	@Test
	void theWestDoorsOpenIntoANaveOrUnderTheBellTowerAtItsEntrance() {
		Set<String> openings = new HashSet<>();
		for (GeneratedRunWorld world : worlds) {
			SceneInstance first = entry(world);
			openings.add(first.definitionCode());
			assertThat(CHAPEL.openingArchetypes()).contains(first.definitionCode());
			SceneExit road = first.state().exits().stream().filter(x -> x.id().equals(RunWorldGenerator.HUB_RETURN_EXIT_ID)).findFirst()
					.orElseThrow();
			assertThat(road.zoneId()).as("the road back leaves from the entrance").isEqualTo(archetype(first).entranceZone());
			if (first.definitionCode().equals("RUINED_NAVE")) {
				assertThat(road.zoneId()).as("the west doors lead to the west end, never the apse").isEqualTo("nave_entrance");
				assertThat(first.state().exits().stream().filter(x -> !x.id().equals(road.id())))
						.as("the way deeper is at the east end").allMatch(x -> x.zoneId().equals("apse"));
			}
			// The player arrives where the road comes in, so the onward ways are elsewhere and reachable.
			assertThat(first.state().exits().stream().filter(x -> !x.id().equals(road.id())).map(SceneExit::zoneId))
					.isNotEmpty().allMatch(zone -> !zone.equals(road.zoneId()));
		}
		assertThat(openings).as("variety is kept among the places that can lie behind the doors")
				.containsExactlyInAnyOrderElementsOf(CHAPEL.openingArchetypes());
	}

	@Test
	void everyWayBackLeavesFromTheEntranceAndEveryWayDeeperFromElsewhere() {
		for (GeneratedRunWorld world : worlds) {
			Map<UUID, Integer> depth = depths(world);
			for (SceneInstance scene : world.region().scenes()) {
				SceneArchetypeDefinition archetype = archetype(scene);
				boolean otherExitZones = archetype.exitZones().size() > 1;
				for (SceneExit exit : scene.state().exits()) {
					Integer there = depth.get(exit.destinationSceneId());
					boolean inward = there == null || there < depth.get(scene.id());
					if (inward) {
						assertThat(exit.zoneId()).as(scene.definitionCode() + " " + exit.id() + " goes back").isEqualTo(archetype.entranceZone());
					} else if (otherExitZones) {
						assertThat(exit.zoneId()).as(scene.definitionCode() + " " + exit.id() + " goes deeper").isNotEqualTo(archetype.entranceZone());
					}
				}
			}
			assertThat(depth).as("every scene, the guardian's included, can be reached from the doors")
					.containsKeys(world.region().scenes().stream().map(SceneInstance::id).toArray(UUID[]::new));
		}
	}

	@Test
	void noTwoWaysOutOfAScenePlaceReadAlike() {
		for (GeneratedRunWorld world : worlds) {
			for (SceneInstance scene : world.region().scenes()) {
				Set<String> all = scene.state().zones().stream().map(SceneZone::id).collect(Collectors.toSet());
				PlayerSceneView everything = PlayerSceneViewProjector.project(scene.state(), scene.state().zones().getFirst().id(), all);
				PlaceDescriber places = new PlaceDescriber(CONTENT, scene.definitionCode(), everything, Set.of(), Map.of(), code -> code);
				List<String> passages = scene.state().exits().stream().map(x -> places.wayPassage(x.id())).toList();
				assertThat(passages).as(scene.definitionCode() + " " + scene.state().exits()).doesNotHaveDuplicates()
						.doesNotContain("a way out");
				if (scene.id().equals(world.region().entrySceneId())) {
					assertThat(places.wayPassage(RunWorldGenerator.HUB_RETURN_EXIT_ID)).contains("west doors").contains("chapel road");
				}
			}
		}
	}

	@Test
	void onlyTheNaveSpeaksOfWestAndEastWhereItsEntranceMakesThatTrue() {
		CONTENT.texts().scenes().forEach((code, texts) -> {
			if (code.equals("RUINED_NAVE") || code.equals("THE_LAST_LANTERN")) {
				return;
			}
			List<String> words = new ArrayList<>();
			texts.zones().values().forEach(z -> words.add(z.phrase() + " " + z.description()));
			words.addAll(texts.connections().values());
			texts.exits().values().forEach(words::addAll);
			words.add(texts.description());
			assertThat(words).as(code).allSatisfy(text -> assertThat(text.toLowerCase(Locale.ROOT))
					.doesNotContainPattern("\\b(west|east|north|south)\\b"));
		});
		// In the nave, the west end is its entrance and the east end its apse.
		assertThat(CONTENT.texts().scene("RUINED_NAVE").orElseThrow().zones().get("nave_entrance").phrase()).contains("west");
		assertThat(CONTENT.findArchetype("RUINED_NAVE").orElseThrow().entranceZone()).isEqualTo("nave_entrance");
		assertThat(CONTENT.texts().scene("RUINED_NAVE").orElseThrow().zones().get("apse").description()).contains("east");
	}

	@Test
	void theSameSeedGivesTheSameChapel() {
		GeneratedRunWorld again = new RunWorldGenerator(CONTENT, sequentialIds()).generate(RUN, 11, GenerationContextSnapshot.empty());
		assertThat(again).isEqualTo(worlds.get(11));
	}
}
