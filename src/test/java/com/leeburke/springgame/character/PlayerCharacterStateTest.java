package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.GameContentLoader;
import com.leeburke.springgame.content.ItemCategory;
import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.PassiveDefinition;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;

class PlayerCharacterStateTest {

	private static final StatBlock STATS = new StatBlock(
			new StatValue(5), new StatValue(6), new StatValue(5), new StatValue(5), new StatValue(6));
	private static final PassiveDefinition PASSIVE = new PassiveDefinition("CALM", "Calm");
	private static final AbilityDefinition ABILITY = new AbilityDefinition("BLINK", "Blink");
	private static final ToolBeltEntry ROPE = new ToolBeltEntry.Item(new ItemDefinition("ROPE", "Rope", ItemCategory.UTILITY));
	private static final ToolBeltEntry TORCH = new ToolBeltEntry.Item(new ItemDefinition("TORCH", "Torch", ItemCategory.UTILITY));

	private static PlayerCharacterState state(int maxHp, int currentHp, PlayerBody body, ToolBelt belt) {
		return new PlayerCharacterState("Wren", STATS, new Fated(1), maxHp, currentHp, body, PASSIVE, ABILITY, belt);
	}

	private static PlayerCharacterState state(int maxHp, int currentHp) {
		return state(maxHp, currentHp, PlayerBody.healthy(), new ToolBelt(List.of(ROPE)));
	}

	@Test
	void convertsFromGeneratedCharacter() {
		GeneratedCharacter generated = new PlayerCharacterGenerator(GameContentLoader.loadBundled(), new PlayerStatGenerator())
				.generate(new SplittableRandom(42));

		PlayerCharacterState state = PlayerCharacterState.from(generated);

		assertThat(state.name()).isEqualTo(generated.name());
		assertThat(state.stats()).isEqualTo(generated.stats());
		assertThat(state.fated()).isEqualTo(generated.fated());
		assertThat(state.maxHp()).isEqualTo(generated.maxHp());
		assertThat(state.currentHp()).isEqualTo(generated.currentHp());
		assertThat(state.body()).isEqualTo(generated.body());
		assertThat(state.passive()).isEqualTo(generated.passive());
		assertThat(state.ability()).isEqualTo(generated.ability());
		assertThat(state.toolBelt()).isEqualTo(generated.toolBelt());
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 5, 26 })
	void currentHpFromZeroToMaxIsValid(int currentHp) {
		assertThat(state(26, currentHp).currentHp()).isEqualTo(currentHp);
	}

	@Test
	void rejectsNegativeCurrentHp() {
		assertThatIllegalArgumentException().isThrownBy(() -> state(26, -1));
	}

	@Test
	void rejectsCurrentHpAboveMax() {
		assertThatIllegalArgumentException().isThrownBy(() -> state(26, 27));
	}

	@Test
	void rejectsMaxHpBelowOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> state(0, 0));
	}

	@Test
	void rejectsBlankName() {
		assertThatIllegalArgumentException().isThrownBy(() -> new PlayerCharacterState(
				" ", STATS, new Fated(1), 26, 26, PlayerBody.healthy(), PASSIVE, ABILITY, new ToolBelt(List.of())));
	}

	@Test
	void rejectsNullComponents() {
		PlayerBody body = PlayerBody.healthy();
		ToolBelt belt = new ToolBelt(List.of());
		Fated fated = new Fated(1);
		assertThatNullPointerException().isThrownBy(() -> new PlayerCharacterState(null, STATS, fated, 26, 26, body, PASSIVE, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new PlayerCharacterState("W", null, fated, 26, 26, body, PASSIVE, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new PlayerCharacterState("W", STATS, null, 26, 26, body, PASSIVE, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new PlayerCharacterState("W", STATS, fated, 26, 26, null, PASSIVE, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new PlayerCharacterState("W", STATS, fated, 26, 26, body, null, ABILITY, belt));
		assertThatNullPointerException().isThrownBy(() -> new PlayerCharacterState("W", STATS, fated, 26, 26, body, PASSIVE, null, belt));
		assertThatNullPointerException().isThrownBy(() -> new PlayerCharacterState("W", STATS, fated, 26, 26, body, PASSIVE, ABILITY, null));
	}

	@Test
	void bodyMayContainNonHealthySeverities() {
		Map<BodyPart, BodySeverity> severities = new EnumMap<>(PlayerBody.healthy().severities());
		severities.put(BodyPart.LEFT_ARM, BodySeverity.WOUNDED);
		severities.put(BodyPart.RIGHT_LEG, BodySeverity.DESTROYED);

		PlayerCharacterState state = state(26, 10, new PlayerBody(severities), new ToolBelt(List.of(ROPE)));

		assertThat(state.body().severity(BodyPart.RIGHT_LEG)).isEqualTo(BodySeverity.DESTROYED);
	}

	@Test
	void toolBeltNeedNotMatchStartingComposition() {
		assertThat(state(26, 26, PlayerBody.healthy(), new ToolBelt(List.of(ROPE, TORCH))).toolBelt().occupiedSlots()).isEqualTo(2);
		assertThat(state(26, 26, PlayerBody.healthy(), new ToolBelt(List.of())).toolBelt().occupiedSlots()).isZero();
		assertThat(state(26, 26, PlayerBody.healthy(), new ToolBelt(List.of(ROPE, TORCH, ROPE, TORCH, ROPE))).toolBelt().emptySlots())
				.isZero();
	}
}
