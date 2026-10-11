package com.leeburke.springgame.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Offline check of how the opt-in narrator probe judges an unconfirmed arrival at the chapel. */
class UnconfirmedArrivalTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"You reach the Chapel Road. Ahead, the road runs on toward the Hollow Chapel.",
			"The Hollow Chapel waits at the road's end.",
			"You enter the Chapel Road.",
			"You have not yet reached the Hollow Chapel.",
			"The road leads to the Hollow Chapel.",
			"Before you can enter the chapel, the road stretches on.",
			"You will reach the Hollow Chapel if you keep walking." })
	void namingTheChapelAsSomewhereAheadIsNotAClaim(String narration) {
		assertThat(UnconfirmedArrival.claimsArrival(narration, "chapel")).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"You enter the Hollow Chapel.",
			"You step inside the chapel.",
			"At last you arrive at the Hollow Chapel.",
			"You have reached the chapel.",
			"You stand within the Hollow Chapel.",
			"You walk the Chapel Road and, at its end, enter the chapel." })
	void sayingThePlayerGotThereIsAClaim(String narration) {
		assertThat(UnconfirmedArrival.claimsArrival(narration, "chapel")).isTrue();
	}
}
