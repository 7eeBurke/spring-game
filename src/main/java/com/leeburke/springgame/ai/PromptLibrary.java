package com.leeburke.springgame.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * The role prompts: versioned classpath resources {@code ai/prompts/<role>-v<version>.txt}. They are
 * application configuration, not game content, and are loaded once. A missing prompt is a
 * packaging error and fails immediately.
 */
public final class PromptLibrary {

	public static final String DIRECTORY = "ai/prompts";

	/** Current prompt version of each role. Bumped when a prompt's meaning changes. */
	public static final Map<AiRole, Integer> CURRENT_VERSIONS = Map.of(
			AiRole.ACTION_INTERPRETER, 2,
			AiRole.OUTCOME_NARRATOR, 2,
			AiRole.ENEMY_ATTACK_NARRATOR, 1,
			AiRole.CHARACTER_INTRODUCTION, 1);

	private final Map<AiRole, String> prompts = new EnumMap<>(AiRole.class);

	public PromptLibrary() {
		for (AiRole role : AiRole.values()) {
			prompts.put(role, load(role, version(role)));
		}
	}

	public String instructions(AiRole role) {
		return prompts.get(role);
	}

	public int version(AiRole role) {
		return CURRENT_VERSIONS.get(role);
	}

	static String path(AiRole role, int version) {
		return DIRECTORY + "/" + role.resourceName() + "-v" + version + ".txt";
	}

	private static String load(AiRole role, int version) {
		String path = path(role, version);
		try (InputStream in = PromptLibrary.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing prompt resource " + path);
			}
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
			if (text.isEmpty()) {
				throw new IllegalStateException("Empty prompt resource " + path);
			}
			return text;
		} catch (IOException e) {
			throw new IllegalStateException("Could not read prompt resource " + path, e);
		}
	}
}
