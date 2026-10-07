package com.leeburke.springgame.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** RegionInstance and PlayerLocation invariants. */
class RegionAndLocationTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	void validRegionInstance() {
		assertThat(new RegionInstance(ID, RUN, "HOLLOW_CHAPEL").definitionCode()).isEqualTo("HOLLOW_CHAPEL");
	}

	@Test
	void regionRejectsNullIds() {
		assertThatNullPointerException().isThrownBy(() -> new RegionInstance(null, RUN, "HOLLOW_CHAPEL"));
		assertThatNullPointerException().isThrownBy(() -> new RegionInstance(ID, null, "HOLLOW_CHAPEL"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "hollow_chapel", "Hollow Chapel", " ", "" })
	void regionRejectsBadCodes(String code) {
		assertThatIllegalArgumentException().isThrownBy(() -> new RegionInstance(ID, RUN, code));
	}

	@Test
	void regionRejectsNullCode() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RegionInstance(ID, RUN, null));
	}

	@Test
	void validPlayerLocation() {
		assertThat(new PlayerLocation(ID, "nave_entrance").zoneId()).isEqualTo("nave_entrance");
	}

	@Test
	void locationRejectsNullScene() {
		assertThatNullPointerException().isThrownBy(() -> new PlayerLocation(null, "nave_entrance"));
		assertThatNullPointerException().isThrownBy(() -> new PlayerLocation(ID, null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  ", " altar", "altar " })
	void locationRejectsBlankOrUntrimmedZone(String zone) {
		assertThatIllegalArgumentException().isThrownBy(() -> new PlayerLocation(ID, zone));
	}
}
