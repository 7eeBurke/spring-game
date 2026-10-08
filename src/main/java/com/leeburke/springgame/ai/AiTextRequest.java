package com.leeburke.springgame.ai;

import java.util.Objects;

/**
 * A plain-text generation. {@code instructions} are the role's fixed prompt; {@code inputJson} is
 * the single user message, a JSON document in which any player text is only a string value.
 */
public record AiTextRequest(AiRole role, int promptVersion, String instructions, String inputJson,
		AiGenerationSettings settings) {

	public AiTextRequest {
		Objects.requireNonNull(role, "role");
		AiRequests.requireVersion(promptVersion);
		AiRequests.requireText(instructions, "instructions");
		AiRequests.requireText(inputJson, "inputJson");
		Objects.requireNonNull(settings, "settings");
	}
}
