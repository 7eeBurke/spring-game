package com.leeburke.springgame.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.mechanics.DamageType;

import tools.jackson.databind.exc.UnrecognizedPropertyException;

class GameContentLoaderTest {

	private static final String FIXTURES = "content-fixtures";

	private static GameContentCatalog bundled;

	@BeforeAll
	static void loadBundledContent() {
		bundled = GameContentLoader.loadBundled();
	}

	// --- Bundled MVP content. The counts below describe current content, not a catalogue limit. ---

	@Test
	void bundledContentHasCurrentMvpCounts() {
		assertThat(bundled.weapons()).hasSize(4);
		assertThat(bundled.passives()).hasSize(6);
		assertThat(bundled.abilities()).hasSize(4);
		assertThat(bundled.items()).hasSize(6);
	}

	@ParameterizedTest
	@CsvSource({
			"LONGSWORD,Longsword,6,3,SLASHING",
			"DAGGER,Dagger,4,2,PIERCING",
			"WAR_HAMMER,War Hammer,7,6,BLUNT",
			"EMBER_ROD,Ember Rod,5,3,FIRE" })
	void bundledWeaponsMatchMvpTable(String code, String name, int baseDamage, int trauma, DamageType type) {
		assertThat(bundled.findWeapon(code)).contains(new WeaponDefinition(code, name, baseDamage, trauma, type));
	}

	@ParameterizedTest
	@CsvSource({
			"LIGHT_FOOT,Light Foot",
			"IRON_GRIP,Iron Grip",
			"GRAVE_SENSE,Grave Sense",
			"CLEAR_MIND,Clear Mind",
			"IMPROVISER,Improviser",
			"ASH_TOUCHED,Ash Touched" })
	void bundledPassivesAreLoaded(String code, String name) {
		assertThat(bundled.findPassive(code)).contains(new PassiveDefinition(code, name));
	}

	@ParameterizedTest
	@CsvSource({
			"STONEBLOOD,Stoneblood",
			"EMBER_EDGE,Ember Edge",
			"SHADOW_STEP,Shadow Step",
			"WARDING_SIGIL,Warding Sigil" })
	void bundledAbilitiesAreLoaded(String code, String name) {
		assertThat(bundled.findAbility(code)).contains(new AbilityDefinition(code, name));
	}

	@ParameterizedTest
	@CsvSource({
			"BANDAGE,Bandage,RECOVERY",
			"RESTORATIVE_SALVE,Restorative Salve,RECOVERY",
			"ROPE,Rope,UTILITY",
			"TORCH,Torch,UTILITY",
			"CROWBAR,Crowbar,UTILITY",
			"LOCKPICKS,Lockpicks,UTILITY" })
	void bundledItemsHaveExpectedCategories(String code, String name, ItemCategory category) {
		assertThat(bundled.findItem(code)).hasValueSatisfying(item -> {
			assertThat(item.displayName()).isEqualTo(name);
			assertThat(item.category()).isEqualTo(category);
			assertThat(item.description()).as("how it looks in the hand").isNotBlank();
		});
	}

	@Test
	void bundledCharacterNamesAreLoaded() {
		assertThat(bundled.characterNames()).hasSize(26)
				.contains("Aldric", "Yorick")
				.allSatisfy(name -> assertThat(name).isNotBlank());
	}

	// --- Strict loading, using test fixtures rather than production content. ---

	@Test
	void nonStringNameFails() {
		String path = FIXTURES + "/names-not-strings.json";
		assertThatThrownBy(() -> new GameContentLoader(FIXTURES).readDefinitions(path, String.class))
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining(path);
	}

	@Test
	void caseInsensitiveDuplicateNameFailsFullLoad() {
		String directory = FIXTURES + "/duplicate-name";
		assertThatThrownBy(() -> new GameContentLoader(directory).load())
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining(directory)
				.hasMessageContaining("aldric");
	}

	@Test
	void missingNamesResourceFailsClearly() {
		assertThatThrownBy(() -> new GameContentLoader(FIXTURES + "/missing-names").load())
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining("Missing required content resource")
				.hasMessageContaining("character-names.json");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"malformed.json",
			"unknown-property.json",
			"unknown-enum.json",
			"missing-field.json",
			"blank-code.json",
			"negative-damage.json",
			"string-number.json" })
	void invalidWeaponFilesFailWithResourceName(String fixture) {
		String path = FIXTURES + "/" + fixture;
		GameContentLoader loader = new GameContentLoader(FIXTURES);

		assertThatThrownBy(() -> loader.readDefinitions(path, WeaponDefinition.class))
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining(path);
	}

	@Test
	void misspelledFieldFailsAsUnknownPropertyNotSilentlyIgnored() {
		String path = FIXTURES + "/unknown-property.json";
		assertThatThrownBy(() -> new GameContentLoader(FIXTURES).readDefinitions(path, WeaponDefinition.class))
				.isInstanceOf(ContentLoadException.class)
				.hasCauseInstanceOf(UnrecognizedPropertyException.class)
				.hasMessageContaining("baseDamge");
	}

	@Test
	void duplicateCodeFailsFullLoad() {
		String directory = FIXTURES + "/duplicate-weapon";
		assertThatThrownBy(() -> new GameContentLoader(directory).load())
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining(directory)
				.hasMessageContaining("LONGSWORD");
	}

	@Test
	void missingResourceFailsClearly() {
		assertThatThrownBy(() -> new GameContentLoader(FIXTURES + "/does-not-exist").load())
				.isInstanceOf(ContentLoadException.class)
				.hasMessageContaining("Missing required content resource")
				.hasMessageContaining("weapons.json");
	}
}
