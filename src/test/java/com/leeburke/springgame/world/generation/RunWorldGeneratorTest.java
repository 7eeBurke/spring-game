package com.leeburke.springgame.world.generation;

import static com.leeburke.springgame.world.generation.GenerationTestSupport.CONTENT;
import static com.leeburke.springgame.world.generation.GenerationTestSupport.RUN;
import static com.leeburke.springgame.world.generation.GenerationTestSupport.descendingIds;
import static com.leeburke.springgame.world.generation.GenerationTestSupport.sequentialIds;
import static com.leeburke.springgame.world.generation.GenerationTestSupport.shape;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.leeburke.springgame.content.world.RegionDefinition;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.HiddenContentRef;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKind;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneViewProjector;

class RunWorldGeneratorTest {

	private static final RegionDefinition CHAPEL = CONTENT.findRegion("HOLLOW_CHAPEL").orElseThrow();
	private static final int SEEDS = 200;

	private static List<GeneratedRunWorld> worlds;

	@BeforeAll
	static void generateFixedSeeds() {
		worlds = new ArrayList<>();
		for (long seed = 0; seed < SEEDS; seed++) {
			worlds.add(generate(seed, GenerationContextSnapshot.empty()));
		}
	}

	private static GeneratedRunWorld generate(long seed, GenerationContextSnapshot context) {
		return new RunWorldGenerator(CONTENT, sequentialIds()).generate(RUN, seed, context);
	}

	private static int branchCount(GeneratedRegion region) {
		return (int) region.routeStages().stream().filter(stage -> stage.size() == 2).count();
	}

	private static int requiredCount(GeneratedRegion region) {
		return region.routeStages().stream().mapToInt(List::size).sum();
	}

	private static List<SceneInstance> normalScenes(GeneratedRunWorld world) {
		return world.region().scenes().stream().filter(s -> !s.id().equals(world.region().bossSceneId())).toList();
	}

	// --- Structural invariants across fixed seeds ---

	@Test
	void countsStayWithinTheRegionDefinition() {
		for (GeneratedRunWorld world : worlds) {
			GeneratedRegion region = world.region();
			assertThat(CHAPEL.requiredScenes().contains(requiredCount(region))).isTrue();
			assertThat(CHAPEL.optionalScenes().contains(region.optionalSceneIds().size())).isTrue();
			assertThat(CHAPEL.branches().contains(branchCount(region))).isTrue();
			assertThat(region.scenes()).hasSize(requiredCount(region) + region.optionalSceneIds().size() + 1);
		}
	}

	@Test
	void everyWorldPassesTheCompleteRegionValidator() {
		CompleteRegionValidator validator = new CompleteRegionValidator(CHAPEL);
		for (GeneratedRunWorld world : worlds) {
			assertThat(validator.problems(world.region())).isEmpty();
		}
	}

	@Test
	void exactlyOneBossArenaWithTheGuardianOnlyThere() {
		for (GeneratedRunWorld world : worlds) {
			List<SceneInstance> arenas = world.region().scenes().stream()
					.filter(s -> s.definitionCode().equals("GUARDIAN_SANCTUM")).toList();
			assertThat(arenas).hasSize(1);
			assertThat(arenas.getFirst().id()).isEqualTo(world.region().bossSceneId());
			for (SceneInstance scene : world.region().scenes()) {
				long guardians = scene.state().entities().stream().filter(e -> e.definitionCode().equals("CHAPEL_GUARDIAN")).count();
				assertThat(guardians).isEqualTo(scene.id().equals(world.region().bossSceneId()) ? 1 : 0);
			}
		}
	}

	@Test
	void everyRegionSceneIsNewSeededAndOwned() {
		for (GeneratedRunWorld world : worlds) {
			UUID regionId = world.region().region().id();
			for (SceneInstance scene : world.region().scenes()) {
				assertThat(scene.kind()).isEqualTo(SceneKind.REGION);
				assertThat(scene.revision()).isZero();
				assertThat(scene.sceneSeed()).isPresent();
				assertThat(scene.regionInstanceId()).contains(regionId);
				assertThat(scene.runId()).isEqualTo(RUN);
				assertThat(CHAPEL.normalArchetypes().contains(scene.definitionCode())
						|| scene.id().equals(world.region().bossSceneId())).isTrue();
			}
		}
	}

