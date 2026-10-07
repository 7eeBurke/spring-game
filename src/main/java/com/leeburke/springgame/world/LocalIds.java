package com.leeburke.springgame.world;

/**
 * Validation for scene-local IDs (zone, connection, entity, object, hazard, exit and event IDs).
 * Unique within their own collection. No format beyond this is imposed: the ID format exposed to
 * AI roles is still deferred.
 */
final class LocalIds {

	private LocalIds() {
	}

	static String requireLocalId(String id, String label) {
		if (id == null) {
			throw new NullPointerException(label + " must not be null");
		}
		if (id.isBlank() || !id.equals(id.strip())) {
			throw new IllegalArgumentException(label + " must be non-blank with no leading or trailing whitespace, but was: \"" + id + "\"");
		}
		return id;
	}
}
