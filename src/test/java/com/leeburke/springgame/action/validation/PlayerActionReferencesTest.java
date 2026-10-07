package com.leeburke.springgame.action.validation;

import static com.leeburke.springgame.action.ActionFixtures.CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.content.ItemDefinition;

class PlayerActionReferencesTest {

	private static final ItemDefinition BANDAGE = CONTENT.findItem("BANDAGE").orElseThrow();

	@Test
	void twoReferencesMayNameTheSameDefinition() {
		PlayerActionReferences refs = new PlayerActionReferences(Map.of(), Map.of(), Map.of("ref_1", BANDAGE, "ref_2", BANDAGE));
		assertThat(refs.items()).containsEntry("ref_1", BANDAGE).containsEntry("ref_2", BANDAGE);
	}

	@Test
	void mapsAreCopiedAndUnmodifiable() {
		Map<String, ItemDefinition> source = new HashMap<>(Map.of("ref_1", BANDAGE));
		PlayerActionReferences refs = new PlayerActionReferences(Map.of(), Map.of(), source);
		source.put("ref_2", BANDAGE);
		assertThat(refs.items()).containsOnlyKeys("ref_1");
		assertThatThrownBy(() -> refs.items().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void keysMustBeClean() {
		assertThatIllegalArgumentException().isThrownBy(() -> new PlayerActionReferences(Map.of(), Map.of(), Map.of(" ref", BANDAGE)));
		assertThatIllegalArgumentException().isThrownBy(() -> new PlayerActionReferences(Map.of(), Map.of(), Map.of("", BANDAGE)));
	}

	@Test
	void noneIsEmpty() {
		assertThat(PlayerActionReferences.none().weapons()).isEmpty();
	}
}
