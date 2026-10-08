package com.leeburke.springgame.ai;

/**
 * The only way game code reaches a language model. Implementations live at the infrastructure edge
 * (for example {@code ai.openai}); vendor request and response types never cross this interface.
 * Provider-side failures are returned as {@link AiResponse.Failure}, not thrown.
 */
public interface AiProvider {

	AiResponse generateStructured(AiStructuredRequest request);

	AiResponse generateText(AiTextRequest request);
}
