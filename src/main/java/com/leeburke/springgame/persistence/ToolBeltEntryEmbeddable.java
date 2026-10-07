package com.leeburke.springgame.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One row of {@code player_tool_belt_entry}: what kind of entry and which static definition code.
 * The slot position is the collection's order column. No runtime instance IDs.
 */
@Embeddable
public class ToolBeltEntryEmbeddable {

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_kind", nullable = false)
	private ToolBeltEntryKind kind;

	@Column(name = "definition_code", nullable = false)
	private String definitionCode;

	protected ToolBeltEntryEmbeddable() {
		// for JPA
	}

	ToolBeltEntryEmbeddable(ToolBeltEntryKind kind, String definitionCode) {
		this.kind = kind;
		this.definitionCode = definitionCode;
	}

	ToolBeltEntryKind getKind() {
		return kind;
	}

	String getDefinitionCode() {
		return definitionCode;
	}
}
