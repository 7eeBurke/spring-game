package com.leeburke.springgame.world;

import static com.leeburke.springgame.world.WorldFixtures.minimalScene;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SceneInstanceTest {

	private static final UUID SCENE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	private static final UUID REGION = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

	@Test
	void hubSceneHasNoRegionAndNoSeed() {
		SceneInstance hub = WorldFixtures.hubScene(SCENE, RUN, minimalScene());
		assertThat(hub.kind()).isEqualTo(SceneKind.HUB);
		assertThat(hub.regionInstanceId()).isEmpty();
		assertThat(hub.sceneSeed()).isEmpty();
		assertThat(ScenePlacement.Hub.class.getRecordComponents()).isEmpty();
	}

	@Test
	void regionSceneCarriesRegionAndSeed() {
		SceneInstance scene = WorldFixtures.regionScene(SCENE, RUN, REGION, -42L, minimalScene());
		assertThat(scene.kind()).isEqualTo(SceneKind.REGION);
		assertThat(scene.regionInstanceId()).contains(REGION);
		assertThat(scene.sceneSeed()).hasValue(-42L);
	}

	@Test
	void regionPlacementRequiresRegion() {
		assertThatNullPointerException().isThrownBy(() -> new ScenePlacement.Region(null, 1L));
	}

	@ParameterizedTest
	@ValueSource(longs = { 0, 5 })
	void acceptsNonNegativeRevision(long revision) {
		assertThat(new SceneInstance(SCENE, RUN, "CLOISTER", new ScenePlacement.Hub(), false, revision, minimalScene()).revision())
				.isEqualTo(revision);
	}

	@Test
	void rejectsNegativeRevision() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> new SceneInstance(SCENE, RUN, "CLOISTER", new ScenePlacement.Hub(), false, -1, minimalScene()));
	}

	@Test
	void rejectsNullsAndBadDefinitionCode() {
		ScenePlacement hub = new ScenePlacement.Hub();
		assertThatNullPointerException().isThrownBy(() -> new SceneInstance(null, RUN, "CLOISTER", hub, false, 0, minimalScene()));
		assertThatNullPointerException().isThrownBy(() -> new SceneInstance(SCENE, null, "CLOISTER", hub, false, 0, minimalScene()));
		assertThatNullPointerException().isThrownBy(() -> new SceneInstance(SCENE, RUN, "CLOISTER", null, false, 0, minimalScene()));
		assertThatNullPointerException().isThrownBy(() -> new SceneInstance(SCENE, RUN, "CLOISTER", hub, false, 0, null));
		assertThatIllegalArgumentException().isThrownBy(() -> new SceneInstance(SCENE, RUN, "the_last_lantern", hub, false, 0, minimalScene()));
	}
}