	@Test
	void exitsResolveAreVisibleAndEverySceneIsReachable() {
		for (GeneratedRunWorld world : worlds) {
			Set<UUID> ids = world.region().sceneIds();
			for (SceneInstance scene : world.region().scenes()) {
				for (SceneExit exit : scene.state().exits()) {
					boolean toHub = exit.destinationSceneId().equals(world.hub().id());
					assertThat(ids.contains(exit.destinationSceneId()) || toHub).isTrue();
					assertThat(scene.state().isHidden(HiddenContentKind.EXIT, exit.id())).isFalse();
				}
				assertThat(scene.state().hiddenContent()).noneMatch(ref -> ref.kind() == HiddenContentKind.ZONE);
			}
			Set<UUID> reached = new HashSet<>();
			List<UUID> queue = new ArrayList<>(List.of(world.region().entrySceneId()));
			while (!queue.isEmpty()) {
				UUID id = queue.removeFirst();
				if (ids.contains(id) && reached.add(id)) {
					world.region().scene(id).orElseThrow().state().exits().forEach(e -> queue.add(e.destinationSceneId()));
				}
			}
			assertThat(reached).isEqualTo(ids).contains(world.region().bossSceneId());
		}
	}

	@Test
	void adjacentNormalScenesNeverShareAnArchetype() {
		for (GeneratedRunWorld world : worlds) {
			for (SceneInstance scene : normalScenes(world)) {
				for (SceneExit exit : scene.state().exits()) {
					world.region().scene(exit.destinationSceneId())
							.filter(next -> !next.id().equals(world.region().bossSceneId()))
							.ifPresent(next -> assertThat(next.definitionCode()).isNotEqualTo(scene.definitionCode()));
				}
			}
		}
	}

	@Test
	void allSixArchetypesAppearBeforeReuse() {
		for (GeneratedRunWorld world : worlds) {
			List<String> codes = normalScenes(world).stream().map(SceneInstance::definitionCode).toList();
			int distinct = new HashSet<>(codes).size();
			assertThat(distinct).isEqualTo(Math.min(codes.size(), 6));
		}
	}

	@Test
	void hubIsFixedAndLinkedBothWays() {
		for (GeneratedRunWorld world : worlds) {
			SceneInstance hub = world.hub();
			assertThat(hub.kind()).isEqualTo(SceneKind.HUB);
			assertThat(hub.definitionCode()).isEqualTo("THE_LAST_LANTERN");
			assertThat(hub.regionInstanceId()).isEmpty();
			assertThat(hub.sceneSeed()).isEmpty();
			assertThat(hub.revision()).isZero();
			assertThat(hub.state().zones()).extracting("id").containsExactly("lantern_hearth", "chapel_road");
			assertThat(hub.state().entities()).isEmpty();
			assertThat(hub.state().hiddenContent()).isEmpty();
			assertThat(hub.state().exits()).containsExactly(
					new SceneExit("road_to_chapel", "chapel_road", world.region().entrySceneId()));
			SceneInstance entry = world.region().scene(world.region().entrySceneId()).orElseThrow();
			assertThat(entry.state().exits()).filteredOn(e -> e.id().equals("lantern_road"))
					.extracting(SceneExit::destinationSceneId).containsExactly(hub.id());
			assertThat(world.start()).isEqualTo(new PlayerLocation(hub.id(), "lantern_hearth"));
		}
	}

	// --- Variation across fixed seeds (deterministic: these seeds always produce these results) ---

	@Test
	void quietScenesExist() {
		boolean quiet = worlds.stream().flatMap(w -> normalScenes(w).stream()).map(SceneInstance::state)
				.anyMatch(s -> s.entities().isEmpty() && s.objects().isEmpty() && s.hazards().isEmpty() && s.activeEvents().isEmpty());
		assertThat(quiet).isTrue();
	}

	@Test
	void contentPlacementVaries() {
		Set<String> navEnemyZones = new HashSet<>();
		Set<String> navEnemyCodes = new HashSet<>();
		boolean navEnemyPresent = false;
		boolean navEnemyAbsent = false;
		boolean anyHidden = false;
		for (GeneratedRunWorld world : worlds) {
			for (SceneInstance scene : normalScenes(world)) {
				anyHidden |= !scene.state().hiddenContent().isEmpty();
				if (scene.definitionCode().equals("RUINED_NAVE")) {
					Optional<SceneEntity> enemy = scene.state().entities().stream().filter(e -> e.id().equals("nave_enemy")).findFirst();
					navEnemyPresent |= enemy.isPresent();
					navEnemyAbsent |= enemy.isEmpty();
					enemy.ifPresent(e -> {
						navEnemyZones.add(e.zoneId());
						navEnemyCodes.add(e.definitionCode());
					});
				}
			}
		}
		assertThat(navEnemyPresent).isTrue();
		assertThat(navEnemyAbsent).isTrue();
		assertThat(navEnemyZones).hasSizeGreaterThan(1);
		assertThat(navEnemyCodes).hasSizeGreaterThan(1);
		assertThat(anyHidden).isTrue();
	}

