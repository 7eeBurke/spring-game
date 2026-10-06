package com.leeburke.springgame.mechanics;

import java.util.Objects;

/**
 * One already-determined numeric DC adjustment and its source. The value is not individually
 * limited: the cap applies to the sum of all adjustments in a check.
 */
public record DcAdjustment(DcAdjustmentSource source, int value) {

	public DcAdjustment {
		Objects.requireNonNull(source, "source");
	}
}
