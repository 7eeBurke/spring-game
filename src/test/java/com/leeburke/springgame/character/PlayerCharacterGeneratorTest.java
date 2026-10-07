package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.GameContentLoader;
import com.leeburke.springgame.content.ItemCategory;
import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.PassiveDefinition;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.DamageType;
import com.leeburke.springgame.mechanics.StatType;

class PlayerCharacterGeneratorTest {

	private static GameContentCatalog bundled;
	private static PlayerCharacterGenerator generator;

	@BeforeAll
	static void setUp() {
		bundled = GameContentLoader.loadBundled();
		generator = new PlayerCharacterGenerator(bundled, new PlayerStatGenerator());
	}

	/** Checks every invariant of a newly generated character against the catalogue it came from. */
	private static void assertValidNewCharacter(GeneratedCharacter character, GameContentCatalog catalog) {
		assertThat(catalog.characterNames()).contains(character.name());

		int total = 0;
		for (StatType type : StatType.values()) {
			int value = character.stats().get(type).value();
			assertThat(value).isBetween(3, 10);
			total += value;
		}
		assertThat(total).isEqualTo(27);

		assertThat(character.fated().value()).isBetween(0, 5);
		assertThat(character.maxHp()).isEqualTo(26 + character.stats().get(StatType.RESOLVE).value() - 6);
		assertThat(character.currentHp()).isEqualTo(character.maxHp());

		for (BodyPart part : BodyPart.values()) {
			assertThat(character.body().severity(part)).isEqualTo(BodySeverity.HEALTHY);
		}

		assertThat(catalog.passives()).contains(character.passive());
		assertThat(catalog.abilities()).contains(character.ability());
		assertThat(character.toolBelt().occupiedSlots()).isEqualTo(3);
		assertThat(character.toolBelt().emptySlots()).isEqualTo(2);
		assertThat(catalog.weapons()).contains(character.weapon());
		assertThat(catalog.items()).contains(character.recoveryItem(), character.utilityItem());
		assertThat(character.recoveryItem().category()).isEqualTo(ItemCategory.RECOVERY);
		assertThat(character.utilityItem().category()).isEqualTo(ItemCategory.UTILITY);
	}

	@Test
	void generatedCharactersSatisfyAllInvariantsAcrossSeeds() {
		for (long seed = 0; seed < 200; seed++) {
			assertValidNewCharacter(generator.generate(new SplittableRandom(seed)), bundled);
		}
	}

	@Test
	void fullGeneratedCharacterExample() {
		GeneratedCharacter character = generator.generate(new SplittableRandom(20261007L));

		assertValidNewCharacter(character, bundled);
		assertThat(character.name()).isNotBlank();
		assertThat(character.toolBelt().entries()).containsExactly(
				new ToolBeltEntry.Weapon(character.weapon()),
				new ToolBeltEntry.Item(character.recoveryItem()),
				new ToolBeltEntry.Item(character.utilityItem()));
	}

	@Test
	void sameSeedProducesSameCharacter() {
		assertThat(generator.generate(new SplittableRandom(7))).isEqualTo(generator.generate(new SplittableRandom(7)));
	}

