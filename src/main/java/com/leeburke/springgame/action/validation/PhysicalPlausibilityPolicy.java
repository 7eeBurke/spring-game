package com.leeburke.springgame.action.validation;

import java.util.Optional;

import com.leeburke.springgame.action.ActionStep;

/**
 * The last validation layer: judges whether a step is physically impossible (no check is rolled),
 * as distinct from merely poorly suited (TERRIBLE suitability still rolls).
 * <p>
 * Consulted only for steps that passed structure, scene-reference and ownership validation and are
 * not affected by an unresolved reference. It must declare impossibility only when the facts it
 * can see prove it, and it never chooses stats, DCs, suitability or rolls.
 */
@FunctionalInterface
public interface PhysicalPlausibilityPolicy {

	/** @return a safe reason when the step is provably impossible, otherwise empty */
	Optional<String> impossibility(ActionStep step, ActionValidationContext context);

	/**
	 * The current baseline. The player view holds only identity, zone placement and visibility (no
	 * mass, strength, distance, anatomy or material properties), so nothing can yet be proven
	 * impossible. Action resolution adds real rules through this seam.
	 */
	PhysicalPlausibilityPolicy NO_PROVEN_IMPOSSIBILITIES = (step, context) -> Optional.empty();
}
