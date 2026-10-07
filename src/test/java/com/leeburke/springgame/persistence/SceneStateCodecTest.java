package com.leeburke.springgame.persistence;

import static com.leeburke.springgame.world.WorldFixtures.minimalScene;
import static com.leeburke.springgame.world.WorldFixtures.richScene;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.shared.StrictJson;

/** The scene-state storage contract, without a database. */
class SceneStateCodecTest {

	private static final UUID SCENE = UUID.fromString("00000000-0000-0000-0000-00000000005c");

	private final SceneStateCodec codec = new SceneStateCodec();

	private static final String EMPTY_LISTS =
			"\"connections\":[],\"entities\":[],\"objects\":[],\"hazards\":[],\"exits\":[],\"activeEvents\":[],"
					+ "\"environmentFlags\":[],\"hiddenContent\":[],\"discoveredFacts\":[]";

	/** A minimal valid document with the given zones array text. */
	private static String document(String zonesJson) {
		return "{\"zones\":" + zonesJson + "," + EMPTY_LISTS + "}";
	}

	private void assertRejected(String json) {
		assertThatThrownBy(() -> codec.decode(SCENE, 1, json))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining(SCENE.toString());
	}

	@Test
	void richSceneRoundTrips() {
		assertThat(codec.decode(SCENE, 1, codec.encode(richScene()))).isEqualTo(richScene());
	}

	@Test
	void minimalSceneRoundTrips() {
		assertThat(codec.decode(SCENE, 1, codec.encode(minimalScene()))).isEqualTo(minimalScene());
	}

	@Test
	void minimalHandWrittenDocumentDecodes() {
		assertThat(codec.decode(SCENE, 1, document("[{\"id\":\"hearth\",\"displayName\":\"Hearth\"}]")))
				.isEqualTo(minimalScene());
	}

	@Test
	void documentHoldsOnlyDynamicStateFields() {
		Map<?, ?> top = StrictJson.createMapper().readValue(codec.encode(richScene()), Map.class);
		assertThat(top.keySet().stream().map(String::valueOf).toList()).containsExactlyInAnyOrder("zones", "connections", "entities", "objects", "hazards",
				"exits", "activeEvents", "environmentFlags", "hiddenContent", "discoveredFacts");
	}

	@Test
	void malformedJsonIsRejected() {
		assertRejected("{\"zones\": [");
	}

	@Test
	void wrongTopLevelShapeIsRejected() {
		assertRejected("[]");
		assertRejected("{\"zones\": 5," + EMPTY_LISTS + "}");
	}

	@Test
	void unknownPropertyIsRejected() {
		assertRejected("{\"zonez\":[]," + EMPTY_LISTS + "}");
		assertRejected(document("[{\"id\":\"hearth\",\"displayName\":\"Hearth\",\"tags\":[]}]"));
	}

	@Test
	void missingPropertyIsRejected() {
		assertRejected(document("[{\"id\":\"hearth\",\"displayName\":\"Hearth\"}]").replace(",\"hiddenContent\":[]", ""));
		assertRejected(document("[{\"id\":\"hearth\"}]"));
	}

	@Test
	void nullElementIsRejected() {
		assertRejected(document("[null]"));
	}

	@Test
	void invalidEnumValueIsRejected() {
		assertRejected(document("[{\"id\":\"hearth\",\"displayName\":\"Hearth\"}]")
				.replace("\"hiddenContent\":[]", "\"hiddenContent\":[{\"kind\":\"SECRET\",\"localId\":\"hearth\"}]"));
	}

	@Test
	void wrongValueTypeIsRejected() {
		assertRejected(document("[{\"id\":7,\"displayName\":\"Hearth\"}]"));
	}

	@Test
	void structurallyValidDocumentViolatingSceneInvariantsIsRejected() {
		assertRejected(document("[]"));
		assertRejected(document("[{\"id\":\"hearth\",\"displayName\":\"Hearth\"}]")
				.replace("\"connections\":[]", "\"connections\":[{\"id\":\"c\",\"zoneA\":\"hearth\",\"zoneB\":\"nowhere\"}]"));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 2 })
	void unsupportedSchemaVersionIsRejectedEvenForValidDocument(int version) {
		String valid = codec.encode(richScene());
		assertThatThrownBy(() -> codec.decode(SCENE, version, valid))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining(SCENE.toString())
				.hasMessageContaining("unsupported scene-state schema version " + version);
	}
}
