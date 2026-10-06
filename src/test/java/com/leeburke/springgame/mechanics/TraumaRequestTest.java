package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class TraumaRequestTest {

	@Test
	void rejectsNegativeWeaponTrauma() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new TraumaRequest(-1, ContactQuality.SOLID, 0, 0, 0, 0, 0));
	}

	@Test
	void rejectsNegativeTraumaProtection() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new TraumaRequest(3, ContactQuality.SOLID, 0, 0, 0, -1, 0));
	}

	@Test
	void rejectsNegativeDefensiveMitigation() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new TraumaRequest(3, ContactQuality.SOLID, 0, 0, 0, 0, -1));
	}

	@Test
	void acceptsZeroForNonNegativeTerms() {
		TraumaRequest request = new TraumaRequest(0, ContactQuality.SOLID, 0, 0, 0, 0, 0);
		assertThat(request.weaponTrauma()).isZero();
		assertThat(request.traumaProtection()).isZero();
		assertThat(request.defensiveMitigation()).isZero();
	}

	@Test
	void acceptsSignedModifierTerms() {
		TraumaRequest request = new TraumaRequest(3, ContactQuality.SOLID, -1, -2, -3, 0, 0);
		assertThat(request.existingInjuryModifier()).isEqualTo(-1);
		assertThat(request.anatomyInteractionModifier()).isEqualTo(-2);
		assertThat(request.attackFormModifier()).isEqualTo(-3);
	}

	@Test
	void rejectsNullContact() {
		assertThatNullPointerException().isThrownBy(() -> new TraumaRequest(3, null, 0, 0, 0, 0, 0));
	}
}
