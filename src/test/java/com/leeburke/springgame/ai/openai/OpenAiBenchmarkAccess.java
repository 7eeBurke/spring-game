package com.leeburke.springgame.ai.openai;

import java.time.Duration;

import com.leeburke.springgame.ai.AiResponse;
import com.leeburke.springgame.ai.AiTextRequest;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

/**
 * Test-only access to the production OpenAI adapter's request building and response extraction,
 * so the narrator benchmark uses the same request shape and the same text extraction (refusals,
 * truncation and empty output are failures exactly as in the game).
 */
public final class OpenAiBenchmarkAccess {

	private OpenAiBenchmarkAccess() {
	}

	/** The production request for a text role (model, instructions, input, max output tokens, store=false). */
	public static ResponseCreateParams textParams(AiTextRequest request) {
		return OpenAiProvider.textParams(request);
	}

	/** The production extraction of a response into success (text and usage) or a failure kind. */
	public static AiResponse interpret(Response response, Duration latency) {
		return OpenAiProvider.interpret(response, latency);
	}
}
