package com.leeburke.springgame.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Telling "where can I go?" from looking around or looking at one thing. */
class ExplorationQuestionTest {

	@ParameterizedTest
	@ValueSource(strings = { "I look for a way I haven't gone", "I look for a way I havent gone", "Where can I go from here?",
			"I search for another passage.", "Is there a way deeper into the chapel?", "I look around for where to go next",
			"Is there another way out?", "/search" })
	void questionsAboutWhereToGoAreExplorationQuestions(String input) {
		assertThat(ExplorationQuestion.asks(input)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "I look around", "I examine the crate", "I take the vial and examine it", "I attack the acolyte",
			"/inspect object_1", "/hold", "" })
	void otherWordsAreNot(String input) {
		assertThat(ExplorationQuestion.asks(input)).isFalse();
	}
}
