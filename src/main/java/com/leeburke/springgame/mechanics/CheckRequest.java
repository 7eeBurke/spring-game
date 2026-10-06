package com.leeburke.springgame.mechanics;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything needed to resolve one check.
 * <p>
 * Suitability is optional and travels in its own field, never as an adjustment, so it cannot be
 * counted twice. An {@code IMPOSSIBLE} request cannot be built: impossible actions must be
 * rejected before resolution. {@code baseDc} has no documented legal range and is not limited.
 */
public record CheckRequest(
		StatType stat,
		StatValue statValue,
		int baseDc,
		Optional<Suitability> suitability,
		List<DcAdjustment> adjustments) {

	public CheckRequest {
		Objects.requireNonNull(stat, "stat");
		Objects.requireNonNull(statValue, "statValue");
		Objects.requireNonNull(suitability, "suitability");
		adjustments = List.copyOf(Objects.requireNonNull(adjustments, "adjustments"));
		if (suitability.isPresent() && !CheckRules.permitsRoll(suitability.get())) {
			throw new IllegalArgumentException(
					"A check cannot be made with IMPOSSIBLE suitability; impossible actions must be rejected before resolution");
		}
		if (adjustments.stream().anyMatch(a -> a.source() == DcAdjustmentSource.SUITABILITY)) {
			throw new IllegalArgumentException("Suitability must be supplied through the suitability field, not as an adjustment");
		}
	}
}
