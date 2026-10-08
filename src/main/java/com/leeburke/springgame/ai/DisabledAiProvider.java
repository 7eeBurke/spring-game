package com.leeburke.springgame.ai;

import java.util.Objects;

/** Used when AI is off or not configured: every call fails immediately, so every role falls back. */
public final class DisabledAiProvider implements AiProvider {

	private final AiFailureKind reason;

	public DisabledAiProvider(AiFailureKind reason) {
		this.reason = Objects.requireNonNull(reason, "reason");
	}

	public AiFailureKind reason() {
		return reason;
	}

	@Override
	public AiResponse generateStructured(AiStructuredRequest request) {
		return AiResponse.Failure.of(reason);
	}

	@Override
	public AiResponse generateText(AiTextRequest request) {
		return AiResponse.Failure.of(reason);
	}
}