	@Test
	void topologiesVary() {
		Set<List<Integer>> shapes = worlds.stream()
				.map(w -> w.region().routeStages().stream().map(List::size).toList())
				.collect(Collectors.toSet());
		assertThat(shapes).hasSizeGreaterThan(1);
	}

	// --- Player-safe view over generated content ---

	@Test
	void generatedHiddenContentNeverReachesThePlayerView() {
		SceneInstance scene = worlds.stream().flatMap(w -> w.region().scenes().stream())
				.filter(s -> s.state().hiddenContent().stream().anyMatch(ref -> ref.kind() != HiddenContentKind.ZONE))
				.findFirst().orElseThrow();
		SceneState state = scene.state();
		Set<String> allZones = state.zones().stream().map(z -> z.id()).collect(Collectors.toSet());

		PlayerSceneView view = PlayerSceneViewProjector.project(state, state.zones().getFirst().id(), allZones);

		String rendered = view.toString();
		for (HiddenContentRef ref : state.hiddenContent()) {
			assertThat(rendered).as("hidden %s", ref).doesNotContain(ref.localId());
		}
		for (SceneExit exit : state.exits()) {
			assertThat(rendered).doesNotContain(exit.destinationSceneId().toString());
		}
	}

	// --- Determinism ---

	@Test
	void sameInputsAndIdSourceGiveEqualWorlds() {
		GenerationContextSnapshot context = new GenerationContextSnapshot(List.of("CLOISTER"));
		assertThat(generate(11, context)).isEqualTo(generate(11, context));
	}

	@Test
	void sameSeedAndSnapshotGiveSameShapeWhateverTheIds() {
		GenerationContextSnapshot context = GenerationContextSnapshot.empty();
		String expected = shape(generate(23, context));
		assertThat(shape(new RunWorldGenerator(CONTENT, IdSource.random()).generate(RUN, 23, context))).isEqualTo(expected);
		assertThat(shape(new RunWorldGenerator(CONTENT, descendingIds()).generate(RUN, 23, context))).isEqualTo(expected);
	}

	@Test
	void differentSeedsGiveDifferentShapes() {
		assertThat(shape(worlds.get(1))).isNotEqualTo(shape(worlds.get(2)));
	}

	@Test
	void recentOpeningSnapshotChangesTheOpenerWhenTheWeightsDiffer() {
		boolean found = false;
		for (long seed = 0; seed < SEEDS && !found; seed++) {
			GeneratedRunWorld plain = worlds.get((int) seed);
			String opener = plain.region().scene(plain.region().entrySceneId()).orElseThrow().definitionCode();
			GeneratedRunWorld avoided = generate(seed, new GenerationContextSnapshot(List.of(opener)));
			String newOpener = avoided.region().scene(avoided.region().entrySceneId()).orElseThrow().definitionCode();
			if (!newOpener.equals(opener)) {
				found = true;
				// Counts and layout are drawn before the opener, so they are unaffected.
				assertThat(avoided.region().routeStages().stream().map(List::size).toList())
						.isEqualTo(plain.region().routeStages().stream().map(List::size).toList());
				assertThat(avoided.region().optionalSceneIds()).hasSameSizeAs(plain.region().optionalSceneIds());
			}
		}
		assertThat(found).as("some fixed seed changes opener when it was the most recent").isTrue();
	}

	@Test
	void generationDoesNotChangeTheSnapshot() {
		GenerationContextSnapshot context = new GenerationContextSnapshot(List.of("OSSUARY", "CLOISTER"));
		GeneratedRunWorld world = generate(3, context);
		assertThat(world.context()).isSameAs(context);
		assertThat(context.recentOpeningArchetypeCodes()).containsExactly("OSSUARY", "CLOISTER");
	}

	// --- Deterministic retry ---

	@Test
	void rejectedFirstAttemptFallsThroughToSecond() {
		AtomicInteger calls = new AtomicInteger();
		CompleteRegionValidator real = new CompleteRegionValidator(CHAPEL);
		RegionValidator rejectFirst = candidate -> calls.getAndIncrement() == 0 ? List.of("forced rejection") : real.problems(candidate);

		GeneratedRunWorld world = new RunWorldGenerator(CONTENT, sequentialIds(), rejectFirst).generate(RUN, 31, GenerationContextSnapshot.empty());

		assertThat(calls).hasValue(2);
		RunWorldGenerator direct = new RunWorldGenerator(CONTENT, sequentialIds());
		UUID hubId = UUID.randomUUID();
		GeneratedRegion attemptOne = direct.generateRegion(RUN, 31, GenerationContextSnapshot.empty(), 1, hubId);
		assertThat(shape(world.region(), world.hub().id())).isEqualTo(shape(attemptOne, hubId));
	}

