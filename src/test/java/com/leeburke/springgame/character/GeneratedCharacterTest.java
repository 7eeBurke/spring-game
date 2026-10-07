package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.ItemCategory;
import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.PassiveDefinition;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.DamageType;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;

class GeneratedCharacterTest {

	private static final WeaponDefinition SWORD = new WeaponDefinition("SWORD", "Sword", 6, 3, DamageType.SLASHING);
	private static final ItemDefinition BANDAGE = new ItemDefinition("BANDAGE", "Bandage", ItemCategory.RECOVERY);
	private static final ItemDefinition ROPE = new ItemDefinition("ROPE", "Rope", ItemCategory.UTILITY);
	private static final ItemDefinition TORCH = new ItemDefinition("TORCH", "Torch", ItemCategory.UTILITY);
	private static final PassiveDefinition PASSIVE = new PassiveDefinition("CALM", "Calm");
	private static final AbilityDefinition ABILITY = new AbilityDefinition("BLINK", "Blink");
	private static final StatBlock STATS = new StatBlock(
			new StatValue(5), new StatValue(6), new StatValue(5), new StatValue(5), new StatValue(6));

	private static ToolBelt belt(ToolBeltEntry... entries) {
		return new ToolBelt(List.of(entries));
	}

	private static ToolBelt validBelt() {
		return belt(new ToolBeltEntry.Weapon(SWORD), new ToolBeltEntry.Item(BANDAGE), new ToolBeltEntry.Item(ROPE));
	}

	private static GeneratedCharacter character(String name, int maxHp, int currentHp, PlayerBody body, ToolBelt belt) {
		return new GeneratedCharacter(name, STATS, new Fated(2), maxHp, currentHp, body, PASSIVE, ABILITY, belt);
	}

	@Test
	void validCharacterExposesBeltContents() {
		GeneratedCharacter character = character("Aldric", 26, 26, PlayerBody.healthy(), validBelt());
		assertThat(character.weapon()).isEqualTo(SWORD);
		assertThat(character.recoveryItem()).isEqualTo(BANDAGE);
		assertThat(character.utilityItem()).isEqualTo(ROPE);
		assertThat(character.toolBelt().emptySlots()).isEqualTo(2);
	}

	@Test
	void rejectsBlankName() {
		assertThatIllegalArgumentException().isThrownBy(() -> character(" ", 26, 26, PlayerBody.healthy(), validBelt()));
	}

	@Test
	void rejectsCurrentHpDifferentFromMax() {
		assertThatIllegalArgumentException().isThrownBy(() -> character("Aldric", 26, 25, PlayerBody.healthy(), validBelt()));
	}

	@Test
	void rejectsNonPositiveMaxHp() {
		assertThatIllegalArgumentException().isThrownBy(() -> character("Aldric", 0, 0, PlayerBody.healthy(), validBelt()));
	}

	@Test
	void rejectsBeltWithoutWeapon() {
		ToolBelt noWeapon = belt(new ToolBeltEntry.Item(BANDAGE), new ToolBeltEntry.Item(ROPE), new ToolBeltEntry.Item(TORCH));
		assertThatIllegalArgumentException().isThrownBy(() -> character("Aldric", 26, 26, PlayerBody.healthy(), noWeapon));
	}

	@Test
	void rejectsBeltWithTwoWeapons() {
		ToolBelt twoWeapons = belt(new ToolBeltEntry.Weapon(SWORD), new ToolBeltEntry.Weapon(SWORD),
				new ToolBeltEntry.Item(BANDAGE), new ToolBeltEntry.Item(ROPE));
		assertThatIllegalArgumentException().isThrownBy(() -> character("Aldric", 26, 26, PlayerBody.healthy(), twoWeapons));
	}

	@Test
	void rejectsBeltWithTwoUtilityAndNoRecovery() {
		ToolBelt noRecovery = belt(new ToolBeltEntry.Weapon(SWORD), new ToolBeltEntry.Item(ROPE), new ToolBeltEntry.Item(TORCH));
		assertThatIllegalArgumentException().isThrownBy(() -> character("Aldric", 26, 26, PlayerBody.healthy(), noRecovery));
	}

	@Test
	void rejectsBeltWithOnlyTwoEntries() {
		ToolBelt twoEntries = belt(new ToolBeltEntry.Weapon(SWORD), new ToolBeltEntry.Item(BANDAGE));
		assertThatIllegalArgumentException().isThrownBy(() -> character("Aldric", 26, 26, PlayerBody.healthy(), twoEntries));
	}

	@Test
	void rejectsNullComponents() {
		PlayerBody body = PlayerBody.healthy();
		ToolBelt belt = validBelt();
		Fated fated = new Fated(0);
		assertThatNullPointerException().isThrownBy(() -> new GeneratedCharacter(null, STATS, fated, 26, 26, body, PASSIVE, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new GeneratedCharacter("A", null, fated, 26, 26, body, PASSIVE, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new GeneratedCharacter("A", STATS, null, 26, 26, body, PASSIVE, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new GeneratedCharacter("A", STATS, fated, 26, 26, null, PASSIVE, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new GeneratedCharacter("A", STATS, fated, 26, 26, body, null, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new GeneratedCharacter("A", STATS, fated, 26, 26, body, PASSIVE, null, belt));
		assertThatNullPointerException().isThrownBy(() -> new GeneratedCharacter("A", STATS, fated, 26, 26, body, PASSIVE, ABILITY, null));
	}

	// The model enforces structure only; exact V1 generation policy belongs to the generator.

	@Test
	void acceptsMaxHpNotMatchingV1Formula() {
		assertThat(character("Aldric", 40, 40, PlayerBody.healthy(), validBelt()).maxHp()).isEqualTo(40);
	}

	@Test
	void acceptsNonHealthyBody() {
		Map<BodyPart, BodySeverity> severities = new EnumMap<>(PlayerBody.healthy().severities());
		severities.put(BodyPart.RIGHT_LEG, BodySeverity.WOUNDED);
		GeneratedCharacter character = character("Aldric", 26, 26, new PlayerBody(severities), validBelt());
		assertThat(character.body().severity(BodyPart.RIGHT_LEG)).isEqualTo(BodySeverity.WOUNDED);
	}
}
