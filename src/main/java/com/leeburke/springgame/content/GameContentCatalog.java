package com.leeburke.springgame.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Immutable set of all loaded static definitions, indexed by definition code, plus the authored
 * character-name pool.
 * <p>
 * Codes must be unique within each content type; different types may share a code. Authored order
 * is preserved so iteration is deterministic and easy to inspect. There are no required counts.
 * Caller collections are copied, never retained.
 */
public final class GameContentCatalog {

	private final Map<String, WeaponDefinition> weapons;
	private final Map<String, PassiveDefinition> passives;
	private final Map<String, AbilityDefinition> abilities;
	private final Map<String, ItemDefinition> items;
	private final List<String> characterNames;

	public GameContentCatalog(
			Collection<WeaponDefinition> weapons,
			Collection<PassiveDefinition> passives,
			Collection<AbilityDefinition> abilities,
			Collection<ItemDefinition> items,
			Collection<String> characterNames) {
		this.weapons = index("weapon", weapons, WeaponDefinition::code);
		this.passives = index("passive", passives, PassiveDefinition::code);
		this.abilities = index("ability", abilities, AbilityDefinition::code);
		this.items = index("item", items, ItemDefinition::code);
		this.characterNames = validateNames(characterNames);
	}

	/** Authored character names, in authored order. */
	public List<String> characterNames() {
		return characterNames;
	}

	public List<WeaponDefinition> weapons() {
		return List.copyOf(weapons.values());
	}

	public Optional<WeaponDefinition> findWeapon(String code) {
		return find(weapons, code);
	}

	public List<PassiveDefinition> passives() {
		return List.copyOf(passives.values());
	}

	public Optional<PassiveDefinition> findPassive(String code) {
		return find(passives, code);
	}

	public List<AbilityDefinition> abilities() {
		return List.copyOf(abilities.values());
	}

	public Optional<AbilityDefinition> findAbility(String code) {
		return find(abilities, code);
	}

	public List<ItemDefinition> items() {
		return List.copyOf(items.values());
	}

	public Optional<ItemDefinition> findItem(String code) {
		return find(items, code);
	}

	private static <T> Optional<T> find(Map<String, T> index, String code) {
		Objects.requireNonNull(code, "code");
		return Optional.ofNullable(index.get(code));
	}

	/**
	 * Names must be non-blank and already trimmed, and unique ignoring case, so an authoring slip
	 * cannot silently add a duplicate weighted choice. Names are stored exactly as authored.
	 */
	private static List<String> validateNames(Collection<String> names) {
		Objects.requireNonNull(names, "character names");
		List<String> validated = new ArrayList<>(names.size());
		Set<String> seen = new HashSet<>();
		for (String name : names) {
			Objects.requireNonNull(name, "null character name");
			if (name.isBlank()) {
				throw new IllegalArgumentException("Character name must not be blank");
			}
			if (!name.equals(name.strip())) {
				throw new IllegalArgumentException("Character name must not have leading or trailing whitespace: \"" + name + "\"");
			}
			if (!seen.add(name.toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException("Duplicate character name (ignoring case): " + name);
			}
			validated.add(name);
		}
		return List.copyOf(validated);
	}

	private static <T> Map<String, T> index(String contentType, Collection<T> definitions, Function<T, String> code) {
		Objects.requireNonNull(definitions, contentType + " definitions");
		Map<String, T> index = new LinkedHashMap<>();
		for (T definition : definitions) {
			Objects.requireNonNull(definition, "null " + contentType + " definition");
			if (index.putIfAbsent(code.apply(definition), definition) != null) {
				throw new IllegalArgumentException("Duplicate " + contentType + " code: " + code.apply(definition));
			}
		}
		return Collections.unmodifiableMap(index);
	}
}
