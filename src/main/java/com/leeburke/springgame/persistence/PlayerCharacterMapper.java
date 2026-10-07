package com.leeburke.springgame.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.leeburke.springgame.character.Fated;
import com.leeburke.springgame.character.PlayerBody;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.ToolBelt;
import com.leeburke.springgame.character.ToolBeltEntry;
import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.PassiveDefinition;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;

/**
 * Converts between {@link PlayerCharacterState} and {@link PlayerCharacterEntity}. Plain Java.
 * <p>
 * Static content is stored by definition code only. On save, every referenced definition must be
 * exactly the catalogue's definition for its code, so hand-built or stale definitions cannot be
 * persisted as if they were current authored content. On load, codes are resolved through the
 * catalogue; an unknown code or any invariant failure raises {@link PersistedStateException}.
 */
final class PlayerCharacterMapper {

	private final GameContentCatalog catalog;

	PlayerCharacterMapper(GameContentCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
	}

	PlayerCharacterEntity toEntity(UUID runId, PlayerCharacterState state) {
		Objects.requireNonNull(runId, "runId");
		Objects.requireNonNull(state, "state");
		requireCatalogued("passive", state.passive(), catalog.findPassive(state.passive().code()));
		requireCatalogued("ability", state.ability(), catalog.findAbility(state.ability().code()));

		List<ToolBeltEntryEmbeddable> belt = new ArrayList<>();
		for (ToolBeltEntry entry : state.toolBelt().entries()) {
			belt.add(switch (entry) {
				case ToolBeltEntry.Weapon(WeaponDefinition definition) -> {
					requireCatalogued("weapon", definition, catalog.findWeapon(definition.code()));
					yield new ToolBeltEntryEmbeddable(ToolBeltEntryKind.WEAPON, definition.code());
				}
				case ToolBeltEntry.Item(ItemDefinition definition) -> {
					requireCatalogued("item", definition, catalog.findItem(definition.code()));
					yield new ToolBeltEntryEmbeddable(ToolBeltEntryKind.ITEM, definition.code());
				}
			});
		}

		StatBlock stats = state.stats();
		return new PlayerCharacterEntity(
				runId,
				state.name(),
				stats.might().value(),
				stats.agility().value(),
				stats.perception().value(),
				stats.arcana().value(),
				stats.resolve().value(),
				state.fated().value(),
				state.maxHp(),
				state.currentHp(),
				state.passive().code(),
				state.ability().code(),
				state.body().severities(),
				belt);
	}

	PlayerCharacterState toDomain(PlayerCharacterEntity entity) {
		Objects.requireNonNull(entity, "entity");
		UUID runId = entity.getRunId();
		PassiveDefinition passive = resolve(runId, "passive", entity.getPassiveCode(), catalog.findPassive(entity.getPassiveCode()));
		AbilityDefinition ability = resolve(runId, "ability", entity.getAbilityCode(), catalog.findAbility(entity.getAbilityCode()));

		List<ToolBeltEntry> belt = new ArrayList<>();
		List<ToolBeltEntryEmbeddable> rows = entity.getToolBelt();
		for (int slot = 0; slot < rows.size(); slot++) {
			ToolBeltEntryEmbeddable row = rows.get(slot);
			if (row == null) {
				throw new PersistedStateException("Run " + runId + ": missing tool belt entry in slot " + slot);
			}
			String code = row.getDefinitionCode();
			String where = "tool belt slot " + slot;
			belt.add(switch (row.getKind()) {
				case WEAPON -> new ToolBeltEntry.Weapon(resolve(runId, "weapon", code, catalog.findWeapon(code), where));
				case ITEM -> new ToolBeltEntry.Item(resolve(runId, "item", code, catalog.findItem(code), where));
			});
		}

		try {
			StatBlock stats = new StatBlock(
					new StatValue(entity.getMight()),
					new StatValue(entity.getAgility()),
					new StatValue(entity.getPerception()),
					new StatValue(entity.getArcana()),
					new StatValue(entity.getResolve()));
			return new PlayerCharacterState(
					entity.getName(),
					stats,
					new Fated(entity.getFated()),
					entity.getMaxHp(),
					entity.getCurrentHp(),
					new PlayerBody(entity.getBodyParts()),
					passive,
					ability,
					new ToolBelt(belt));
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new PersistedStateException("Run " + runId + ": stored player character is invalid: " + e.getMessage(), e);
		}
	}

	private static <T> void requireCatalogued(String type, T definition, Optional<T> catalogued) {
		if (catalogued.filter(definition::equals).isEmpty()) {
			throw new IllegalArgumentException(
					"Cannot persist " + type + " " + definition + ": it is not the current catalogue definition for its code");
		}
	}

	private static <T> T resolve(UUID runId, String type, String code, Optional<T> found) {
		return resolve(runId, type, code, found, type);
	}

	private static <T> T resolve(UUID runId, String type, String code, Optional<T> found, String where) {
		return found.orElseThrow(() -> new PersistedStateException(
				"Run " + runId + ": unknown " + type + " code '" + code + "' in " + where + "; it is not in the current content catalogue"));
	}
}
