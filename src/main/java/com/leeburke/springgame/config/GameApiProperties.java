package com.leeburke.springgame.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Public API settings ({@code game.api.*}). Invite codes come from {@code GAME_INVITE_CODES}
 * (comma-separated); with none configured, run creation is closed. CORS is off unless origins are
 * listed. Limits are kept in memory and reset on restart. Invite codes are never printed.
 *
 * @param lease how long a request may hold a turn (interpretation) or its narration before another
 *              request may take over
 */
@ConfigurationProperties("game.api")
public record GameApiProperties(
		@DefaultValue("") List<String> inviteCodes,
		@DefaultValue("") List<String> corsOrigins,
		@DefaultValue("2m") Duration lease,
		@DefaultValue Limits limits) {

	public GameApiProperties {
		inviteCodes = inviteCodes.stream().map(String::strip).filter(code -> !code.isEmpty()).toList();
		corsOrigins = corsOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
	}

	/**
	 * @param invalidInvitesPerHourPerAddress failed invite attempts per remote address per hour
	 * @param invalidInvitesPerHour           failed invite attempts per hour in total
	 * @param creationsPerHourPerInvite       new runs per invite code per hour
	 * @param creationsPerDay                 new runs per day in total
	 * @param turnsPerMinutePerRun            new turns per run per minute (replays are free)
	 */
	public record Limits(
			@DefaultValue("10") int invalidInvitesPerHourPerAddress,
			@DefaultValue("30") int invalidInvitesPerHour,
			@DefaultValue("5") int creationsPerHourPerInvite,
			@DefaultValue("20") int creationsPerDay,
			@DefaultValue("12") int turnsPerMinutePerRun) {
	}

	@Override
	public String toString() {
		return "GameApiProperties[inviteCodes=<" + inviteCodes.size() + " configured>, corsOrigins=" + corsOrigins
				+ ", lease=" + lease + ", limits=" + limits + "]";
	}
}
