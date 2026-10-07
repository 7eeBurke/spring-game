package com.leeburke.springgame.content.enemy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.content.ContentLoadException;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.GameContentLoader;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.content.world.WorldContentLoader;
import com.leeburke.springgame.content.world.WorldElementKind;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.StatType;

class EnemyContentLoaderTest {

	private static final String FIXTURES = "content-fixtures/enemy";
	private static final GameContentCatalog CONTENT = GameContentLoader.loadBundled();
	private static final WorldContentCatalog WORLD = WorldContentLoader.loadBundled();
	private static final EnemyCatalog BUNDLED = EnemyContentLoader.loadBundled(CONTENT, WORLD);

	@Test
	void bundledEnemiesCoverEveryMvpEnemyEntity() {
		assertThat(BUNDLED.enemies()).extracting(EnemyDefinition::code)
				.containsExactly("HOLLOW_ACOLYTE", "BONE_WARDEN", "ASHBOUND_PENITENT", "CHAPEL_GUARDIAN");
		List<String> worldEntities = WORLD.elements().stream().filter(e -> e.kind() == WorldElementKind.ENTITY)
				.map(e -> e.code()).toList();
		assertThat(worldEntities).allMatch(code -> BUNDLED.findEnemy(code).isPresent());
	}

	@Test
	void bundledHumanoidAnatomyHasEveryBodyPart() {
		assertThat(BUNDLED.anatomies()).singleElement().satisfies(anatomy -> {
			assertThat(anatomy.code()).isEqualTo("HUMANOID");
			assertThat(anatomy.bodyParts()).containsExactly(BodyPart.values());
		});
		assertThat(BUNDLED.enemies()).allMatch(enemy -> enemy.anatomy().equals("HUMANOID"));
	}

	@ParameterizedTest
	@CsvSource({
			"HOLLOW_ACOLYTE, DAGGER, 12, AGILITY, 0",
			"BONE_WARDEN, WAR_HAMMER, 20, MIGHT, 0",
			"ASHBOUND_PENITENT, EMBER_ROD, 14, AGILITY, 10",
			"CHAPEL_GUARDIAN, LONGSWORD, 40, MIGHT, 0" })
	void bundledEnemyValues(String code, String weapon, int baseHp, StatType defenseStat, int holdWeight) {
		EnemyDefinition enemy = BUNDLED.findEnemy(code).orElseThrow();
		assertThat(enemy.weapon()).isEqualTo(weapon);
		assertThat(enemy.baseHp()).isEqualTo(baseHp);
		assertThat(enemy.defenseStat()).isEqualTo(defenseStat);
		assertThat(enemy.holdWeight()).isEqualTo(holdWeight);
	}

