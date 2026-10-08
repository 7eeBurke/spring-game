package com.leeburke.springgame.ai.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.FakeAiProvider;
import com.leeburke.springgame.ai.PromptLibrary;
import com.leeburke.springgame.ai.interpreter.InterpreterFixtures;
import com.leeburke.springgame.content.ContentLoadException;
import com.leeburke.springgame.mechanics.StatType;

class CharacterIntroductionNarrationTest {

	private static final LoreCatalog LORE = LoreCatalog.loadBundled();

	private static CharacterIntroductionContext context() {
		return CharacterIntroductionContext.from(InterpreterFixtures.player(), LORE);
	}

	@Test
	void bundledLoreIsTheApprovedPremise() {
		assertThat(LORE.premise()).containsExactly(
				"The world is sustained by a single fading supernatural flame.",
				"As the flame weakens, the dead rise.",
				"Time and reality have begun to distort.",
				"You are a Bound Soul.",
				"The Last Lantern is a small refuge on the road to the Hollow Chapel.");
		assertThatThrownBy(() -> LoreCatalog.load("content/no-such-lore.json")).isInstanceOf(ContentLoadException.class);
		assertThatIllegalArgumentException().isThrownBy(() -> new LoreCatalog(java.util.List.of(" ")));
	}

	@ParameterizedTest
	@CsvSource({ "0, ORDINARY", "1, TOUCHED", "2, TOUCHED", "3, UNUSUAL", "4, OMINOUS", "5, DEEPLY_FATED" })
	void fatedBands(int fated, FatedBand band) {
		assertThat(FatedBand.of(fated)).isEqualTo(band);
	}

	@Test
	void fatedOutsideItsRangeHasNoBand() {
		assertThatIllegalArgumentException().isThrownBy(() -> FatedBand.of(6));
		assertThatIllegalArgumentException().isThrownBy(() -> FatedBand.of(-1));
	}

	@Test
	void contextHoldsOnlyConfirmedFactsAndLore() {
		CharacterIntroductionContext context = context();
		assertThat(context.name()).isEqualTo("Wren");
		assertThat(context.stats()).containsEntry(StatType.MIGHT, 8).containsEntry(StatType.ARCANA, 3).hasSize(5);
		assertThat(context.fatedValue()).isEqualTo(3);
		assertThat(context.fatedBand()).isEqualTo(FatedBand.UNUSUAL);
		assertThat(context.weapons()).containsExactly("Longsword");
		assertThat(context.items()).containsExactly("Restorative Salve", "Crowbar");
		assertThat(context.ability()).isEqualTo("Stoneblood");
		assertThat(context.lore()).isEqualTo(LORE.premise());
		assertThat(Arrays.stream(CharacterIntroductionContext.class.getRecordComponents()).map(c -> c.getName()))
				.containsExactly("name", "stats", "fatedValue", "fatedBand", "weapons", "passive", "ability", "items", "lore");
	}

	@Test
	void promptStatesEveryConstraint() {
		String prompt = new PromptLibrary().instructions(AiRole.CHARACTER_INTRODUCTION);
		assertThat(prompt).contains("stats, numbers or any mechanical bonus", "any item, weapon, power or ability beyond those given",
				"named people the character must find", "quest, promise or obligation", "places that the character must later visit",
				"Do not contradict or extend the lore");
	}

	@Test
	void aiIntroductionIsUsedAsWritten() {
		FakeAiProvider provider = FakeAiProvider.answering("You remember salt wind and a door that would not open.");
		Narration narration = new CharacterIntroductionNarrator(provider, "INTRO", 1, FakeAiProvider.settings()).narrate(context());
		assertThat(narration).isEqualTo(new Narration("You remember salt wind and a door that would not open.", NarrationSource.AI, 1,
				Optional.empty()));
		String input = provider.textRequests().getFirst().inputJson();
		assertThat(input).contains("\"name\":\"Wren\"", "UNUSUAL", "Bound Soul");
		assertThat(input).doesNotContain("maxHp", "currentHp", "seed", "runId", "probab");
	}

	@Test
	void fallbackRestatesFactsWithoutInventingAny() {
		Narration narration = new CharacterIntroductionNarrator(new FakeAiProvider().thenFail(AiFailureKind.DISABLED), "INTRO", 1,
				FakeAiProvider.settings()).narrate(context());

		assertThat(narration.source()).isEqualTo(NarrationSource.FALLBACK);
		assertThat(narration.text()).startsWith("The world is sustained by a single fading supernatural flame.")
				.contains("Your name is Wren.", "Your greatest strength is Might; your weakness is Arcana.",
						"You carry Longsword, along with Restorative Salve and Crowbar.", "the ability Stoneblood",
						FatedBand.UNUSUAL.phrase())
				.doesNotContainPattern("[0-9]");
	}
}
