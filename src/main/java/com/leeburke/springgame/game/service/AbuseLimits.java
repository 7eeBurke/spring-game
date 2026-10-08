package com.leeburke.springgame.game.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.leeburke.springgame.config.GameApiProperties;

/**
 * In-memory abuse limits for the public API. Client identity is only the socket's remote address:
 * forwarding headers such as {@code X-Forwarded-For} are never trusted, so behind a local proxy
 * (Tailscale Funnel) every client shares one address and the global limits are the real
 * protection. Replayed requests are never counted. Counters reset on restart.
 */
@Component
public class AbuseLimits {

	private static final String ALL = "*";

	private final SlidingWindowLimiter invalidInvitesPerAddress;
	private final SlidingWindowLimiter invalidInvites;
	private final SlidingWindowLimiter creationsPerInvite;
	private final SlidingWindowLimiter creations;
	private final SlidingWindowLimiter turnsPerRun;

	public AbuseLimits(GameApiProperties properties, Clock clock) {
		GameApiProperties.Limits limits = Objects.requireNonNull(properties, "properties").limits();
		invalidInvitesPerAddress = new SlidingWindowLimiter(limits.invalidInvitesPerHourPerAddress(), Duration.ofHours(1), clock);
		invalidInvites = new SlidingWindowLimiter(limits.invalidInvitesPerHour(), Duration.ofHours(1), clock);
		creationsPerInvite = new SlidingWindowLimiter(limits.creationsPerHourPerInvite(), Duration.ofHours(1), clock);
		creations = new SlidingWindowLimiter(limits.creationsPerDay(), Duration.ofDays(1), clock);
		turnsPerRun = new SlidingWindowLimiter(limits.turnsPerMinutePerRun(), Duration.ofMinutes(1), clock);
	}

	/** Refuses run creation outright while too many invalid invite codes have been tried. */
	public void checkInviteAttempts(String remoteAddress) {
		if (!invalidInvitesPerAddress.allows(remoteAddress) || !invalidInvites.allows(ALL)) {
			throw limited();
		}
	}

	public void recordInvalidInvite(String remoteAddress) {
		invalidInvitesPerAddress.record(remoteAddress);
		invalidInvites.record(ALL);
	}

	/** Counts a new run (not a resumed one) against its invite code and the global daily limit. */
	public synchronized void acquireCreation(String inviteCode) {
		if (!creationsPerInvite.allows(inviteCode) || !creations.allows(ALL)) {
			throw limited();
		}
		creationsPerInvite.record(inviteCode);
		creations.record(ALL);
	}

	/** Counts new turn work for a run. */
	public void acquireTurn(UUID runId) {
		if (!turnsPerRun.tryAcquire(runId.toString())) {
			throw limited();
		}
	}

	private static GameException limited() {
		return new GameException(ErrorCode.RATE_LIMITED, "Too many requests; wait a little and try again.");
	}
}
