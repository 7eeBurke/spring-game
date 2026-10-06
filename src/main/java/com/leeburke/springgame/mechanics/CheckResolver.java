package com.leeburke.springgame.mechanics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Resolves {@code d20 + stat modifier} against {@code BaseDC + clamp(sum of adjustments, -5, +5)}.
 * <p>
 * {@link #resolve(CheckRequest, int)} holds all rule logic and takes an explicit roll, so rules are
 * testable without randomness. {@link #resolve(CheckRequest, RandomGenerator)} only adds the d20.
 * All derived arithmetic is overflow-checked because {@code baseDc} has no documented range.
 */
public final class CheckResolver {

	public CheckResult resolve(CheckRequest request, RandomGenerator rng) {
		Objects.requireNonNull(request, "request");
		return resolve(request, rollD20(rng));
	}

	public CheckResult resolve(CheckRequest request, int rawRoll) {
		Objects.requireNonNull(request, "request");
		CheckRules.requireValidD20Roll(rawRoll);

		List<DcAdjustment> applied = new ArrayList<>();
		request.suitability().ifPresent(s -> applied.add(
				new DcAdjustment(DcAdjustmentSource.SUITABILITY, CheckRules.suitabilityDcAdjustment(s))));
		applied.addAll(request.adjustments());

		int adjustmentTotal = 0;
		for (DcAdjustment adjustment : applied) {
			adjustmentTotal = Math.addExact(adjustmentTotal, adjustment.value());
		}
		int clampedTotal = CheckRules.clampAdjustmentTotal(adjustmentTotal);
		int finalDc = Math.addExact(request.baseDc(), clampedTotal);

		int statModifier = CheckRules.statModifier(request.statValue());
		int rollTotal = Math.addExact(rawRoll, statModifier);
		int margin = Math.subtractExact(rollTotal, finalDc);

		return new CheckResult(
				request.stat(),
				request.statValue(),
				statModifier,
				rawRoll,
				rollTotal,
				request.baseDc(),
				request.suitability(),
				applied,
				adjustmentTotal,
				clampedTotal,
				finalDc,
				margin,
				CheckRules.degreeFor(margin));
	}

	/** Uniform over 1..20 inclusive, per the {@link RandomGenerator#nextInt(int, int)} contract. */
	public static int rollD20(RandomGenerator rng) {
		Objects.requireNonNull(rng, "rng");
		return rng.nextInt(CheckRules.D20_MIN, CheckRules.D20_MAX + 1);
	}
}
