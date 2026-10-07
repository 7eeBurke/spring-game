package com.leeburke.springgame.enemy;

import static com.leeburke.springgame.enemy.EnemyFixtures.CONTENT;
import static com.leeburke.springgame.enemy.EnemyFixtures.ENEMIES;
import static com.leeburke.springgame.enemy.EnemyFixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.run.initialization.RunInitialization;
import com.leeburke.springgame.run.initialization.RunInitializer;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.generation.GeneratedRegion;
import com.leeburke.springgame.world.generation.GeneratedRunWorld;
import com.leeburke.springgame.world.generation.GenerationContextSnapshot;
import com.leeburke.springgame.world.generation.IdSource;
import com.leeburke.springgame.world.generation.RunWorldGenerator;

class EnemyRosterTest {

	private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-00000000bbbb");

	private final EnemyRosterGenerator rosters = new EnemyRosterGenerator(ENEMIES);
	private final EnemyRosterValidator validator = new EnemyRosterValidator(ENEMIES, CONTENT);

	private static IdSource sequentialIds() {
		long[] next = { 0 };
		return () -> new UUID(0, ++next[0]);
	}

	private static GeneratedRunWorld world(long seed) {
		return new RunWorldGenerator(WORLD, sequentialIds()).generate(RUN, seed, GenerationContextSnapshot.empty());
	}

	@Test
	void everyEnemyPlacementGetsExactlyOneInstanceIncludingHiddenOnes() {
		boolean sawHiddenEnemy = false;
		for (long seed = 1; seed <= 40; seed++) {
			GeneratedRunWorld world = world(seed);
			GeneratedEnemyRoster roster = rosters.generate(world);

			assertThat(validator.problems(world, roster)).as("seed " + seed).isEmpty();
			int placements = 0;
			for (SceneInstance scene : world.region().scenes()) {
				for (SceneEntity entity : scene.state().entities()) {
					placements++;
					EnemyInstance enemy = roster.find(scene.id(), entity.id()).orElseThrow();
					assertThat(enemy.definitionCode()).isEqualTo(entity.definitionCode());
					sawHiddenEnemy |= scene.state().isHidden(HiddenContentKind.ENTITY, entity.id());
				}
			}
			assertThat(roster.enemies()).hasSize(placements);
			assertThat(roster.inScene(world.hub().id())).isEmpty();
		}
		assertThat(sawHiddenEnemy).as("some generated world hides an enemy").isTrue();
	}

	@Test
	void sameWorldGivesTheSameRoster() {
		assertThat(rosters.generate(world(9))).isEqualTo(rosters.generate(world(9)));
	}

	@Test
	void anEnemyDoesNotDependOnOtherEnemiesOrTheirOrder() {
		GeneratedRunWorld world = world(11);
		SceneInstance scene = world.region().scenes().stream()
				.filter(s -> s.state().entities().size() >= 2).findFirst()
				.orElseGet(() -> world.region().scene(world.region().bossSceneId()).orElseThrow());
		List<SceneEntity> entities = scene.state().entities();
		SceneEntity kept = entities.getLast();

		List<SceneEntity> reordered = new ArrayList<>(entities.reversed());
		GeneratedRunWorld changed = replaceEntities(world, scene.id(), s -> withEntities(s, reordered));
		List<SceneEntity> alone = List.of(kept);
		GeneratedRunWorld reduced = replaceEntities(world, scene.id(), s -> withEntities(s, alone));

		EnemyInstance original = rosters.generate(world).find(scene.id(), kept.id()).orElseThrow();
		assertThat(rosters.generate(changed).find(scene.id(), kept.id())).contains(original);
		assertThat(rosters.generate(reduced).find(scene.id(), kept.id())).contains(original);
	}

	@Test
	void validatorReportsEachKindOfCorruption() {
		GeneratedRunWorld world = world(5);
		GeneratedEnemyRoster roster = rosters.generate(world);
		SceneEnemy first = roster.enemies().getFirst();
		EnemyInstance enemy = first.enemy();

		assertThat(validator.problems(world, without(roster, first))).anyMatch(p -> p.contains("has no enemy state"));
		assertThat(validator.problems(world, replace(roster, first, new SceneEnemy(UUID.randomUUID(), enemy))))
				.anyMatch(p -> p.contains("unknown scene"));
		EnemyInstance wrongEntity = new EnemyInstance("ghost_9", enemy.definitionCode(), enemy.stats(), enemy.maxHp(),
				enemy.currentHp(), enemy.body(), enemy.weaponCode());
		assertThat(validator.problems(world, replace(roster, first, new SceneEnemy(first.sceneId(), wrongEntity))))
				.anyMatch(p -> p.contains("has no entity"));
		String otherCode = enemy.definitionCode().equals("BONE_WARDEN") ? "HOLLOW_ACOLYTE" : "BONE_WARDEN";
		EnemyInstance wrongKind = new EnemyInstance(enemy.entityId(), otherCode, enemy.stats(), enemy.maxHp(),
				enemy.currentHp(), enemy.body(), EnemyFixtures.definition(otherCode).weapon());
		assertThat(validator.problems(world, replace(roster, first, new SceneEnemy(first.sceneId(), wrongKind))))
				.anyMatch(p -> p.contains("but its entity is a"));
		Map<BodyPart, BodySeverity> noHeart = new EnumMap<>(enemy.body().severities());
		noHeart.remove(BodyPart.HEART);
		EnemyInstance wrongBody = new EnemyInstance(enemy.entityId(), enemy.definitionCode(), enemy.stats(), enemy.maxHp(),
				enemy.currentHp(), new EnemyBody(noHeart), enemy.weaponCode());
		assertThat(validator.problems(world, replace(roster, first, new SceneEnemy(first.sceneId(), wrongBody))))
				.anyMatch(p -> p.contains("does not match anatomy"));
		EnemyInstance unknownWeapon = new EnemyInstance(enemy.entityId(), enemy.definitionCode(), enemy.stats(),
				enemy.maxHp(), enemy.currentHp(), enemy.body(), "GREAT_AXE");
		assertThat(validator.problems(world, replace(roster, first, new SceneEnemy(first.sceneId(), unknownWeapon))))
				.anyMatch(p -> p.contains("GREAT_AXE"));
	}

