package com.leeburke.springgame.mechanics;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of a trauma calculation: the validated inputs plus the impact, which is present
 * exactly when contact occurred. {@code NONE} contact has no impact and no {@link ImpactSeverity}.
 * Produced by {@link TraumaCalculator}.
 */
public record TraumaResult(TraumaRequest request, Optional<TraumaImpact> impact) {

	public TraumaResult {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(impact, "impact");
		boolean contact = request.contactQuality() != ContactQuality.NONE;
		if (contact != impact.isPresent()) {
			throw new IllegalArgumentException(contact
					? "Contact occurred, so a trauma impact is required"
					: "No contact occurred, so there can be no trauma impact");
		}
	}

	public Optional<ImpactSeverity> impactSeverity() {
		return impact.map(TraumaImpact::severity);
	}
}
