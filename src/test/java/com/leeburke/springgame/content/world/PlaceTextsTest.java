package com.leeburke.springgame.content.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.content.GameContentLoader;

/** The authored world is described, its names are physically sensible, and containers hold real items. */
class PlaceTextsTest {

	private final WorldContentCatalog world = WorldContentLoader.loadBundled();

	@Test
	void theBundledWorldIsFullyDescribed() {
		// The catalog validates completeness when texts are present; here we check they are.
		assertThat(world.texts()).isNotSameAs(PlaceTexts.NONE);
		assertThat(world.texts().scene("THE_LAST_LANTERN").orElseThrow().exits().get("chapel_road").getFirst()).contains("Hollow Chapel");
		world.archetypes().forEach(a -> assertThat(world.texts().scene(a.code())).as(a.code()).isPresent());
	}

	@Test
	void noPlaceIsNamedForABarrierThatIsNotThere() {
		// Nothing in the generated world locks or seals a place, so no name may say it does.
		world.archetypes().forEach(a -> a.zones().forEach(zone -> {
			String name = zone.displayName().toLowerCase(Locale.ROOT);
			assertThat(name).as(a.code() + " " + zone.id()).doesNotContain("locked", "sealed").doesNotEndWith(" door").doesNotEndWith(" gate");
		}));
	}

	@Test
	void containersHoldOnlyItemsThatExist() {
		var items = GameContentLoader.loadBundled();
		assertThat(world.containers().itemCodes()).isNotEmpty()
				.allSatisfy(code -> assertThat(items.findItem(code)).as(code).isPresent());
		assertThat(world.containers().firstFind()).isPresent();
	}
}