	@Test
	void allAttemptsInvalidThrowsAfterExactlyTheMaximum() {
		AtomicInteger calls = new AtomicInteger();
		RegionValidator rejectAll = candidate -> {
			calls.incrementAndGet();
			return List.of("always invalid");
		};
		RunWorldGenerator generator = new RunWorldGenerator(CONTENT, sequentialIds(), rejectAll);

		assertThatThrownBy(() -> generator.generate(RUN, 5, GenerationContextSnapshot.empty()))
				.isInstanceOf(GenerationException.class)
				.hasMessageContaining(RunWorldGenerator.MAX_ATTEMPTS + " attempts")
				.hasMessageContaining("always invalid");
		assertThat(calls).hasValue(RunWorldGenerator.MAX_ATTEMPTS);
	}

	// --- GeneratedRunWorld integrity (both link directions) ---

	private static SceneInstance withState(SceneInstance scene, SceneState state) {
		return new SceneInstance(scene.id(), scene.runId(), scene.definitionCode(), scene.placement(), scene.discovered(),
				scene.revision(), state);
	}

	private static SceneState withExits(SceneState s, List<SceneExit> exits) {
		return new SceneState(s.zones(), s.connections(), s.entities(), s.objects(), s.hazards(), exits, s.activeEvents(),
				s.environmentFlags(), s.hiddenContent(), s.discoveredFacts());
	}

	@Test
	void hubExitMustLeadToTheEntry() {
		GeneratedRunWorld world = worlds.getFirst();
		SceneInstance wrongHub = withState(world.hub(), withExits(world.hub().state(),
				List.of(new SceneExit("road_to_chapel", "chapel_road", world.region().bossSceneId()))));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new GeneratedRunWorld(RUN, world.context(), wrongHub, world.region(), world.start()));
	}

	@Test
	void entryReturnExitMustLeadToTheActualHub() {
		GeneratedRunWorld world = worlds.getFirst();
		GeneratedRegion region = world.region();
		SceneInstance entry = region.scene(region.entrySceneId()).orElseThrow();
		List<SceneExit> exits = entry.state().exits().stream()
				.map(e -> e.id().equals("lantern_road") ? new SceneExit(e.id(), e.zoneId(), UUID.randomUUID()) : e)
				.toList();
		SceneInstance wrongEntry = withState(entry, withExits(entry.state(), exits));
		List<SceneInstance> scenes = region.scenes().stream().map(s -> s.id().equals(entry.id()) ? wrongEntry : s).toList();
		GeneratedRegion wrongRegion = new GeneratedRegion(region.region(), region.routeStages(), region.optionalSceneIds(),
				region.bossSceneId(), scenes);

		assertThatIllegalArgumentException().isThrownBy(
				() -> new GeneratedRunWorld(RUN, world.context(), world.hub(), wrongRegion, world.start()));
	}

	@Test
	void startMustBeAVisibleHubZone() {
		GeneratedRunWorld world = worlds.getFirst();
		assertThatIllegalArgumentException().isThrownBy(() -> new GeneratedRunWorld(RUN, world.context(), world.hub(),
				world.region(), new PlayerLocation(world.hub().id(), "cellar")));
		assertThatIllegalArgumentException().isThrownBy(() -> new GeneratedRunWorld(RUN, world.context(), world.hub(),
				world.region(), new PlayerLocation(world.region().entrySceneId(), "lantern_hearth")));
	}

	@Test
	void everyPartMustBelongToTheRun() {
		GeneratedRunWorld world = worlds.getFirst();
		assertThatIllegalArgumentException().isThrownBy(() -> new GeneratedRunWorld(UUID.randomUUID(), world.context(),
				world.hub(), world.region(), world.start()));
	}

	@Test
	void validatorRejectsHandBuiltRegionWithoutGuardian() {
		GeneratedRegion region = worlds.getFirst().region();
		SceneInstance boss = region.scene(region.bossSceneId()).orElseThrow();
		SceneState s = boss.state();
		SceneState noGuardian = new SceneState(s.zones(), s.connections(), List.of(), s.objects(), s.hazards(), s.exits(),
				s.activeEvents(), s.environmentFlags(), s.hiddenContent(), s.discoveredFacts());
		List<SceneInstance> scenes = region.scenes().stream().map(sc -> sc.id().equals(boss.id()) ? withState(boss, noGuardian) : sc).toList();
		GeneratedRegion broken = new GeneratedRegion(region.region(), region.routeStages(), region.optionalSceneIds(),
				region.bossSceneId(), scenes);

		assertThat(new CompleteRegionValidator(CHAPEL).problems(broken)).anyMatch(p -> p.contains("CHAPEL_GUARDIAN"));
	}
}
