package com.leeburke.springgame.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.world.generation.GenerationContextSnapshot;

class GenerationContextCodecTest {

	private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-0000000000c0");

	private final GenerationContextCodec codec = new GenerationContextCodec();

	private void assertRejected(String json) {
		assertThatThrownBy(() -> codec.decode(RUN, 1, json))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining(RUN.toString());
	}

	@Test
	void roundTrips() {
		GenerationContextSnapshot snapshot = new GenerationContextSnapshot(List.of("CLOISTER", "OSSUARY", "CLOISTER"));
		assertThat(codec.decode(RUN, 1, codec.encode(snapshot))).isEqualTo(snapshot);
	}

	@Test
	void emptySnapshotRoundTrips() {
		assertThat(codec.decode(RUN, 1, codec.encode(GenerationContextSnapshot.empty()))).isEqualTo(GenerationContextSnapshot.empty());
	}

	@Test
	void corruptDocumentsAreRejected() {
		assertRejected("{\"recentOpeningArchetypeCodes\": [");
		assertRejected("{\"recentOpeningArchetypeCodes\": [], \"playerHistory\": []}");
		assertRejected("{}");
		assertRejected("{\"recentOpeningArchetypeCodes\": [\"A_ROOM\", \"B_ROOM\", \"C_ROOM\", \"D_ROOM\"]}");
		assertRejected("{\"recentOpeningArchetypeCodes\": [\"cloister\"]}");
		assertRejected("{\"recentOpeningArchetypeCodes\": [7]}");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 2 })
	void unsupportedVersionsAreRejected(int version) {
		String valid = codec.encode(GenerationContextSnapshot.empty());
		assertThatThrownBy(() -> codec.decode(RUN, version, valid))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("unsupported generation-context schema version " + version);
	}
}
