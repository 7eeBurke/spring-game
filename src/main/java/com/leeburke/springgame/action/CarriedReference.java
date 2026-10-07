package com.leeburke.springgame.action;

import java.util.Objects;

/** An owned weapon or item used in an interaction, by its opaque action-context reference. */
public record CarriedReference(CarriedKind kind, String ref) {

	public CarriedReference {
		Objects.requireNonNull(kind, "kind");
		Refs.require(ref, "Carried reference");
	}
}
