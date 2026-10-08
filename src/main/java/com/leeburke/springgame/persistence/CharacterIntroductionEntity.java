package com.leeburke.springgame.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.leeburke.springgame.ai.narration.NarrationSource;

/** JPA mapping of {@code character_introduction}. Rows are inserted by native SQL and only read here. */
@Entity
@Table(name = "character_introduction")
public class CharacterIntroductionEntity {

	@Id
	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "intro_text", nullable = false)
	private String introText;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false)
	private NarrationSource source;

	@Column(name = "prompt_version", nullable = false)
	private int promptVersion;

	protected CharacterIntroductionEntity() {
		// for JPA
	}

	UUID getRunId() {
		return runId;
	}

	String getIntroText() {
		return introText;
	}

	NarrationSource getSource() {
		return source;
	}

	int getPromptVersion() {
		return promptVersion;
	}
}
