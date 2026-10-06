package com.leeburke.springgame.mechanics;

import java.util.Objects;
import java.util.Optional;

/**
 * Computes the trauma score
 * {@code weaponTrauma + contactModifier + existingInjury + anatomyInteraction + attackForm
 * - traumaProtection - defensiveMitigation}, clamps it to at least 0 and maps it to an
 * {@link ImpactSeverity}. Without contact nothing is computed.
 * Arithmetic is overflow-checked and throws {@link ArithmeticException} rather than wrapping.
 */
public final class TraumaCalculator {

	public TraumaResult calculate(TraumaRequest request) {
		Objects.requireNonNull(request, "request");
		if (request.contactQuality() == ContactQuality.NONE) {
			return new TraumaResult(request, Optional.empty());
		}

		int contactModifier = TraumaRules.contactModifier(request.contactQuality());
		int raw = request.weaponTrauma();
		raw = Math.addExact(raw, contactModifier);
		raw = Math.addExact(raw, request.existingInjuryModifier());
		raw = Math.addExact(raw, request.anatomyInteractionModifier());
		raw = Math.addExact(raw, request.attackFormModifier());
		raw = Math.subtractExact(raw, request.traumaProtection());
		raw = Math.subtractExact(raw, request.defensiveMitigation());

		int effective = Math.max(0, raw);
		TraumaImpact impact = new TraumaImpact(contactModifier, raw, effective, TraumaRules.severityFor(effective));
		return new TraumaResult(request, Optional.of(impact));
	}
}
