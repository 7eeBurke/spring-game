package com.leeburke.springgame.ai;

import java.util.Objects;

/**
 * A generation constrained to a JSON schema (provider-side structured output). Schema enforcement
 * by the provider never replaces our own strict parsing and validation.
 */
public record AiStructuredRequest(AiRole role, int promptVersion, String instructions, String inputJson,
		String schemaName, String schemaJson, AiGenerationSettings settings) {

	public AiStructuredRequest {
		Objects.requireNonNull(role, "role");
		AiRequests.requireVersion(promptVersion);
		AiRequests.requireText(instructions, "instructions");
		AiRequests.requireText(inputJson, "inputJson");
		AiRequests.requireText(schemaName, "schemaName");
		AiRequests.requireText(schemaJson, "schemaJson");
		Objects.requireNonNull(settings, "settings");
	}
}
