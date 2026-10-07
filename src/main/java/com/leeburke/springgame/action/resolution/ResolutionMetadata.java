package com.leeburke.springgame.action.resolution;

/**
 * Factual resolution metadata.
 *
 * @param rollsConsumed d20 rolls drawn from the supplied random source
 * @param rulesVersion  version of the resolution rules that produced the outcome
 */
public record ResolutionMetadata(int rollsConsumed, int rulesVersion) {

	public static final int CURRENT_RULES_VERSION = 1;

	public ResolutionMetadata {
		if (rollsConsumed < 0 || rulesVersion < 1) {
			throw new IllegalArgumentException("Invalid resolution metadata");
		}
	}
}