	@Test
	void bundledStatRulesTraitsAndAttacks() {
		EnemyDefinition acolyte = BUNDLED.findEnemy("HOLLOW_ACOLYTE").orElseThrow();
		assertThat(acolyte.statRule()).isEqualTo(new EnemyStatRule.ShapePriority(List.of(StatType.AGILITY,
				StatType.PERCEPTION, StatType.MIGHT, StatType.RESOLVE, StatType.ARCANA)));
		assertThat(acolyte.traits()).containsExactly(EnemyTrait.AGGRESSIVE, EnemyTrait.OPPORTUNISTIC);
		assertThat(acolyte.attacks()).containsExactly(
				new EnemyAttackOption("ACOLYTE_QUICK_SLASH", WeaponMethod.SLASH, AttackTemplate.QUICK_SLASH, 55),
				new EnemyAttackOption("ACOLYTE_STAB", WeaponMethod.THRUST, AttackTemplate.THRUST, 45));

		assertThat(BUNDLED.findEnemy("BONE_WARDEN").orElseThrow().traits()).containsExactly(EnemyTrait.RELENTLESS);
		assertThat(BUNDLED.findEnemy("BONE_WARDEN").orElseThrow().attacks()).extracting(EnemyAttackOption::code)
				.containsExactly("WARDEN_OVERHEAD_SMASH", "WARDEN_HEAVY_SMASH", "WARDEN_HAFT_HOOK");

		EnemyDefinition penitent = BUNDLED.findEnemy("ASHBOUND_PENITENT").orElseThrow();
		assertThat(penitent.traits()).containsExactly(EnemyTrait.CAUTIOUS, EnemyTrait.ADAPTIVE);
		assertThat(penitent.attacks()).containsExactly(
				new EnemyAttackOption("PENITENT_EMBER_BOLT", WeaponMethod.PROJECT, AttackTemplate.PROJECTED_ATTACK, 70),
				new EnemyAttackOption("PENITENT_ROD_STRIKE", WeaponMethod.SMASH, AttackTemplate.OVERHEAD_STRIKE, 20));

		EnemyDefinition guardian = BUNDLED.findEnemy("CHAPEL_GUARDIAN").orElseThrow();
		assertThat(guardian.statRule()).isInstanceOfSatisfying(EnemyStatRule.Fixed.class, fixed -> {
			assertThat(fixed.stats().might().value()).isEqualTo(10);
			assertThat(fixed.stats().agility().value()).isEqualTo(9);
			assertThat(fixed.stats().resolve().value()).isEqualTo(8);
			assertThat(fixed.stats().perception().value()).isEqualTo(5);
			assertThat(fixed.stats().arcana().value()).isEqualTo(4);
		});
		assertThat(guardian.traits()).containsExactly(EnemyTrait.AGGRESSIVE, EnemyTrait.RELENTLESS);
		assertThat(guardian.attacks()).extracting(EnemyAttackOption::code)
				.containsExactly("GUARDIAN_SWEEPING_CUT", "GUARDIAN_OVERHEAD_CLEAVE", "GUARDIAN_LUNGE");
	}

	@Test
	void validFixtureDirectoryLoads() {
		assertThat(new EnemyContentLoader(FIXTURES + "/valid").load(CONTENT, WORLD).enemies()).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "malformed.json", "unknown-property.json", "missing-field.json", "wrong-type.json",
			"unknown-method.json", "unknown-template.json", "zero-weight.json", "unknown-trait.json", "null-traits.json" })
	void invalidEnemyFilesFailToParse(String fixture) {
		String path = FIXTURES + "/" + fixture;
		assertThatThrownBy(() -> new EnemyContentLoader(FIXTURES).readDefinitions(path, EnemyDefinition.class))
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining(path);
	}

	@ParameterizedTest
	@ValueSource(strings = { "anatomy-duplicate-part.json", "anatomy-unknown-part.json", "anatomy-no-parts.json" })
	void invalidAnatomyFilesFailToParse(String fixture) {
		String path = FIXTURES + "/" + fixture;
		assertThatThrownBy(() -> new EnemyContentLoader(FIXTURES).readDefinitions(path, AnatomyDefinition.class))
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining(path);
	}

	@ParameterizedTest
	@CsvSource({
			"unknown-weapon, GREAT_AXE",
			"unknown-anatomy, QUADRUPED",
			"unknown-entity, CRYPT_HOUND",
			"not-an-entity, OBJECT",
			"duplicate-enemy, Duplicate enemy code",
			"missing-enemies, enemies.json" })
	void crossReferenceFailuresFailTheWholeLoad(String directory, String expectedMessage) {
		assertThatThrownBy(() -> new EnemyContentLoader(FIXTURES + "/" + directory).load(CONTENT, WORLD))
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining(expectedMessage);
	}

	@Test
	void attackOptionCodesMustBeUniqueAcrossEnemies() {
		EnemyDefinition acolyte = BUNDLED.findEnemy("HOLLOW_ACOLYTE").orElseThrow();
		EnemyDefinition warden = BUNDLED.findEnemy("BONE_WARDEN").orElseThrow();
		EnemyDefinition borrowing = new EnemyDefinition(warden.code(), warden.anatomy(), warden.weapon(), warden.statPriority(),
				Map.of(), warden.baseHp(), warden.defenseStat(), warden.traits(), warden.holdWeight(), acolyte.attacks());

		assertThatThrownBy(() -> new EnemyCatalog(BUNDLED.anatomies(), List.of(acolyte, borrowing), CONTENT, WORLD))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ACOLYTE_QUICK_SLASH");
	}
}
