package com.leeburke.springgame.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.leeburke.springgame.character.Fated;
import com.leeburke.springgame.character.PlayerBody;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.PlayerStatGenerator;
import com.leeburke.springgame.character.ToolBelt;
import com.leeburke.springgame.character.ToolBeltEntry;
import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.GameContentLoader;
import com.leeburke.springgame.content.PassiveDefinition;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.DamageType;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;

/** Mapping between domain state and entities, in memory, against the bundled catalogue. No database. */
class PlayerCharacterMapperTest {

	private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

	private static GameContentCatalog catalog;
	private static PlayerCharacterMapper mapper;

	@BeforeAll
	static void setUp() {
		catalog = GameContentLoader.loadBundled();
		mapper = new PlayerCharacterMapper(catalog);
	}

	private static PlayerCharacterState generatedState() {
		return PlayerCharacterState.from(new PlayerCharacterGenerator(catalog, new PlayerStatGenerator())
				.generate(new SplittableRandom(42)));
	}

	/** A non-starting state: damaged, wounded, and a belt that is not the starting composition. */
	private static PlayerCharacterState laterState() {
		Map<BodyPart, BodySeverity> severities = new EnumMap<>(PlayerBody.healthy().severities());
		severities.put(BodyPart.LEFT_ARM, BodySeverity.WOUNDED);
		ToolBelt belt = new ToolBelt(List.of(
				new ToolBeltEntry.Item(catalog.findItem("TORCH").orElseThrow()),
				new ToolBeltEntry.Weapon(catalog.findWeapon("DAGGER").orElseThrow()),
				new ToolBeltEntry.Item(catalog.findItem("BANDAGE").orElseThrow()),
				new ToolBeltEntry.Item(catalog.findItem("TORCH").orElseThrow())));
		return new PlayerCharacterState("Morwen",
				new StatBlock(new StatValue(9), new StatValue(3), new StatValue(4), new StatValue(7), new StatValue(4)),
				new Fated(4), 24, 4, new PlayerBody(severities),
				catalog.findPassive("GRAVE_SENSE").orElseThrow(),
				catalog.findAbility("SHADOW_STEP").orElseThrow(),
				belt);
	}

	/** An entity copy of {@code base} with selected stored values replaced, simulating corrupt rows. */
	private static PlayerCharacterEntity corrupt(PlayerCharacterEntity base, int might, int maxHp, int currentHp,
			String passiveCode, String abilityCode, Map<BodyPart, BodySeverity> body, List<ToolBeltEntryEmbeddable> belt) {
		return new PlayerCharacterEntity(base.getRunId(), base.getName(), might, base.getAgility(), base.getPerception(),
				base.getArcana(), base.getResolve(), base.getFated(), maxHp, currentHp, passiveCode, abilityCode, body, belt);
	}

	private static PlayerCharacterEntity withBelt(PlayerCharacterEntity base, List<ToolBeltEntryEmbeddable> belt) {
		return corrupt(base, base.getMight(), base.getMaxHp(), base.getCurrentHp(), base.getPassiveCode(),
				base.getAbilityCode(), base.getBodyParts(), belt);
	}

	@Test
	void generatedStateRoundTrips() {
		PlayerCharacterState state = generatedState();
		assertThat(mapper.toDomain(mapper.toEntity(RUN_ID, state))).isEqualTo(state);
	}

	@Test
	void nonStartingStateRoundTripsIncludingBeltOrder() {
		PlayerCharacterState state = laterState();

		PlayerCharacterState loaded = mapper.toDomain(mapper.toEntity(RUN_ID, state));

		assertThat(loaded).isEqualTo(state);
		assertThat(loaded.currentHp()).isEqualTo(4);
		assertThat(loaded.body().severity(BodyPart.LEFT_ARM)).isEqualTo(BodySeverity.WOUNDED);
		assertThat(loaded.toolBelt().entries()).hasSize(4);
	}

	@Test
	void entityStoresDefinitionCodesOnly() {
		PlayerCharacterEntity entity = mapper.toEntity(RUN_ID, laterState());

		assertThat(entity.getRunId()).isEqualTo(RUN_ID);
		assertThat(entity.getMight()).isEqualTo(9);
		assertThat(entity.getResolve()).isEqualTo(4);
		assertThat(entity.getFated()).isEqualTo(4);
		assertThat(entity.getMaxHp()).isEqualTo(24);
		assertThat(entity.getCurrentHp()).isEqualTo(4);
		assertThat(entity.getPassiveCode()).isEqualTo("GRAVE_SENSE");
		assertThat(entity.getAbilityCode()).isEqualTo("SHADOW_STEP");
		assertThat(entity.getBodyParts()).hasSize(BodyPart.values().length).containsEntry(BodyPart.LEFT_ARM, BodySeverity.WOUNDED);
		assertThat(entity.getToolBelt()).extracting(ToolBeltEntryEmbeddable::getKind, ToolBeltEntryEmbeddable::getDefinitionCode)
				.containsExactly(
						tuple(ToolBeltEntryKind.ITEM, "TORCH"),
						tuple(ToolBeltEntryKind.WEAPON, "DAGGER"),
						tuple(ToolBeltEntryKind.ITEM, "BANDAGE"),
						tuple(ToolBeltEntryKind.ITEM, "TORCH"));
	}

	// --- Unknown static content on load ---

