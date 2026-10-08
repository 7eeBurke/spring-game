package com.leeburke.springgame.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.game.PendingAttack;

/** Persists the one pending enemy attack of a run, exactly as chosen: never rerolled on reload. */
@Service
public class PendingAttackStore {

	private final EntityManager entityManager;

	public PendingAttackStore(EntityManager entityManager) {
		this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
	}

	@Transactional(readOnly = true)
	public Optional<PendingAttack> find(UUID runId) {
		return Optional.ofNullable(entityManager.find(PendingAttackEntity.class, runId)).map(PendingAttackStore::toDomain);
	}

	/** Stores a new pending attack (without narration); joins the turn's mechanics transaction. */
	@Transactional
	public void insert(UUID runId, PendingAttack pending) {
		IncomingAttack a = pending.attack();
		entityManager.persist(new PendingAttackEntity(runId, a.ref(), pending.sceneId(), a.attackerEntityId(),
				pending.optionCode(), a.template(), a.difficulty(), a.baseDamage(), a.weaponTrauma(), a.effectiveness(),
				a.attackFormModifier(), a.anatomyInteractionModifier(), a.targetBodyPart().orElse(null), pending.weaponCode(),
				pending.createdTurn(), pending.cueText()));
		entityManager.flush();
	}

	/** Removes the attack once a defense has resolved it; joins the turn's mechanics transaction. */
	@Transactional
	public void delete(UUID runId) {
		PendingAttackEntity entity = entityManager.find(PendingAttackEntity.class, runId);
		if (entity != null) {
			entityManager.remove(entity);
			entityManager.flush();
		}
	}

	/** Stores the attack's finalised narration if this exact attack is still pending and not yet narrated. */
	@Transactional
	public void setNarration(UUID runId, String attackRef, PendingAttack.StoredNarration narration) {
		PendingAttackEntity entity = entityManager.find(PendingAttackEntity.class, runId);
		if (entity != null && entity.getAttackRef().equals(attackRef) && entity.getNarrationText() == null) {
			entity.setNarration(narration.text(), narration.source());
		}
	}

	private static PendingAttack toDomain(PendingAttackEntity e) {
		try {
			IncomingAttack attack = new IncomingAttack(e.getAttackRef(), e.getAttackerEntityId(), e.getTemplate(), e.getDifficulty(),
					e.getBaseDamage(), e.getWeaponTrauma(), e.getEffectiveness(), e.getAttackFormModifier(),
					e.getAnatomyInteractionModifier(), Optional.ofNullable(e.getTargetBodyPart()));
			Optional<PendingAttack.StoredNarration> narration = e.getNarrationText() == null
					? Optional.empty()
					: Optional.of(new PendingAttack.StoredNarration(e.getNarrationText(), e.getNarrationSource()));
			return new PendingAttack(attack, e.getSceneId(), e.getOptionCode(), e.getWeaponCode(), e.getCreatedTurn(),
					e.getCueText(), narration);
		} catch (IllegalArgumentException | NullPointerException ex) {
			throw new PersistedStateException("Run " + e.getRunId() + ": invalid pending attack: " + ex.getMessage(), ex);
		}
	}
}
