package com.leeburke.springgame.content.world;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.shared.DefinitionCodes;
import com.leeburke.springgame.world.LocalIds;

/**
 * One optional piece of content an archetype may place: of one kind, chosen uniformly from
 * {@code candidates}, placed uniformly in one of {@code zones}.
 *
 * @param id           becomes the placed content's local ID
 * @param chance       percent chance (1..100) that the slot is filled
 * @param hiddenChance percent chance (0..100) that filled content starts hidden
 */
public record ContentSlot(
		String id,
		WorldElementKind kind,
		List<String> candidates,
		List<String> zones,
		int chance,
		int hiddenChance) {

	public ContentSlot {
		LocalIds.requireLocalId(id, "Slot id");
		Objects.requireNonNull(kind, "kind");
		candidates = WorldText.uniqueList(candidates, "candidates of slot " + id, true);
		candidates.forEach(code -> DefinitionCodes.requireCode(code, "Slot " + id + " candidate"));
		zones = WorldText.uniqueList(zones, "zones of slot " + id, true);
		zones.forEach(zone -> LocalIds.requireLocalId(zone, "Slot " + id + " zone"));
		if (chance < 1 || chance > 100) {
			throw new IllegalArgumentException("Slot " + id + " chance must be 1..100, but was " + chance);
		}
		if (hiddenChance < 0 || hiddenChance > 100) {
			throw new IllegalArgumentException("Slot " + id + " hiddenChance must be 0..100, but was " + hiddenChance);
		}
	}
}