	@Test
	void sameSeedProducesSameSequence() {
		PlayerCharacterGenerator other = new PlayerCharacterGenerator(bundled, new PlayerStatGenerator());
		SplittableRandom first = new SplittableRandom(99);
		SplittableRandom second = new SplittableRandom(99);
		List<GeneratedCharacter> a = new ArrayList<>();
		List<GeneratedCharacter> b = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			a.add(generator.generate(first));
			b.add(other.generate(second));
		}
		assertThat(a).isEqualTo(b);
	}

	@Test
	void differentSeedsProduceVariation() {
		Set<GeneratedCharacter> distinct = new HashSet<>();
		for (long seed = 0; seed < 20; seed++) {
			distinct.add(generator.generate(new SplittableRandom(seed)));
		}
		assertThat(distinct).hasSizeGreaterThan(1);
	}

	// --- Controlled RNG against a custom catalogue: proves sizes and categories come from the catalogue. ---

	private static WeaponDefinition weapon(String code) {
		return new WeaponDefinition(code, "Weapon " + code, 5, 2, DamageType.BLUNT);
	}

	private static final ItemDefinition UTILITY_A = new ItemDefinition("UTILITY_A", "Utility A", ItemCategory.UTILITY);
	private static final ItemDefinition RECOVERY_B = new ItemDefinition("RECOVERY_B", "Recovery B", ItemCategory.RECOVERY);
	private static final ItemDefinition UTILITY_C = new ItemDefinition("UTILITY_C", "Utility C", ItemCategory.UTILITY);
	private static final ItemDefinition RECOVERY_D = new ItemDefinition("RECOVERY_D", "Recovery D", ItemCategory.RECOVERY);

	private static GameContentCatalog customCatalog() {
		return new GameContentCatalog(
				List.of(weapon("W1"), weapon("W2"), weapon("W3"), weapon("W4"), weapon("W5")),
				List.of(new PassiveDefinition("P1", "Passive 1"), new PassiveDefinition("P2", "Passive 2")),
				List.of(new AbilityDefinition("A1", "Ability 1"), new AbilityDefinition("A2", "Ability 2"),
						new AbilityDefinition("A3", "Ability 3")),
				List.of(UTILITY_A, RECOVERY_B, UTILITY_C, RECOVERY_D),
				List.of("Ansel", "Brena", "Cato"));
	}

	@Test
	void lowestChoicesSelectFirstOfEachPool() {
		GameContentCatalog catalog = customCatalog();
		GeneratedCharacter character = new PlayerCharacterGenerator(catalog, new PlayerStatGenerator())
				.generate(FixedChoiceGenerator.lowest());

		assertValidNewCharacter(character, catalog);
		assertThat(StatProfile.classify(StatShape.of(character.stats()))).isEqualTo(StatProfile.BALANCED);
		assertThat(character.fated()).isEqualTo(new Fated(0));
		assertThat(character.weapon().code()).isEqualTo("W1");
		assertThat(character.passive().code()).isEqualTo("P1");
		assertThat(character.ability().code()).isEqualTo("A1");
		assertThat(character.recoveryItem()).isEqualTo(RECOVERY_B);
		assertThat(character.utilityItem()).isEqualTo(UTILITY_A);
		assertThat(character.name()).isEqualTo("Ansel");
	}

	@Test
	void highestChoicesSelectLastOfEachPool() {
		GameContentCatalog catalog = customCatalog();
		FixedChoiceGenerator rng = FixedChoiceGenerator.highest();
		GeneratedCharacter character = new PlayerCharacterGenerator(catalog, new PlayerStatGenerator()).generate(rng);

		assertValidNewCharacter(character, catalog);
		assertThat(character.fated()).isEqualTo(new Fated(5));
		assertThat(character.weapon().code()).isEqualTo("W5");
		assertThat(character.passive().code()).isEqualTo("P2");
		assertThat(character.ability().code()).isEqualTo("A3");
		assertThat(character.recoveryItem()).isEqualTo(RECOVERY_D);
		assertThat(character.utilityItem()).isEqualTo(UTILITY_C);
		assertThat(character.name()).isEqualTo("Cato");
		// After the stat draws come Fated, weapon, passive, ability, recovery, utility and name; bounds follow pool sizes.
		assertThat(rng.requestedBounds.subList(rng.requestedBounds.size() - 7, rng.requestedBounds.size()))
				.containsExactly(100, 5, 2, 3, 2, 2, 3);
	}

	// --- Required pools ---

	static Stream<Arguments> catalogsMissingOnePool() {
		GameContentCatalog full = customCatalog();
		return Stream.of(
				Arguments.of("weapons", new GameContentCatalog(List.of(), full.passives(), full.abilities(), full.items(), full.characterNames())),
				Arguments.of("passives", new GameContentCatalog(full.weapons(), List.of(), full.abilities(), full.items(), full.characterNames())),
				Arguments.of("abilities", new GameContentCatalog(full.weapons(), full.passives(), List.of(), full.items(), full.characterNames())),
				Arguments.of("RECOVERY items", new GameContentCatalog(full.weapons(), full.passives(), full.abilities(),
						List.of(UTILITY_A, UTILITY_C), full.characterNames())),
				Arguments.of("UTILITY items", new GameContentCatalog(full.weapons(), full.passives(), full.abilities(),
						List.of(RECOVERY_B, RECOVERY_D), full.characterNames())),
				Arguments.of("character names", new GameContentCatalog(full.weapons(), full.passives(), full.abilities(), full.items(), List.of())));
	}

	@ParameterizedTest(name = "fails without {0}")
	@MethodSource("catalogsMissingOnePool")
	void failsClearlyWhenARequiredPoolIsEmpty(String poolName, GameContentCatalog catalog) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlayerCharacterGenerator(catalog, new PlayerStatGenerator()))
				.withMessageContaining(poolName);
	}

	@Test
	void namesEveryEmptyPool() {
		GameContentCatalog catalog = new GameContentCatalog(List.of(), List.of(), List.of(), List.of(UTILITY_A), List.of());
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new PlayerCharacterGenerator(catalog, new PlayerStatGenerator()))
				.withMessageContaining("weapons")
				.withMessageContaining("passives")
				.withMessageContaining("abilities")
				.withMessageContaining("RECOVERY items")
				.withMessageContaining("character names")
				.withMessageNotContaining("UTILITY items");
	}

	@Test
	void allRandomnessComesFromSuppliedGenerator() {
		RandomGenerator refusing = new RandomGenerator() {
			@Override
			public long nextLong() {
				throw new IllegalStateException("supplied generator used");
			}

			@Override
			public int nextInt(int bound) {
				throw new IllegalStateException("supplied generator used");
			}
		};
		assertThatThrownBy(() -> generator.generate(refusing)).hasMessage("supplied generator used");
	}
}
