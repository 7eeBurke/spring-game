package com.leeburke.springgame.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.ai.narration.NarrationSource;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.Effectiveness;

/** JPA mapping of {@code pending_attack}: the incoming attack awaiting the player's defense. */
@Entity
@Table(name = "pending_attack")
public class PendingAttackEntity {

	@Id
	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "attack_ref", nullable = false)
	private String attackRef;

	@Column(name = "scene_id", nullable = false)
	private UUID sceneId;

	@Column(name = "attacker_entity_id", nullable = false)
	private String attackerEntityId;

	@Column(name = "option_code", nullable = false)
	private String optionCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "template", nullable = false)
	private AttackTemplate template;

	@Column(name = "difficulty", nullable = false)
	private int difficulty;

	@Column(name = "base_damage", nullable = false)
	private int baseDamage;

	@Column(name = "weapon_trauma", nullable = false)
	private int weaponTrauma;

	@Enumerated(EnumType.STRING)
	@Column(name = "effectiveness", nullable = false)
	private Effectiveness effectiveness;

	@Column(name = "attack_form_modifier", nullable = false)
	private int attackFormModifier;

	@Column(name = "anatomy_interaction_modifier", nullable = false)
	private int anatomyInteractionModifier;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_body_part")
	private BodyPart targetBodyPart;

	@Column(name = "weapon_code", nullable = false)
	private String weaponCode;

	@Column(name = "created_turn", nullable = false)
	private int createdTurn;

	@Column(name = "cue_text", nullable = false)
	private String cueText;

	@Column(name = "narration_text")
	private String narrationText;

	@Enumerated(EnumType.STRING)
	@Column(name = "narration_source")
	private NarrationSource narrationSource;

	protected PendingAttackEntity() {
		// for JPA
	}

	PendingAttackEntity(UUID runId, String attackRef, UUID sceneId, String attackerEntityId, String optionCode,
			AttackTemplate template, int difficulty, int baseDamage, int weaponTrauma, Effectiveness effectiveness,
			int attackFormModifier, int anatomyInteractionModifier, BodyPart targetBodyPart, String weaponCode,
			int createdTurn, String cueText) {
		this.runId = runId;
		this.attackRef = attackRef;
		this.sceneId = sceneId;
		this.attackerEntityId = attackerEntityId;
		this.optionCode = optionCode;
		this.template = template;
		this.difficulty = difficulty;
		this.baseDamage = baseDamage;
		this.weaponTrauma = weaponTrauma;
		this.effectiveness = effectiveness;
		this.attackFormModifier = attackFormModifier;
		this.anatomyInteractionModifier = anatomyInteractionModifier;
		this.targetBodyPart = targetBodyPart;
		this.weaponCode = weaponCode;
		this.createdTurn = createdTurn;
		this.cueText = cueText;
	}

	UUID getRunId() {
		return runId;
	}

	String getAttackRef() {
		return attackRef;
	}

	UUID getSceneId() {
		return sceneId;
	}

	String getAttackerEntityId() {
		return attackerEntityId;
	}

	String getOptionCode() {
		return optionCode;
	}

	AttackTemplate getTemplate() {
		return template;
	}

	int getDifficulty() {
		return difficulty;
	}

	int getBaseDamage() {
		return baseDamage;
	}

	int getWeaponTrauma() {
		return weaponTrauma;
	}

	Effectiveness getEffectiveness() {
		return effectiveness;
	}

	int getAttackFormModifier() {
		return attackFormModifier;
	}

	int getAnatomyInteractionModifier() {
		return anatomyInteractionModifier;
	}

	BodyPart getTargetBodyPart() {
		return targetBodyPart;
	}

	String getWeaponCode() {
		return weaponCode;
	}

	int getCreatedTurn() {
		return createdTurn;
	}

	String getCueText() {
		return cueText;
	}

	String getNarrationText() {
		return narrationText;
	}

	NarrationSource getNarrationSource() {
		return narrationSource;
	}

	void setNarration(String text, NarrationSource source) {
		this.narrationText = text;
		this.narrationSource = source;
	}
}
