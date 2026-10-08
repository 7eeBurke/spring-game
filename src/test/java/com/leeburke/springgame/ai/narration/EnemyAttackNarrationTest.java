package com.leeburke.springgame.ai.narration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.FakeAiProvider;
import com.leeburke.springgame.ai.interpreter.InterpreterFixtures;
import com.leeburke.springgame.content.WeaponDefinition;

class EnemyAttackNarrationTest {

	private static final WeaponDefinition DAGGER = InterpreterFixtures.CONTENT.findWeapon("DAGGER").orElseThrow();

	private static EnemyAttackNarrationContext context(IncomingAttack attack) {
		return EnemyAttackNarrationContext.of(attack, NarrationNames.of(InterpreterFixtures.view(), InterpreterFixtures.WORLD), DAGGER);
	}

	private static EnemyAttackNarrator narrator(FakeAiProvider provider) {
		return new EnemyAttackNarrator(provider, "ATTACK", 1, FakeAiProvider.settings());
	}

	@ParameterizedTest
	@CsvSource({ "THRUST, DIRECT_THRUST", "HORIZONTAL_SWING, HORIZONTAL_SWEEP", "LOW_SWEEP, LOW_SWEEP",
			"OVERHEAD_STRIKE, DESCENDING_STRIKE", "QUICK_SLASH, FAST_CUT", "HEAVY_SMASH, HEAVY_BLOW",
			"HOOK_AND_PULL, HOOKING_PULL", "PROJECTED_ATTACK, PROJECTED" })
	void eachTemplateHasItsCue(AttackTemplate template, AttackCue cue) {
		assertThat(AttackCue.of(template)).isEqualTo(cue);
	}

	@ParameterizedTest
	@EnumSource(AttackCue.class)
	void everyCuePhraseConveysItsOwnCue(AttackCue cue) {
		assertThat(cue.isConveyedBy(cue.phrase())).isTrue();
		assertThat(cue.keywords()).isNotEmpty();
	}

	@Test
	void everyTemplateIsCovered() {
		assertThat(Arrays.stream(AttackTemplate.values()).map(AttackCue::of).distinct()).hasSize(AttackTemplate.values().length);
	}

	@Test
	void contextHoldsOnlyTheChosenAttacksNamesAndCue() {
		EnemyAttackNarrationContext context = context(InterpreterFixtures.incoming());
		assertThat(context).isEqualTo(new EnemyAttackNarrationContext("Hollow Acolyte", "Dagger", AttackCue.DESCENDING_STRIKE,
				AttackCue.DESCENDING_STRIKE.phrase(), AttackCue.DESCENDING_STRIKE.keywords()));
		assertThat(Arrays.stream(EnemyAttackNarrationContext.class.getRecordComponents()).map(c -> c.getName()))
				.containsExactly("attackerName", "weaponName", "cue", "cuePhrase", "requiredKeywords");
	}

	@Test
	void unseenAttackerIsNotNamed() {
		IncomingAttack unseen = new IncomingAttack("a", "lurker_9", AttackTemplate.THRUST, 12, 3, 1,
				com.leeburke.springgame.mechanics.Effectiveness.NORMAL, 0, 0, Optional.empty());
		assertThat(context(unseen).attackerName()).isEqualTo("something unseen");
	}

	@Test
	void requestCarriesNoMechanics() {
		FakeAiProvider provider = FakeAiProvider.answering("The acolyte's blade comes down from above.");
		narrator(provider).narrate(context(InterpreterFixtures.incoming()));
		String input = provider.textRequests().getFirst().inputJson();
		assertThat(input).contains("Hollow Acolyte", "Dagger", "DESCENDING_STRIKE");
		assertThat(input).doesNotContain("difficulty", "13", "damage", "weight", "acolyte_1", InterpreterFixtures.BACKEND_ATTACK,
				"ACOLYTE_");
	}

	@Test
	void proseIsUsedAsWrittenAndTheCueTravelsSeparately() {
		IncomingAttack attack = InterpreterFixtures.incoming();
		EnemyAttackNarration narration = narrator(FakeAiProvider.answering("A rusted blade falls from above, aimed to split you."))
				.narrate(context(attack));
		assertThat(narration.prose()).isEqualTo(new Narration("A rusted blade falls from above, aimed to split you.",
				NarrationSource.AI, 1, Optional.empty()));
		assertThat(narration.cue()).isEqualTo(AttackCue.DESCENDING_STRIKE);
		assertThat(narration.cueText()).isEqualTo("Incoming: an overhead strike coming down from above.");
		assertThat(attack).isEqualTo(InterpreterFixtures.incoming());
	}

