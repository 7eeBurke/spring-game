package com.leeburke.springgame.world.generation;

import static com.leeburke.springgame.world.generation.GenerationTestSupport.CONTENT;
import static com.leeburke.springgame.world.generation.GenerationTestSupport.RUN;
import static com.leeburke.springgame.world.generation.GenerationTestSupport.sequentialIds;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.leeburke.springgame.content.world.ContainerRules;
import com.leeburke.springgame.world.ContainerState;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKnowledge;
import com.leeburke.springgame.world.SceneObject;
import com.leeburke.springgame.world.SceneState;

/** Containers and the first find: seeded, persisted with the scene, and independent of the structure. */
class SceneFurnisherTest {

	private static final int SEEDS = 60;
	private static final ContainerRules RULES = CONTENT.containers();
	private static List<GeneratedRunWorld> worlds;

	@BeforeAll
	static void generate() {
		worlds = new ArrayList<>();
		for (long seed = 0; seed < SEEDS; seed++) {
			worlds.add(new RunWorldGenerator(CONTENT, sequentialIds()).generate(RUN, seed, GenerationContextSnapshot.empty()));
		}
	}

	private static SceneInstance entry(GeneratedRunWorld world) {
		return world.region().scenes().stream().filter(s -> s.id().equals(world.region().entrySceneId())).findFirst().orElseThrow();
	}

	@Test
	void everyNewRunsFirstSceneHasAUsefulFindInSightAndOneStepAway() {
		for (GeneratedRunWorld world : worlds) {
			SceneState state = entry(world).state();
			SceneObject find = state.objects().stream().filter(o -> o.id().equals(SceneFurnisher.FIRST_FIND_ID)).findFirst().orElseThrow();
			String arrival = state.exits().stream().filter(x -> x.id().equals(RunWorldGenerator.HUB_RETURN_EXIT_ID)).findFirst()
					.orElseThrow().zoneId();

			assertThat(find.definitionCode()).isEqualTo(RULES.firstFind().orElseThrow().object());
			assertThat(state.isHidden(HiddenContentKind.OBJECT, find.id())).isFalse();
			assertThat(SceneKnowledge.perceivable(state, arrival)).as("in sight on arrival").contains(find.zoneId());
			ContainerState contents = state.container(find.id()).orElseThrow();
			assertThat(contents.open()).isFalse();
			assertThat(contents.contents()).singleElement().satisfies(item -> assertThat(RULES.firstFind().orElseThrow().candidates()).contains(item));
		}
	}

	@Test
	void onlyTheFirstSceneHasAFirstFind() {
		for (GeneratedRunWorld world : worlds) {
			world.region().scenes().stream().filter(s -> !s.id().equals(world.region().entrySceneId()))
					.forEach(s -> assertThat(s.state().objects()).noneMatch(o -> o.id().equals(SceneFurnisher.FIRST_FIND_ID)));
		}
	}

	@Test
	void everyCrateHasStoredContentsFromItsListAndNothingElseIsAContainer() {
		for (GeneratedRunWorld world : worlds) {
			for (SceneInstance scene : world.region().scenes()) {
				SceneState state = scene.state();
				for (SceneObject object : state.objects()) {
					assertThat(state.container(object.id()).isPresent()).as(object.id()).isEqualTo(RULES.isContainer(object.definitionCode()));
				}
				for (ContainerState container : state.containers()) {
					assertThat(container.contents()).hasSizeLessThanOrEqualTo(1);
					String code = state.objects().stream().filter(o -> o.id().equals(container.objectId())).findFirst().orElseThrow()
							.definitionCode();
					Set<String> allowed = new java.util.HashSet<>(RULES.container(code).orElseThrow().candidates());
					RULES.firstFind().ifPresent(f -> allowed.addAll(f.candidates()));
					assertThat(allowed).containsAll(container.contents());
				}
				assertThat(state.seenZones()).as("a new scene starts unseen").contains(List.of());
			}
		}
	}

	@Test
	void sameSeedSameContentsAndDifferentSeedsDiffer() {
		GeneratedRunWorld again = new RunWorldGenerator(CONTENT, sequentialIds()).generate(RUN, 7, GenerationContextSnapshot.empty());
		assertThat(again).isEqualTo(worlds.get(7));
		Set<String> finds = new java.util.HashSet<>();
		worlds.forEach(w -> finds.add(entry(w).state().container(SceneFurnisher.FIRST_FIND_ID).orElseThrow().contents().getFirst()));
		assertThat(finds).as("the first find varies across seeds").hasSizeGreaterThan(1);
	}

	@Test
	void furnishingLeavesTheGeneratedStructureAsItWas() {
		SceneState generated = entry(worlds.get(3)).state();
		SceneState bare = new SceneState(generated.zones(), generated.connections(), generated.entities(),
				generated.objects().stream().filter(o -> !o.id().equals(SceneFurnisher.FIRST_FIND_ID)).toList(), generated.hazards(),
				generated.exits(), generated.activeEvents(), generated.environmentFlags(), generated.hiddenContent(),
				generated.discoveredFacts());

		SceneState furnished = SceneFurnisher.furnish(bare, 1234, RULES, Optional.of(RunWorldGenerator.HUB_RETURN_EXIT_ID));

		assertThat(furnished.entities()).isEqualTo(bare.entities());
		assertThat(furnished.hazards()).isEqualTo(bare.hazards());
		assertThat(furnished.exits()).isEqualTo(bare.exits());
		assertThat(furnished.hiddenContent()).isEqualTo(bare.hiddenContent());
		assertThat(furnished.objects()).containsAll(bare.objects()).hasSize(bare.objects().size() + 1);
		assertThat(SceneFurnisher.furnish(bare, 1234, RULES, Optional.of(RunWorldGenerator.HUB_RETURN_EXIT_ID))).isEqualTo(furnished);
	}
}
