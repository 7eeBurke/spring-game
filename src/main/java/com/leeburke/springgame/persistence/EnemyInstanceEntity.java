package com.leeburke.springgame.persistence;

import java.util.EnumMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;

/**
 * JPA mapping of {@code enemy_instance} and its {@code enemy_body_part} child table. No association
 * to the scene entity; the foreign key is enforced by the database. Static enemy content is
 * referenced by definition code only.
 */
@Entity
@Table(name = "enemy_instance")
public class EnemyInstanceEntity {

	@EmbeddedId
	private EnemyInstanceId id;

	@Column(name = "definition_code", nullable = false)
	private String definitionCode;

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

	@Column(name = "max_hp", nullable = false)
	private int maxHp;

	@Column(name = "current_hp", nullable = false)
	private int currentHp;

	@Column(name = "weapon_code", nullable = false)
	private String weaponCode;

	@ElementCollection
	@CollectionTable(name = "enemy_body_part", joinColumns = {
			@JoinColumn(name = "scene_id", referencedColumnName = "scene_id"),
			@JoinColumn(name = "entity_local_id", referencedColumnName = "entity_local_id") })
	@MapKeyEnumerated(EnumType.STRING)
	@MapKeyColumn(name = "body_part")
	@Enumerated(EnumType.STRING)
	@Column(name = "severity", nullable = false)
	private Map<BodyPart, BodySeverity> bodyParts = new EnumMap<>(BodyPart.class);

	protected EnemyInstanceEntity() {
		// for JPA
	}

	EnemyInstanceEntity(EnemyInstanceId id, String definitionCode, int might, int agility, int perception, int arcana,
			int resolve, int maxHp, int currentHp, String weaponCode, Map<BodyPart, BodySeverity> bodyParts) {
		this.id = id;
		this.definitionCode = definitionCode;
		this.might = might;
		this.agility = agility;
		this.perception = perception;
		this.arcana = arcana;
		this.resolve = resolve;
		this.maxHp = maxHp;
		this.currentHp = currentHp;
		this.weaponCode = weaponCode;
		this.bodyParts = new EnumMap<>(BodyPart.class);
		this.bodyParts.putAll(bodyParts);
	}

	EnemyInstanceId getId() {
		return id;
	}

	String getDefinitionCode() {
		return definitionCode;
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

	int getMaxHp() {
		return maxHp;
	}

	int getCurrentHp() {
		return currentHp;
	}

	String getWeaponCode() {
		return weaponCode;
	}

	Map<BodyPart, BodySeverity> getBodyParts() {
		return bodyParts;
	}
}