	@Test
	void differentWordingIsNotDiscarded() {
		EnemyAttackNarration narration = narrator(FakeAiProvider.answering("The acolyte lunges at you, snarling."))
				.narrate(context(InterpreterFixtures.incoming()));
		assertThat(narration.prose().source()).isEqualTo(NarrationSource.AI);
		assertThat(narration.prose().text()).isEqualTo("The acolyte lunges at you, snarling.");
		assertThat(narration.cueText()).isEqualTo("Incoming: an overhead strike coming down from above.");
	}

	@ParameterizedTest
	@ValueSource(strings = { "A blade falls from above for 4 damage.", "   " })
	void numbersOrBlankProseFallBackButKeepTheCue(String text) {
		EnemyAttackNarration narration = narrator(FakeAiProvider.answering(text)).narrate(context(InterpreterFixtures.incoming()));
		assertThat(narration.prose().source()).isEqualTo(NarrationSource.FALLBACK);
		assertThat(narration.prose().text()).isEqualTo(
				"The Hollow Acolyte comes at you with its Dagger: an overhead strike coming down from above.");
		assertThat(narration.cueText()).isEqualTo("Incoming: an overhead strike coming down from above.");
	}

	@Test
	void overlongProseFallsBack() {
		EnemyAttackNarration narration = narrator(FakeAiProvider.answering("a".repeat(EnemyAttackNarrator.MAX_LENGTH + 1)))
				.narrate(context(InterpreterFixtures.incoming()));
		assertThat(narration.prose().source()).isEqualTo(NarrationSource.FALLBACK);
	}

	@Test
	void providerFailureFallsBackWithTheCue() {
		EnemyAttackNarration narration = narrator(new FakeAiProvider().thenFail(AiFailureKind.NETWORK))
				.narrate(context(InterpreterFixtures.incoming()));
		assertThat(narration.prose().fallbackReason()).contains(AiFailureKind.NETWORK);
		assertThat(AttackCue.DESCENDING_STRIKE.isConveyedBy(narration.prose().text())).isTrue();
		assertThat(narration.cueText()).isEqualTo("Incoming: an overhead strike coming down from above.");
	}

	@ParameterizedTest
	@EnumSource(AttackTemplate.class)
	void cueTextIsAlwaysPresentForEveryTemplate(AttackTemplate template) {
		IncomingAttack attack = new IncomingAttack("a", "acolyte_1", template, 12, 3, 1,
				com.leeburke.springgame.mechanics.Effectiveness.NORMAL, 0, 0, Optional.empty());
		AttackCue cue = AttackCue.of(template);
		EnemyAttackNarration ai = narrator(FakeAiProvider.answering("It comes for you.")).narrate(context(attack));
		EnemyAttackNarration fallback = narrator(new FakeAiProvider().thenFail(AiFailureKind.DISABLED)).narrate(context(attack));
		for (EnemyAttackNarration narration : java.util.List.of(ai, fallback)) {
			assertThat(narration.cue()).isEqualTo(cue);
			assertThat(narration.cueText()).isEqualTo("Incoming: " + cue.phrase() + ".");
			assertThat(cue.isConveyedBy(narration.cueText())).isTrue();
		}
	}

	@Test
	void cueTextCannotBeReplaced() {
		Narration prose = new Narration("x", NarrationSource.AI, 1, Optional.empty());
		org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(
				() -> new EnemyAttackNarration(prose, AttackCue.LOW_SWEEP, "Incoming: something else."));
	}

	@ParameterizedTest
	@EnumSource(AttackTemplate.class)
	void fallbackAlwaysConveysTheCue(AttackTemplate template) {
		IncomingAttack attack = new IncomingAttack("a", "acolyte_1", template, 12, 3, 1,
				com.leeburke.springgame.mechanics.Effectiveness.NORMAL, 0, 0, Optional.empty());
		EnemyAttackNarrationContext context = context(attack);
		assertThat(context.cue().isConveyedBy(EnemyAttackNarrator.fallback(context))).isTrue();
	}
}
