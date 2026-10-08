package com.leeburke.springgame.ai.interpreter;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend-only mapping from request-scoped aliases to Stage 10 references. Never shown to a model.
 * Scene aliases map to scene-local IDs; player-owned aliases map to themselves (the alias is the
 * opaque Stage 10 reference); attack aliases map to the backend incoming-attack reference.
 */
public final class AliasTable {

	private final Map<AliasKind, Map<String, String>> byKind;

	private AliasTable(Map<AliasKind, Map<String, String>> byKind) {
		this.byKind = byKind;
	}

	/**
	 * Resolves an alias that must be of {@code expected} kind.
	 *
	 * @throws AliasException with a message naming only the alias, if it is malformed, of another
	 *                        kind, or not in this request's context
	 */
	public String resolve(AliasKind expected, String alias) {
		Objects.requireNonNull(expected, "expected");
		Optional<AliasKind> actual = AliasKind.kindOf(alias);
		if (actual.isEmpty()) {
			throw new AliasException("'" + alias + "' is not a valid alias");
		}
		if (actual.get() != expected) {
			throw new AliasException("'" + alias + "' is a " + actual.get().prefix() + " alias, but a "
					+ expected.prefix() + " alias is required here");
		}
		String reference = byKind.get(expected).get(alias);
		if (reference == null) {
			throw new AliasException("'" + alias + "' is not in the context");
		}
		return reference;
	}

	/** The alias of a backend reference, if this request minted one. */
	public Optional<String> aliasOf(AliasKind kind, String reference) {
		return byKind.get(kind).entrySet().stream()
				.filter(entry -> entry.getValue().equals(reference))
				.map(Map.Entry::getKey)
				.findFirst();
	}

	public Map<String, String> aliases(AliasKind kind) {
		return Map.copyOf(byKind.get(kind));
	}

	/** Thrown when an alias cannot be resolved. The message contains only the alias itself. */
	public static final class AliasException extends RuntimeException {
		AliasException(String message) {
			super(message);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Mints aliases in call order within each kind: the first of a kind is {@code <kind>_1}. */
	public static final class Builder {

		private final Map<AliasKind, Map<String, String>> byKind = new EnumMap<>(AliasKind.class);

		private Builder() {
			for (AliasKind kind : AliasKind.values()) {
				byKind.put(kind, new LinkedHashMap<>());
			}
		}

		/** Mints the next alias of the kind for a backend reference, and returns it. */
		public String mint(AliasKind kind, String reference) {
			Objects.requireNonNull(reference, "reference");
			Map<String, String> aliases = byKind.get(kind);
			if (aliases.containsValue(reference)) {
				throw new IllegalArgumentException("Reference already has a " + kind.prefix() + " alias");
			}
			String alias = kind.alias(aliases.size() + 1);
			aliases.put(alias, reference);
			return alias;
		}

		public AliasTable build() {
			Map<AliasKind, Map<String, String>> copy = new EnumMap<>(AliasKind.class);
			byKind.forEach((kind, aliases) -> copy.put(kind, Map.copyOf(aliases)));
			return new AliasTable(copy);
		}
	}
}
