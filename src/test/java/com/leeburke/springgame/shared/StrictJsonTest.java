package com.leeburke.springgame.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class StrictJsonTest {

	record Sample(String name, int count) {
	}

	@Test
	void eachCallBuildsAnIndependentMapper() {
		assertThat(StrictJson.createMapper()).isNotSameAs(StrictJson.createMapper());
	}

	@Test
	void everyMapperIsStrict() {
		for (JsonMapper mapper : new JsonMapper[] { StrictJson.createMapper(), StrictJson.createMapper() }) {
			assertThat(mapper.readValue("{\"name\":\"a\",\"count\":1}", Sample.class)).isEqualTo(new Sample("a", 1));
			assertThatThrownBy(() -> mapper.readValue("{\"name\":\"a\",\"count\":1,\"extra\":2}", Sample.class))
					.isInstanceOf(JacksonException.class);
			assertThatThrownBy(() -> mapper.readValue("{\"name\":\"a\",\"count\":\"6\"}", Sample.class))
					.isInstanceOf(JacksonException.class);
		}
	}
}
