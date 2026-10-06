package com.leeburke.springgame.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.mechanics.DamageType;

class ContentDefinitionTest {

	/** Builds each definition type from a code and display name, with otherwise-valid fields. */
	static Stream<Arguments> definitionFactories() {
		return Stream.of(
				Arguments.of("weapon", (Factory) (c, n) -> new WeaponDefinition(c, n, 6, 3, DamageType.SLASHING)),
				Arguments.of("passive", (Factory) PassiveDefinition::new),
				Arguments.of("ability", (Factory) AbilityDefinition::new),
				Arguments.of("item", (Factory) (c, n) -> new ItemDefinition(c, n, ItemCategory.UTILITY)));
	}

	@FunctionalInterface
	interface Factory {
		Object create(String code, String displayName);
	}

	static Stream<Arguments> invalidCodes() {
		return definitionFactories().flatMap(args -> Stream.of(null, "", "  ", "longsword", "WAR HAMMER", " DAGGER", "1ROPE")
				.map(code -> Arguments.of(args.get()[0], args.get()[1], code)));
	}

	@ParameterizedTest(name = "{0} rejects code [{2}]")
	@MethodSource("invalidCodes")
	void rejectsInvalidCodes(String type, Factory factory, String code) {
		assertThatIllegalArgumentException().isThrownBy(() -> factory.create(code, "Name"));
	}

	static Stream<Arguments> invalidDisplayNames() {
		return definitionFactories().flatMap(args -> Stream.of(null, "", "   ")
				.map(name -> Arguments.of(args.get()[0], args.get()[1], name)));
	}

	@ParameterizedTest(name = "{0} rejects display name [{2}]")
	@MethodSource("invalidDisplayNames")
	void rejectsInvalidDisplayNames(String type, Factory factory, String displayName) {
		assertThatIllegalArgumentException().isThrownBy(() -> factory.create("VALID_CODE_2", displayName));
	}

	@ParameterizedTest(name = "{0} accepts upper snake case")
	@MethodSource("definitionFactories")
	void acceptsUpperSnakeCaseCode(String type, Factory factory) {
		assertThat(factory.create("WAR_HAMMER_2", "War Hammer")).isNotNull();
	}

	@Test
	void rejectsNegativeWeaponBaseDamage() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new WeaponDefinition("STICK", "Stick", -1, 0, DamageType.BLUNT));
	}

	@Test
	void rejectsNegativeWeaponTrauma() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new WeaponDefinition("STICK", "Stick", 0, -1, DamageType.BLUNT));
	}

	@Test
	void acceptsZeroWeaponDamageAndTrauma() {
		WeaponDefinition weapon = new WeaponDefinition("STICK", "Stick", 0, 0, DamageType.BLUNT);
		assertThat(weapon.baseDamage()).isZero();
		assertThat(weapon.trauma()).isZero();
	}

	@ParameterizedTest
	@NullSource
	void rejectsNullWeaponDamageType(DamageType damageType) {
		assertThatNullPointerException().isThrownBy(() -> new WeaponDefinition("STICK", "Stick", 1, 1, damageType));
	}

	@ParameterizedTest
	@NullSource
	void rejectsNullItemCategory(ItemCategory category) {
		assertThatNullPointerException().isThrownBy(() -> new ItemDefinition("ROPE", "Rope", category));
	}

	@ParameterizedTest
	@ValueSource(strings = { "A", "ROPE", "WAR_HAMMER", "ITEM_2" })
	void codeFormatExamples(String code) {
		assertThat(new PassiveDefinition(code, "Name").code()).isEqualTo(code);
	}
}
