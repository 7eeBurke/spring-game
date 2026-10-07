package com.leeburke.springgame.enemy;

import static com.leeburke.springgame.enemy.EnemyFixtures.ENEMIES;
import static com.leeburke.springgame.enemy.EnemyFixtures.definition;
import static com.leeburke.springgame.enemy.EnemyFixtures.stats;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.character.StatProfile;
import com.leeburke.springgame.character.StatShape;
import com.leeburke.springgame.character.StatShapeCatalog;
import com.leeburke.springgame.content.enemy.EnemyDefinition;
import com.leeburke.springgame.content.enemy.EnemyStatRule;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.world.generation.WorldRandom;

class EnemyGeneratorTest {

	private final EnemyGenerator generator = new EnemyGenerator(ENEMIES);

	@Test
	void enemyShapesAreSpecializedThenExtremeInCatalogueOrder() {
		List<StatShape> expected = new java.util.ArrayList<>(StatShapeCatalog.shapesFor(StatProfile.SPECIALIZED));
		expected.addAll(StatShapeCatalog.shapesFor(StatProfile.EXTREME));
		assertThat(EnemyRules.enemyShapes()).containsExactlyElementsOf(expected);
		assertThat(EnemyRules.enemyShapes()).allMatch(shape -> shape.max() - shape.min() >= 3);
	}

	@ParameterizedTest
	@ValueSource(strings = { "HOLLOW_ACOLYTE", "BONE_WARDEN", "ASHBOUND_PENITENT" })
	void everyShapeIsAssignedAlongThePriority(String code) {
		EnemyDefinition enemy = definition(code);
		List<StatType> priority = ((EnemyStatRule.ShapePriority) enemy.statRule()).priority();
		for (int index = 0; index < EnemyRules.enemyShapes().size(); index++) {
			StatBlock stats = generator.generate(enemy, "e_1", new ScriptedRandom(index)).stats();
			List<Integer> inPriorityOrder = priority.stream().map(stat -> stats.get(stat).value()).toList();
			assertThat(inPriorityOrder).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
			assertThat(inPriorityOrder.stream().mapToInt(Integer::intValue).sum()).isEqualTo(27);
			assertThat(inPriorityOrder.getFirst() - inPriorityOrder.getLast()).isGreaterThanOrEqualTo(3);
		}
	}

	@Test
	void normalEnemyDrawsExactlyOneShapeIndex() {
		ScriptedRandom rng = new ScriptedRandom(0);
		generator.generate(definition("HOLLOW_ACOLYTE"), "acolyte_1", rng);
		assertThat(rng.bounds()).containsExactly((long) EnemyRules.enemyShapes().size());
	}

	@Test
	void shapeIsAssignedHighestFirst() {
		StatShape shape = new StatShape(List.of(10, 7, 4, 3, 3));
		StatBlock stats = EnemyGenerator.assign(shape, List.of(StatType.ARCANA, StatType.RESOLVE, StatType.PERCEPTION,
				StatType.AGILITY, StatType.MIGHT));
		assertThat(stats).isEqualTo(stats(3, 3, 4, 10, 7));
	}

	@Test
	void differentDrawsGiveDifferentStats() {
		EnemyDefinition warden = definition("BONE_WARDEN");
		int last = EnemyRules.enemyShapes().size() - 1;
		assertThat(generator.generate(warden, "w", new ScriptedRandom(0)).stats())
				.isNotEqualTo(generator.generate(warden, "w", new ScriptedRandom(last)).stats());
	}

	@Test
	void bossUsesItsFixedBlockAndDrawsNothing() {
		ScriptedRandom rng = new ScriptedRandom();
		EnemyInstance guardian = generator.generate(definition("CHAPEL_GUARDIAN"), "guardian", rng);

		assertThat(guardian.stats()).isEqualTo(stats(10, 9, 5, 4, 8));
		assertThat(guardian.maxHp()).isEqualTo(42);
		assertThat(rng.bounds()).isEmpty();
	}

	@Test
	void startsAtFullHpWithHealthyHumanoidBodyAndTheDefinitionsWeapon() {
		EnemyInstance acolyte = generator.generate(definition("HOLLOW_ACOLYTE"), "acolyte_1", new ScriptedRandom(0));

		assertThat(acolyte.entityId()).isEqualTo("acolyte_1");
		assertThat(acolyte.definitionCode()).isEqualTo("HOLLOW_ACOLYTE");
		assertThat(acolyte.maxHp()).isEqualTo(12 + acolyte.stats().resolve().value() - 6);
		assertThat(acolyte.currentHp()).isEqualTo(acolyte.maxHp());
		assertThat(acolyte.body().severities()).hasSize(BodyPart.values().length)
				.allSatisfy((part, severity) -> assertThat(severity).isEqualTo(BodySeverity.HEALTHY));
		assertThat(acolyte.weaponCode()).isEqualTo("DAGGER");
	}

	@Test
	void sameSeedGivesEqualInstances() {
		long seed = WorldRandom.entitySeed(77, "warden_post_1");
		assertThat(generator.generate(definition("BONE_WARDEN"), "warden_post_1", WorldRandom.create(seed)))
				.isEqualTo(generator.generate(definition("BONE_WARDEN"), "warden_post_1", WorldRandom.create(seed)));
	}

	@Test
	void entityIdDoesNotConsumeRandomness() {
		ScriptedRandom rng = new ScriptedRandom(5);
		EnemyInstance a = generator.generate(definition("HOLLOW_ACOLYTE"), "a_very_long_entity_identifier", rng);
		EnemyInstance b = generator.generate(definition("HOLLOW_ACOLYTE"), "x", new ScriptedRandom(5));
		assertThat(rng.bounds()).hasSize(1);
		assertThat(a.stats()).isEqualTo(b.stats());
	}

	@Test
	void uncataloguedDefinitionIsRejected() {
		EnemyDefinition acolyte = definition("HOLLOW_ACOLYTE");
		EnemyDefinition altered = new EnemyDefinition(acolyte.code(), acolyte.anatomy(), acolyte.weapon(),
				acolyte.statPriority(), Map.of(), 30, acolyte.defenseStat(), acolyte.traits(), acolyte.holdWeight(),
				acolyte.attacks());
		assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(altered, "a", new ScriptedRandom(0)));
	}
}