	@Test
	void unknownPassiveCodeFailsClearly() {
		PlayerCharacterEntity base = mapper.toEntity(RUN_ID, laterState());
		PlayerCharacterEntity entity = corrupt(base, base.getMight(), base.getMaxHp(), base.getCurrentHp(),
				"REMOVED_PASSIVE", base.getAbilityCode(), base.getBodyParts(), base.getToolBelt());
		assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("REMOVED_PASSIVE").hasMessageContaining(RUN_ID.toString());
	}

	@Test
	void unknownAbilityCodeFailsClearly() {
		PlayerCharacterEntity base = mapper.toEntity(RUN_ID, laterState());
		PlayerCharacterEntity entity = corrupt(base, base.getMight(), base.getMaxHp(), base.getCurrentHp(),
				base.getPassiveCode(), "REMOVED_ABILITY", base.getBodyParts(), base.getToolBelt());
		assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("REMOVED_ABILITY");
	}

	@Test
	void unknownWeaponCodeInBeltFailsClearly() {
		PlayerCharacterEntity entity = withBelt(mapper.toEntity(RUN_ID, laterState()),
				List.of(new ToolBeltEntryEmbeddable(ToolBeltEntryKind.WEAPON, "REMOVED_SWORD")));
		assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("REMOVED_SWORD").hasMessageContaining("slot 0");
	}

	@Test
	void unknownItemCodeInBeltFailsClearly() {
		PlayerCharacterEntity entity = withBelt(mapper.toEntity(RUN_ID, laterState()),
				List.of(new ToolBeltEntryEmbeddable(ToolBeltEntryKind.ITEM, "REMOVED_ROPE")));
		assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("REMOVED_ROPE");
	}

	@Test
	void itemRowHoldingAWeaponCodeFails() {
		PlayerCharacterEntity entity = withBelt(mapper.toEntity(RUN_ID, laterState()),
				List.of(new ToolBeltEntryEmbeddable(ToolBeltEntryKind.ITEM, "LONGSWORD")));
		assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("LONGSWORD");
	}

	// --- Corrupt stored state never becomes valid domain state ---

	@Test
	void currentHpAboveMaxFails() {
		PlayerCharacterEntity base = mapper.toEntity(RUN_ID, laterState());
		PlayerCharacterEntity entity = corrupt(base, base.getMight(), 24, 25,
				base.getPassiveCode(), base.getAbilityCode(), base.getBodyParts(), base.getToolBelt());
		assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(PersistedStateException.class);
	}

	@Test
	void missingBodyPartFails() {
		PlayerCharacterEntity base = mapper.toEntity(RUN_ID, laterState());
		Map<BodyPart, BodySeverity> body = new EnumMap<>(base.getBodyParts());
		body.remove(BodyPart.HEART);
		PlayerCharacterEntity entity = corrupt(base, base.getMight(), base.getMaxHp(), base.getCurrentHp(),
				base.getPassiveCode(), base.getAbilityCode(), body, base.getToolBelt());
		assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("HEART");
	}

	@Test
	void outOfRangeStatFails() {
		PlayerCharacterEntity base = mapper.toEntity(RUN_ID, laterState());
		PlayerCharacterEntity entity = corrupt(base, 11, base.getMaxHp(), base.getCurrentHp(),
				base.getPassiveCode(), base.getAbilityCode(), base.getBodyParts(), base.getToolBelt());
		assertThatThrownBy(() -> mapper.toDomain(entity)).isInstanceOf(PersistedStateException.class);
	}

	// --- Only current catalogue content can be saved ---

	private static PlayerCharacterState withWeaponInBelt(WeaponDefinition weapon) {
		PlayerCharacterState base = laterState();
		return new PlayerCharacterState(base.name(), base.stats(), base.fated(), base.maxHp(), base.currentHp(),
				base.body(), base.passive(), base.ability(), new ToolBelt(List.of(new ToolBeltEntry.Weapon(weapon))));
	}

	@Test
	void savingUnknownContentCodeIsRejected() {
		PlayerCharacterState state = withWeaponInBelt(new WeaponDefinition("HOMEMADE_SPEAR", "Spear", 5, 2, DamageType.PIERCING));
		assertThatIllegalArgumentException().isThrownBy(() -> mapper.toEntity(RUN_ID, state))
				.withMessageContaining("HOMEMADE_SPEAR");
	}

	@Test
	void savingKnownCodeWithAlteredDataIsRejected() {
		PlayerCharacterState state = withWeaponInBelt(new WeaponDefinition("LONGSWORD", "Longsword", 99, 3, DamageType.SLASHING));
		assertThatIllegalArgumentException().isThrownBy(() -> mapper.toEntity(RUN_ID, state))
				.withMessageContaining("LONGSWORD");
	}

	@Test
	void savingUncataloguedPassiveOrAbilityIsRejected() {
		PlayerCharacterState base = laterState();
		PlayerCharacterState badPassive = new PlayerCharacterState(base.name(), base.stats(), base.fated(), base.maxHp(),
				base.currentHp(), base.body(), new PassiveDefinition("GRAVE_SENSE", "Renamed"), base.ability(), base.toolBelt());
		PlayerCharacterState badAbility = new PlayerCharacterState(base.name(), base.stats(), base.fated(), base.maxHp(),
				base.currentHp(), base.body(), base.passive(), new AbilityDefinition("NOT_AN_ABILITY", "Nope"), base.toolBelt());
		assertThatIllegalArgumentException().isThrownBy(() -> mapper.toEntity(RUN_ID, badPassive));
		assertThatIllegalArgumentException().isThrownBy(() -> mapper.toEntity(RUN_ID, badAbility));
	}
}
