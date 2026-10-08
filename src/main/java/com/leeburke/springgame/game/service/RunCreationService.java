package com.leeburke.springgame.game.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.leeburke.springgame.character.GeneratedCharacter;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.config.GameApiProperties;
import com.leeburke.springgame.game.RunStatus;
import com.leeburke.springgame.game.TurnRandom;
import com.leeburke.springgame.game.view.CreateRunResponse;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.RunSessionStore;
import com.leeburke.springgame.persistence.RunSessionStore.Credentials;
import com.leeburke.springgame.persistence.WorldAlreadyInitializedException;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.run.GameRun;
import com.leeburke.springgame.run.initialization.RunInitializer;
import com.leeburke.springgame.run.introduction.CharacterIntroductionService;
import com.leeburke.springgame.world.generation.GenerationContextSnapshot;

/**
 * Creates a run, idempotently and resumably. The client generates the run token and a creation key
 * before its first request; the server stores only the token's hash and never issues or rotates a
 * token. A retry with the same key and token resumes from the first missing step; the same key with
 * a different token is refused, so a creation key can never re-credential a run.
 * <ol>
 * <li>Transaction A: a {@link SecureRandom} run seed, the character (seeded from it), the run and
 * an INITIALIZING session.</li>
 * <li>Transaction B: the whole world and its enemies, generated in Java and persisted atomically.</li>
 * <li>No transaction: the character introduction (possibly a model call), stored once.</li>
 * <li>Transaction C: INITIALIZING to ACTIVE.</li>
 * </ol>
 */
@Service
public class RunCreationService {

	private final List<byte[]> inviteCodes;
	private final AbuseLimits limits;
	private final RunSessionStore sessions;
	private final GameRunStore runs;
	private final WorldStore world;
	private final PlayerCharacterGenerator characters;
	private final RunInitializer initializer;
	private final CharacterIntroductionService introductions;
	private final GameViewAssembler views;
	private final TransactionTemplate transactions;
	private final SecureRandom seeds;
	private final Clock clock;

	RunCreationService(GameApiProperties properties, AbuseLimits limits, RunSessionStore sessions, GameRunStore runs,
			WorldStore world, PlayerCharacterGenerator characters, RunInitializer initializer,
			CharacterIntroductionService introductions, GameViewAssembler views, PlatformTransactionManager transactionManager,
			SecureRandom seeds, Clock clock) {
		this.inviteCodes = properties.inviteCodes().stream().map(code -> code.getBytes(StandardCharsets.UTF_8)).toList();
		this.limits = Objects.requireNonNull(limits, "limits");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.runs = Objects.requireNonNull(runs, "runs");
		this.world = Objects.requireNonNull(world, "world");
		this.characters = Objects.requireNonNull(characters, "characters");
		this.initializer = Objects.requireNonNull(initializer, "initializer");
		this.introductions = Objects.requireNonNull(introductions, "introductions");
		this.views = Objects.requireNonNull(views, "views");
		this.transactions = new TransactionTemplate(transactionManager);
		this.seeds = Objects.requireNonNull(seeds, "seeds");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * @param remoteAddress the socket address only; forwarding headers are never used
	 */
	public CreateRunResponse create(String inviteCode, String creationKeyHeader, String authorizationHeader, String remoteAddress) {
		limits.checkInviteAttempts(remoteAddress);
		if (!validInvite(inviteCode)) {
			limits.recordInvalidInvite(remoteAddress);
			throw new GameException(ErrorCode.UNAUTHORIZED, "The invite code was not accepted.");
		}
		String token = RunTokens.fromHeader(authorizationHeader).filter(RunTokens::wellFormed)
				.orElseThrow(() -> new GameException(ErrorCode.INVALID_REQUEST,
						"A client-generated run token is required: Authorization: Bearer <43 base64url characters>."));
		UUID creationKey = parseKey(creationKeyHeader);
		String tokenHash = RunTokens.hash(token);

		UUID runId = existing(creationKey, tokenHash).orElseGet(() -> start(inviteCode, creationKey, tokenHash));
		finishCreation(runId);
		return new CreateRunResponse(runId, views.view(runId));
	}

	private Optional<UUID> existing(UUID creationKey, String tokenHash) {
		Optional<Credentials> credentials = sessions.findByCreationKey(creationKey);
		if (credentials.isPresent() && !credentials.get().accessTokenHash().equals(tokenHash)) {
			throw new GameException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "This creation key belongs to another run token.");
		}
		return credentials.map(Credentials::runId);
	}

	private UUID start(String inviteCode, UUID creationKey, String tokenHash) {
		if (sessions.findRunByTokenHash(tokenHash).isPresent()) {
			throw new GameException(ErrorCode.TOKEN_IN_USE, "This run token is already in use; generate a new one.");
		}
		limits.acquireCreation(inviteCode);
		try {
			return transactions.execute(status -> {
				long runSeed = seeds.nextLong();
				GeneratedCharacter character = characters.generate(TurnRandom.character(runSeed));
				GameRun run = runs.createRun(runSeed, character);
				sessions.create(run.id(), tokenHash, creationKey, clock.instant());
				return run.id();
			});
		} catch (DataIntegrityViolationException race) {
			// A concurrent request with the same key or token committed first.
			return existing(creationKey, tokenHash).orElseThrow(() ->
					new GameException(ErrorCode.TOKEN_IN_USE, "This run token is already in use; generate a new one."));
		}
	}

	/** Resumes from the first missing step; every step is safe to repeat. */
	private void finishCreation(UUID runId) {
		if (sessions.find(runId).orElseThrow().status() != RunStatus.INITIALIZING) {
			return;
		}
		if (world.findPlayerLocation(runId).isEmpty()) {
			GameRun run = runs.findRun(runId).orElseThrow();
			try {
				// The generation context of recent openings across runs is not tracked yet: empty.
				world.initializeWorld(initializer.initialize(runId, run.runSeed(), GenerationContextSnapshot.empty()));
			} catch (WorldAlreadyInitializedException alreadyDone) {
				// A concurrent retry initialised it first.
			}
		}
		introductions.introductionFor(runId);
		sessions.activate(runId);
	}

	private boolean validInvite(String inviteCode) {
		if (inviteCode == null || inviteCode.isBlank()) {
			return false;
		}
		byte[] candidate = inviteCode.strip().getBytes(StandardCharsets.UTF_8);
		boolean match = false;
		for (byte[] code : inviteCodes) {
			match |= MessageDigest.isEqual(code, candidate);
		}
		return match;
	}

	private static UUID parseKey(String header) {
		try {
			return UUID.fromString(Objects.requireNonNull(header));
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new GameException(ErrorCode.INVALID_REQUEST, "An Idempotency-Key header with a UUID is required.");
		}
	}
}
