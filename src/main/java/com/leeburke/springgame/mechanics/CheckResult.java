package com.leeburke.springgame.mechanics;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of a resolved check and its full mechanics breakdown. Values are stored, not
 * recomputed, so the result keeps showing what was actually resolved. Produced by {@link CheckResolver}.
 *
 * @param appliedAdjustments the suitability adjustment first (when present), then the request's
 *                           adjustments in their given order
 * @param adjustmentTotal    sum of applied adjustments before clamping
 */
public record CheckResult(
		StatType stat,
		StatValue statValue,
		int statModifier,
		int rawRoll,
		int rollTotal,
		int baseDc,
		Optional<Suitability> suitability,
		List<DcAdjustment> appliedAdjustments,
		int adjustmentTotal,
		int clampedAdjustmentTotal,
		int finalDc,
		int margin,
		DegreeOfSuccess degree) {

	public CheckResult {
		Objects.requireNonNull(stat, "stat");
		Objects.requireNonNull(statValue, "statValue");
		Objects.requireNonNull(suitability, "suitability");
		appliedAdjustments = List.copyOf(Objects.requireNonNull(appliedAdjustments, "appliedAdjustments"));
		Objects.requireNonNull(degree, "degree");
		CheckRules.requireValidD20Roll(rawRoll);
	}
}
