package com.leeburke.springgame.mechanics;

/**
 * How a check's degree of success becomes contact. See docs/GAME_RULES.md "Damage" and
 * "Defense Resolution". The single location for both mappings.
 */
public final class ContactRules {

	private ContactRules() {
	}

	/** The attacker's degree: critical CLEAN, success SOLID, partial GLANCING, failure NONE. */
	public static ContactQuality attackContact(DegreeOfSuccess degree) {
		return switch (degree) {
			case CRITICAL_SUCCESS -> ContactQuality.CLEAN;
			case SUCCESS -> ContactQuality.SOLID;
			case PARTIAL_SUCCESS -> ContactQuality.GLANCING;
			case FAILURE -> ContactQuality.NONE;
		};
	}

	/**
	 * The defender's degree against an incoming attack: critical or success NONE (avoided),
	 * partial GLANCING, failure SOLID. The same for every defense method; method differences and
	 * defensive mitigation are deferred.
	 */
	public static ContactQuality incomingContact(DegreeOfSuccess defenderDegree) {
		return switch (defenderDegree) {
			case CRITICAL_SUCCESS, SUCCESS -> ContactQuality.NONE;
			case PARTIAL_SUCCESS -> ContactQuality.GLANCING;
			case FAILURE -> ContactQuality.SOLID;
		};
	}
}
