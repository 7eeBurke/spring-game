package com.leeburke.springgame.persistence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;

/**
 * JPA mapping of {@code player_character} and its body/tool-belt child tables.
 * <p>
 * The primary key is the owning run's ID (one character per run). There is deliberately no JPA
 * association to {@link GameRunEntity}; the foreign key is enforced by the database. Static content
 * is referenced by definition code only.
 */
@Entity
@Table(name = "player_character")
public class PlayerCharacterEntity {

	@Id
	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "might", nullable = false)
	private int might;

	@Column(name = "agility", nullable = false)
	private int agility;

	@Column(name = "perception", nullable = false)
	private int perception;

	@Column(name = "arcana", nullable = false)
	private int arcana;

	@Column(name = "resolve", nullable = false)
	private int resolve;

	@Column(name = "fated", nullable = false)
	private int fated;

	@Column(name = "max_hp", nullable = false)
	private int maxHp;

	@Column(name = "current_hp", nullable = false)
	private int currentHp;

	@Column(name = "passive_code", nullable = false)
	private String passiveCode;

	@Column(name = "ability_code", nullable = false)
	private String abilityCode;

	@ElementCollection
	@CollectionTable(name = "player_body_part", joinColumns = @JoinColumn(name = "run_id"))
	@MapKeyEnumerated(EnumType.STRING)
	@MapKeyColumn(name = "body_part")
	@Enumerated(EnumType.STRING)
	@Column(name = "severity", nullable = false)
	private Map<BodyPart, BodySeverity> bodyParts = new EnumMap<>(BodyPart.class);

	@ElementCollection
	@CollectionTable(name = "player_tool_belt_entry", joinColumns = @JoinColumn(name = "run_id"))
	@OrderColumn(name = "slot_index")
	private List<ToolBeltEntryEmbeddable> toolBelt = new ArrayList<>();

	protected PlayerCharacterEntity() {
		// for JPA
	}

	PlayerCharacterEntity(UUID runId, String name, int might, int agility, int perception, int arcana, int resolve,
			int fated, int maxHp, int currentHp, String passiveCode, String abilityCode,
			Map<BodyPart, BodySeverity> bodyParts, List<ToolBeltEntryEmbeddable> toolBelt) {
		this.runId = runId;
		this.name = name;
		this.might = might;
		this.agility = agility;
		this.perception = perception;
		this.arcana = arcana;
		this.resolve = resolve;
		this.fated = fated;
		this.maxHp = maxHp;
		this.currentHp = currentHp;
		this.passiveCode = passiveCode;
		this.abilityCode = abilityCode;
		this.bodyParts = new EnumMap<>(BodyPart.class);
		this.bodyParts.putAll(bodyParts);
		this.toolBelt = new ArrayList<>(toolBelt);
	}

	UUID getRunId() {
		return runId;
	}

	String getName() {
		return name;
	}

	int getMight() {
		return might;
	}

	int getAgility() {
		return agility;
	}

	int getPerception() {
		return perception;
	}

	int getArcana() {
		return arcana;
	}

	int getResolve() {
		return resolve;
	}

	int getFated() {
		return fated;
	}

	int getMaxHp() {
		return maxHp;
	}

	int getCurrentHp() {
		return currentHp;
	}

	String getPassiveCode() {
		return passiveCode;
	}

	String getAbilityCode() {
		return abilityCode;
	}

	Map<BodyPart, BodySeverity> getBodyParts() {
		return bodyParts;
	}

	List<ToolBeltEntryEmbeddable> getToolBelt() {
		return toolBelt;
	}
}
