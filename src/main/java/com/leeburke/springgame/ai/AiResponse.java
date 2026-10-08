package com.leeburke.springgame.ai;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** What a provider returned: usable text, or a classified failure. Providers never throw for provider-side failures. */
public sealed interface AiResponse {

	/** @param usage token usage, when the provider reported it */
	record Success(String text, Duration latency, Optional<AiUsage> usage) implements AiResponse {
		public Success {
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(latency, "latency");
			Objects.requireNonNull(usage, "usage");
		}
	}

	/** @param safeDetail a short diagnostic (for example an HTTP status), never a key, header or body */
	record Failure(AiFailureKind kind, String safeDetail) implements AiResponse {
		public Failure {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(safeDetail, "safeDetail");
		}

		public static Failure of(AiFailureKind kind) {
			return new Failure(kind, kind.name());
		}
	}
}
