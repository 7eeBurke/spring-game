package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class DamageRequestTest {

	@Test
	void rejectsNegativeBaseDamage() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new DamageRequest(-1, ContactQuality.SOLID, Effectiveness.NORMAL, 0));
	}

	@Test
	void rejectsNegativeProtection() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new DamageRequest(6, ContactQuality.SOLID, Effectiveness.NORMAL, -1));
	}

	@Test
	void acceptsZeroBaseDamageAndProtection() {
		DamageRequest request = new DamageRequest(0, ContactQuality.SOLID, Effectiveness.NORMAL, 0);
		assertThat(request.baseDamage()).isZero();
		assertThat(request.protection()).isZero();
	}

	@Test
	void rejectsNullEnums() {
		assertThatNullPointerException().isThrownBy(() -> new DamageRequest(6, null, Effectiveness.NORMAL, 0));
		assertThatNullPointerException().isThrownBy(() -> new DamageRequest(6, ContactQuality.SOLID, null, 0));
	}
}