	@Test
	void nonEnemyEntitiesGetNoStateAndMustNotHaveAny() {
		GeneratedRunWorld world = world(5);
		SceneInstance scene = world.region().scenes().getFirst();
		List<SceneEntity> withMonk = new ArrayList<>(scene.state().entities());
		String zone = scene.state().zones().getFirst().id();
		withMonk.add(new SceneEntity("monk_1", "WANDERING_MONK", zone));
		GeneratedRunWorld changed = replaceEntities(world, scene.id(), s -> withEntities(s, withMonk));

		GeneratedEnemyRoster roster = rosters.generate(changed);
		assertThat(roster.find(scene.id(), "monk_1")).isEmpty();
		assertThat(validator.problems(changed, roster)).isEmpty();

		EnemyInstance monk = EnemyFixtures.instance("HOLLOW_ACOLYTE", "monk_1", EnemyFixtures.stats(5, 9, 7, 3, 3));
		List<SceneEnemy> extra = new ArrayList<>(roster.enemies());
		extra.add(new SceneEnemy(scene.id(), monk));
		assertThat(validator.problems(changed, new GeneratedEnemyRoster(extra)))
				.anyMatch(p -> p.contains("is not an enemy but has enemy state"));
	}

	@Test
	void rosterRejectsDuplicateEnemies() {
		SceneEnemy enemy = new SceneEnemy(UUID.randomUUID(), EnemyFixtures.acolyte());
		assertThatIllegalArgumentException().isThrownBy(() -> new GeneratedEnemyRoster(List.of(enemy, enemy)));
	}

	@Test
	void runInitializerGeneratesWorldAndEnemiesTogether() {
		RunInitialization initialization = new RunInitializer(new RunWorldGenerator(WORLD, sequentialIds()), rosters)
				.initialize(RUN, 21, GenerationContextSnapshot.empty());

		assertThat(initialization.runId()).isEqualTo(RUN);
		assertThat(validator.problems(initialization.world(), initialization.enemies())).isEmpty();
		assertThat(initialization.enemies().enemies()).anyMatch(e -> e.enemy().definitionCode().equals("CHAPEL_GUARDIAN"));
	}

	@Test
	void runInitializationRejectsEnemiesOfForeignScenes() {
		GeneratedRunWorld world = world(3);
		GeneratedEnemyRoster foreign = new GeneratedEnemyRoster(List.of(new SceneEnemy(UUID.randomUUID(), EnemyFixtures.acolyte())));
		assertThatIllegalArgumentException().isThrownBy(() -> new RunInitialization(world, foreign));
	}

	// --- helpers ---

	private static GeneratedEnemyRoster without(GeneratedEnemyRoster roster, SceneEnemy removed) {
		return new GeneratedEnemyRoster(roster.enemies().stream().filter(e -> !e.equals(removed)).toList());
	}

	private static GeneratedEnemyRoster replace(GeneratedEnemyRoster roster, SceneEnemy old, SceneEnemy replacement) {
		return new GeneratedEnemyRoster(roster.enemies().stream().map(e -> e.equals(old) ? replacement : e).toList());
	}

	private static SceneState withEntities(SceneState s, List<SceneEntity> entities) {
		List<String> ids = entities.stream().map(SceneEntity::id).toList();
		return new SceneState(s.zones(), s.connections(), entities, s.objects(), s.hazards(), s.exits(), s.activeEvents(),
				s.environmentFlags(),
				s.hiddenContent().stream().filter(r -> r.kind() != HiddenContentKind.ENTITY || ids.contains(r.localId())).toList(),
				s.discoveredFacts());
	}

	private static GeneratedRunWorld replaceEntities(GeneratedRunWorld world, UUID sceneId, UnaryOperator<SceneState> change) {
		GeneratedRegion region = world.region();
		List<SceneInstance> scenes = region.scenes().stream()
				.map(s -> s.id().equals(sceneId)
						? new SceneInstance(s.id(), s.runId(), s.definitionCode(), s.placement(), s.discovered(), s.revision(),
								change.apply(s.state()))
						: s)
				.toList();
		return new GeneratedRunWorld(world.runId(), world.context(), world.hub(),
				new GeneratedRegion(region.region(), region.routeStages(), region.optionalSceneIds(), region.bossSceneId(), scenes),
				world.start());
	}
}
