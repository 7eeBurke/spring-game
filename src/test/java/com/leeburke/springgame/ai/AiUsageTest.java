package com.leeburke.springgame.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class AiUsageTest {

	@Test
	void usageAddsUpAndKeepsOptionalCountsWhenEitherHasThem() {
		AiUsage first = new AiUsage(100, 20, Optional.of(40L), Optional.empty());
		AiUsage second = new AiUsage(50, 10, Optional.empty(), Optional.of(7L));
		assertThat(first.plus(second)).isEqualTo(new AiUsage(150, 30, Optional.of(40L), Optional.of(7L)));
		assertThat(AiUsage.combine(Optional.empty(), Optional.of(second))).contains(second);
		assertThat(AiUsage.combine(Optional.of(first), Optional.empty())).contains(first);
		assertThat(AiUsage.combine(Optional.empty(), Optional.empty())).isEmpty();
	}

	@Test
	void countsCannotBeNegative() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AiUsage(-1, 0, Optional.empty(), Optional.empty()));
		assertThatIllegalArgumentException().isThrownBy(() -> new AiUsage(0, 0, Optional.of(-1L), Optional.empty()));
	}

	@Test
	void usageCarriesNoContent() {
		assertThat(java.util.Arrays.stream(AiUsage.class.getRecordComponents()).map(c -> c.getName()))
				.containsExactly("inputTokens", "outputTokens", "cachedInputTokens", "reasoningTokens");
	}
}
