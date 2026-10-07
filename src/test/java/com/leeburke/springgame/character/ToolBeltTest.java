package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.content.ItemCategory;
import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.mechanics.DamageType;

class ToolBeltTest {

	private static final ToolBeltEntry SWORD =
			new ToolBeltEntry.Weapon(new WeaponDefinition("SWORD", "Sword", 6, 3, DamageType.SLASHING));
	private static final ToolBeltEntry BANDAGE =
			new ToolBeltEntry.Item(new ItemDefinition("BANDAGE", "Bandage", ItemCategory.RECOVERY));
	private static final ToolBeltEntry ROPE =
			new ToolBeltEntry.Item(new ItemDefinition("ROPE", "Rope", ItemCategory.UTILITY));

	@Test
	void capacityIsFive() {
		assertThat(ToolBelt.CAPACITY).isEqualTo(5);
	}

	@Test
	void threeEntriesLeaveTwoEmptySlots() {
		ToolBelt belt = new ToolBelt(List.of(SWORD, BANDAGE, ROPE));
		assertThat(belt.occupiedSlots()).isEqualTo(3);
		assertThat(belt.emptySlots()).isEqualTo(2);
	}

	@Test
	void fiveEntriesFillTheBelt() {
		assertThat(new ToolBelt(Collections.nCopies(5, ROPE)).emptySlots()).isZero();
	}

	@Test
	void rejectsMoreThanCapacity() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ToolBelt(Collections.nCopies(6, ROPE)));
	}

	@Test
	void rejectsNullEntryAndNullDefinition() {
		assertThatNullPointerException().isThrownBy(() -> new ToolBelt(Arrays.asList(SWORD, null)));
		assertThatNullPointerException().isThrownBy(() -> new ToolBeltEntry.Weapon(null));
		assertThatNullPointerException().isThrownBy(() -> new ToolBeltEntry.Item(null));
	}

	@Test
	void entriesAreUnmodifiableAndCopied() {
		List<ToolBeltEntry> source = new ArrayList<>(List.of(SWORD));
		ToolBelt belt = new ToolBelt(source);

		source.add(ROPE);

		assertThat(belt.entries()).containsExactly(SWORD);
		assertThatThrownBy(() -> belt.entries().add(ROPE)).isInstanceOf(UnsupportedOperationException.class);
	}
}
