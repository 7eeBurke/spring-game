package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;

class PlayerBodyTest {

	private static Map<BodyPart, BodySeverity> allHealthy() {
		Map<BodyPart, BodySeverity> map = new EnumMap<>(BodyPart.class);
		for (BodyPart part : BodyPart.values()) {
			map.put(part, BodySeverity.HEALTHY);
		}
		return map;
	}

	@Test
	void healthyHasEveryBodyPartHealthy() {
		PlayerBody body = PlayerBody.healthy();
		assertThat(body.severities()).hasSize(BodyPart.values().length);
		for (BodyPart part : BodyPart.values()) {
			assertThat(body.severity(part)).isEqualTo(BodySeverity.HEALTHY);
		}
	}

	@Test
	void rejectsMissingBodyPart() {
		Map<BodyPart, BodySeverity> map = allHealthy();
		map.remove(BodyPart.HEART);
		assertThatIllegalArgumentException().isThrownBy(() -> new PlayerBody(map)).withMessageContaining("HEART");
	}

	@Test
	void rejectsNullSeverityAndNullMap() {
		Map<BodyPart, BodySeverity> map = new HashMap<>(allHealthy());
		map.put(BodyPart.NECK, null);
		assertThatNullPointerException().isThrownBy(() -> new PlayerBody(map));
		assertThatNullPointerException().isThrownBy(() -> new PlayerBody(null));
	}

	@Test
	void canRepresentNonHealthyState() {
		Map<BodyPart, BodySeverity> map = allHealthy();
		map.put(BodyPart.LEFT_ARM, BodySeverity.WOUNDED);
		assertThat(new PlayerBody(map).severity(BodyPart.LEFT_ARM)).isEqualTo(BodySeverity.WOUNDED);
	}

	@Test
	void callerMapChangesDoNotAffectBody() {
		Map<BodyPart, BodySeverity> map = allHealthy();
		PlayerBody body = new PlayerBody(map);

		map.put(BodyPart.HEAD, BodySeverity.DESTROYED);

		assertThat(body.severity(BodyPart.HEAD)).isEqualTo(BodySeverity.HEALTHY);
	}

	@Test
	void severitiesAreUnmodifiable() {
		PlayerBody body = PlayerBody.healthy();
		assertThatThrownBy(() -> body.severities().put(BodyPart.HEAD, BodySeverity.INJURED))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void equalSeveritiesAreEqual() {
		assertThat(new PlayerBody(allHealthy())).isEqualTo(PlayerBody.healthy());
	}
}
