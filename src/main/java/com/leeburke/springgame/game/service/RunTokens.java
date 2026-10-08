package com.leeburke.springgame.game.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Client-generated run tokens: 32 random bytes as unpadded base64url (exactly 43 characters),
 * created by the client before its first request. The server stores only the SHA-256 hash and never
 * issues, logs or rotates tokens.
 */
public final class RunTokens {

	private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{43}$");
	private static final String BEARER = "Bearer ";

	private RunTokens() {
	}

	/** The token from an {@code Authorization: Bearer} header, if present (format not checked). */
	public static Optional<String> fromHeader(String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			return Optional.empty();
		}
		return Optional.of(authorization.substring(BEARER.length()).strip());
	}

	public static boolean wellFormed(String token) {
		return token != null && FORMAT.matcher(token).matches();
	}

	public static String hash(String token) {
		return sha256Hex(token);
	}

	static String sha256Hex(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}
}
