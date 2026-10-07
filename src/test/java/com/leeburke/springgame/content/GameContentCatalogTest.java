package com.leeburke.springgame.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.mechanics.DamageType;

class GameContentCatalogTest {

	private static WeaponDefinition weapon(String code) {
		return new WeaponDefinition(code, "Weapon " + code, 5, 2, DamageType.BLUNT);
	}

	private static PassiveDefinition passive(String code) {
		return new PassiveDefinition(code, "Passive " + code);
	}

	private static AbilityDefinition ability(String code) {
		return new AbilityDefinition(code, "Ability " + code);
	}

	private static ItemDefinition item(String code) {
		return new ItemDefinition(code, "Item " + code, ItemCategory.UTILITY);
	}

	private static GameContentCatalog weaponsOnly(List<WeaponDefinition> weapons) {
		return new GameContentCatalog(weapons, List.of(), List.of(), List.of(), List.of());
	}

	@Test
	void findsDefinitionsByCode() {
		GameContentCatalog catalog = new GameContentCatalog(
				List.of(weapon("CLUB")), List.of(passive("STEADY")), List.of(ability("BLINK")), List.of(item("ROPE")), List.of("Aldric"));

		assertThat(catalog.findWeapon("CLUB")).contains(weapon("CLUB"));
		assertThat(catalog.findPassive("STEADY")).contains(passive("STEADY"));
		assertThat(catalog.findAbility("BLINK")).contains(ability("BLINK"));
		assertThat(catalog.findItem("ROPE")).contains(item("ROPE"));
	}

	@Test
	void unknownCodeIsEmpty() {
		GameContentCatalog catalog = weaponsOnly(List.of(weapon("CLUB")));
		assertThat(catalog.findWeapon("SPEAR")).isEmpty();
		assertThat(catalog.findItem("CLUB")).isEmpty();
	}

	@Test
	void nullLookupCodeIsRejected() {
		GameContentCatalog catalog = weaponsOnly(List.of(weapon("CLUB")));
		assertThatNullPointerException().isThrownBy(() -> catalog.findWeapon(null));
	}

	@Test
	void duplicateCodesWithinEachTypeAreRejected() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new GameContentCatalog(List.of(weapon("CLUB"), weapon("CLUB")), List.of(), List.of(), List.of(), List.of()))
				.withMessageContaining("weapon").withMessageContaining("CLUB");
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new GameContentCatalog(List.of(), List.of(passive("CALM"), passive("CALM")), List.of(), List.of(), List.of()))
				.withMessageContaining("passive").withMessageContaining("CALM");
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new GameContentCatalog(List.of(), List.of(), List.of(ability("BLINK"), ability("BLINK")), List.of(), List.of()))
				.withMessageContaining("ability").withMessageContaining("BLINK");
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new GameContentCatalog(List.of(), List.of(), List.of(), List.of(item("ROPE"), item("ROPE")), List.of()))
				.withMessageContaining("item").withMessageContaining("ROPE");
	}

	@Test
	void sameCodeInDifferentTypesIsAllowed() {
		GameContentCatalog catalog = new GameContentCatalog(
				List.of(weapon("EMBER")), List.of(passive("EMBER")), List.of(ability("EMBER")), List.of(item("EMBER")), List.of());
		assertThat(catalog.findWeapon("EMBER")).isPresent();
		assertThat(catalog.findAbility("EMBER")).isPresent();
	}

	@Test
	void acceptsAnyNumberOfWeapons() {
		GameContentCatalog catalog = weaponsOnly(
				List.of(weapon("A"), weapon("B"), weapon("C"), weapon("D"), weapon("E")));
		assertThat(catalog.weapons()).hasSize(5);
		assertThat(catalog.findWeapon("E")).isPresent();
	}

	@Test
	void emptyCatalogIsValid() {
		GameContentCatalog catalog = new GameContentCatalog(List.of(), List.of(), List.of(), List.of(), List.of());
		assertThat(catalog.weapons()).isEmpty();
		assertThat(catalog.passives()).isEmpty();
		assertThat(catalog.abilities()).isEmpty();
		assertThat(catalog.items()).isEmpty();
	}

	@Test
	void preservesAuthoredOrder() {
		GameContentCatalog catalog = weaponsOnly(List.of(weapon("ZWEIHANDER"), weapon("AXE"), weapon("MACE")));
		assertThat(catalog.weapons()).extracting(WeaponDefinition::code).containsExactly("ZWEIHANDER", "AXE", "MACE");
	}

	@Test
	void returnedListsAreUnmodifiable() {
		GameContentCatalog catalog = new GameContentCatalog(
				List.of(weapon("CLUB")), List.of(passive("CALM")), List.of(ability("BLINK")), List.of(item("ROPE")), List.of("Aldric"));
		assertThatThrownBy(() -> catalog.weapons().add(weapon("AXE"))).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> catalog.passives().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> catalog.abilities().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> catalog.items().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> catalog.characterNames().add("Bram")).isInstanceOf(UnsupportedOperationException.class);
	}

	// --- Character names ---

	private static GameContentCatalog namesOnly(List<String> names) {
		return new GameContentCatalog(List.of(), List.of(), List.of(), List.of(), names);
	}

	@Test
	void namesKeepAuthoredOrderExactlyAsAuthored() {
		assertThat(namesOnly(List.of("Wren", "Aldric", "McCorvin")).characterNames())
				.containsExactly("Wren", "Aldric", "McCorvin");
	}

	@Test
	void emptyNamePoolIsValid() {
		assertThat(namesOnly(List.of()).characterNames()).isEmpty();
	}

	@Test
	void blankNamesAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> namesOnly(List.of("")));
		assertThatIllegalArgumentException().isThrownBy(() -> namesOnly(List.of("   ")));
	}

	@Test
	void nullNameIsRejected() {
		assertThatNullPointerException().isThrownBy(() -> namesOnly(Arrays.asList("Aldric", null)));
	}

	@Test
	void untrimmedNamesAreRejectedNotNormalised() {
		assertThatIllegalArgumentException().isThrownBy(() -> namesOnly(List.of(" Aldric")));
		assertThatIllegalArgumentException().isThrownBy(() -> namesOnly(List.of("Aldric ")));
	}

	@Test
	void duplicateNamesAreRejectedIgnoringCase() {
		assertThatIllegalArgumentException().isThrownBy(() -> namesOnly(List.of("Aldric", "Aldric")))
				.withMessageContaining("Aldric");
		assertThatIllegalArgumentException().isThrownBy(() -> namesOnly(List.of("Aldric", "aldric")))
				.withMessageContaining("aldric");
	}

	@Test
	void doesNotRetainCallerNameCollection() {
		List<String> source = new ArrayList<>(List.of("Aldric"));
		GameContentCatalog catalog = namesOnly(source);

		source.add("Bram");

		assertThat(catalog.characterNames()).containsExactly("Aldric");
	}

	@Test
	void doesNotRetainCallerCollections() {
		List<WeaponDefinition> source = new ArrayList<>(List.of(weapon("CLUB")));
		GameContentCatalog catalog = weaponsOnly(source);

		source.add(weapon("AXE"));
		source.remove(0);

		assertThat(catalog.weapons()).extracting(WeaponDefinition::code).containsExactly("CLUB");
		assertThat(catalog.findWeapon("AXE")).isEmpty();
	}

	@Test
	void nullDefinitionIsRejected() {
		List<WeaponDefinition> withNull = Arrays.asList(weapon("CLUB"), null);
		assertThatNullPointerException().isThrownBy(() -> weaponsOnly(withNull));
	}
}
