package com.leeburke.springgame.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;
import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.FakeAiProvider;
import com.leeburke.springgame.ai.narration.CharacterIntroduction;
import com.leeburke.springgame.ai.narration.CharacterIntroductionNarrator;
import com.leeburke.springgame.ai.narration.LoreCatalog;
import com.leeburke.springgame.ai.narration.NarrationSource;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.character.PlayerStatGenerator;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.run.introduction.CharacterIntroductionService;

/** One introduction per run, generated outside any transaction, stored once and then reused. */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class CharacterIntroductionIntegrationTest {

	@Autowired
	private GameRunStore runs;

	@Autowired
	private IntroductionStore introductions;

	@Autowired
	private GameContentCatalog content;

	@Autowired
	private LoreCatalog lore;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID newRun(long seed) {
		return runs.createRun(seed, new PlayerCharacterGenerator(content, new PlayerStatGenerator())
				.generate(new SplittableRandom(seed))).id();
	}

	private CharacterIntroductionService service(FakeAiProvider provider) {
		provider.onCall(() -> assertThat(TransactionSynchronizationManager.isActualTransactionActive())
				.as("no transaction is open while the model is called").isFalse());
		return new CharacterIntroductionService(runs, introductions,
				new CharacterIntroductionNarrator(provider, "INTRO", 1, FakeAiProvider.settings()), lore);
	}

	@Test
	void flywayAppliedV5() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '5' AND success", Long.class))
				.isEqualTo(1);
	}

	@Test
	void introductionIsGeneratedOnceAndReloadedExactly() {
		UUID runId = newRun(301);
		FakeAiProvider provider = FakeAiProvider.answering("You remember a lantern guttering in the rain.");
		CharacterIntroductionService service = service(provider);

		CharacterIntroduction first = service.introductionFor(runId);
		CharacterIntroduction again = service.introductionFor(runId);

		assertThat(first).isEqualTo(new CharacterIntroduction("You remember a lantern guttering in the rain.", NarrationSource.AI, 1));
		assertThat(again).isEqualTo(first);
		assertThat(provider.calls()).isEqualTo(1);
		assertThat(jdbc.queryForMap("SELECT intro_text, source, prompt_version FROM character_introduction WHERE run_id = ?", runId))
				.containsEntry("source", "AI").containsEntry("prompt_version", 1);
	}

	@Test
	void fallbackIntroductionIsPersistedTheSameWay() {
		UUID runId = newRun(302);
		CharacterIntroduction stored = service(new FakeAiProvider().thenFail(AiFailureKind.DISABLED)).introductionFor(runId);

		assertThat(stored.source()).isEqualTo(NarrationSource.FALLBACK);
		assertThat(stored.text()).contains("Bound Soul");
		assertThat(introductions.find(runId)).contains(stored);
		assertThat(service(new FakeAiProvider()).introductionFor(runId)).isEqualTo(stored);
	}

	@Test
	void concurrentRequestsStoreOneIntroductionAndBothReturnIt() throws Exception {
		UUID runId = newRun(303);
		CountDownLatch bothGenerating = new CountDownLatch(2);
		FakeAiProvider first = FakeAiProvider.answering("First telling.");
		FakeAiProvider second = FakeAiProvider.answering("Second telling.");
		Runnable meet = () -> {
			bothGenerating.countDown();
			try {
				bothGenerating.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
		CharacterIntroductionService a = new CharacterIntroductionService(runs, introductions,
				new CharacterIntroductionNarrator(first.onCall(meet), "INTRO", 1, FakeAiProvider.settings()), lore);
		CharacterIntroductionService b = new CharacterIntroductionService(runs, introductions,
				new CharacterIntroductionNarrator(second.onCall(meet), "INTRO", 1, FakeAiProvider.settings()), lore);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<CharacterIntroduction> fa = pool.submit(() -> a.introductionFor(runId));
			Future<CharacterIntroduction> fb = pool.submit(() -> b.introductionFor(runId));
			CharacterIntroduction ra = fa.get(30, TimeUnit.SECONDS);
			CharacterIntroduction rb = fb.get(30, TimeUnit.SECONDS);
			assertThat(ra).isEqualTo(rb);
			assertThat(List.of("First telling.", "Second telling.")).contains(ra.text());
		} finally {
			pool.shutdownNow();
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM character_introduction WHERE run_id = ?", Long.class, runId))
				.isEqualTo(1);
		assertThat(introductions.find(runId).orElseThrow().text()).isIn("First telling.", "Second telling.");
	}

	@Test
	void unknownRunIsRejectedBeforeAnyModelCall() {
		FakeAiProvider provider = new FakeAiProvider();
		assertThatIllegalArgumentException().isThrownBy(() -> service(provider).introductionFor(UUID.randomUUID()));
		assertThat(provider.calls()).isZero();
	}

	@Test
	void schemaRejectsBlankTextAndUnknownSources() {
		UUID runId = newRun(304);
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO character_introduction (run_id, intro_text, source, prompt_version) VALUES (?, '  ', 'AI', 1)", runId))
				.isNotNull();
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO character_introduction (run_id, intro_text, source, prompt_version) VALUES (?, 'x', 'MODEL', 1)", runId))
				.isNotNull();
	}
}
