package com.leeburke.springgame.ai;

final class AiRequests {

	private AiRequests() {
	}

	static void requireVersion(int promptVersion) {
		if (promptVersion < 1) {
			throw new IllegalArgumentException("Prompt version must be at least 1");
		}
	}

	static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}
}
