package com.leeburke.springgame.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.CheckRequest;
import com.leeburke.springgame.mechanics.CheckResult;
import com.leeburke.springgame.mechanics.ContactQuality;
import com.leeburke.springgame.mechanics.DamageResult;
import com.leeburke.springgame.mechanics.DegreeOfSuccess;
import com.leeburke.springgame.mechanics.Effectiveness;
import com.leeburke.springgame.mechanics.ImpactSeverity;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.StatValue;
import com.leeburke.springgame.mechanics.Suitability;
import com.leeburke.springgame.mechanics.TraumaResult;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * The AI-side action contract must not be able to carry mechanical outcomes or backend identities.
 * Walks every type reachable from {@link ActionIntent}: record components, generic arguments and the
 * permitted subclasses of sealed interfaces.
 */
class ActionContractLeakTest {

	private static Set<Class<?>> reachable() {
		Set<Class<?>> seen = new HashSet<>();
		collect(ActionIntent.class, seen);
		return seen;
	}

	private static void collect(Type type, Set<Class<?>> seen) {
		if (type instanceof ParameterizedType parameterized) {
			collect(parameterized.getRawType(), seen);
			for (Type argument : parameterized.getActualTypeArguments()) {
				collect(argument, seen);
			}
			return;
		}
		if (!(type instanceof Class<?> c) || !seen.add(c)) {
			return;
		}
		if (c.isSealed()) {
			for (Class<?> permitted : c.getPermittedSubclasses()) {
				collect(permitted, seen);
			}
		}
		if (c.isRecord()) {
			for (RecordComponent component : c.getRecordComponents()) {
				collect(component.getGenericType(), seen);
			}
		}
	}

	@Test
	void containsNoMechanicsOutcomesOrBackendIdentities() {
		assertThat(reachable()).doesNotContain(
				StatType.class, StatValue.class, Suitability.class, CheckResult.class, CheckRequest.class,
				DegreeOfSuccess.class, ContactQuality.class, ImpactSeverity.class, Effectiveness.class,
				DamageResult.class, TraumaResult.class, SceneState.class, SceneInstance.class, PlayerSceneView.class,
				UUID.class, long.class, Long.class);
	}

	@Test
	void everyReachableTypeIsActionVocabularyJavaOrBodyPart() {
		for (Class<?> type : reachable()) {
			boolean allowed = type.getPackageName().equals("com.leeburke.springgame.action")
					|| type.isPrimitive()
					|| type.getPackageName().equals("java.lang")
					|| type.getPackageName().equals("java.util")
					|| type == BodyPart.class;
			assertThat(allowed).as(type.getName()).isTrue();
		}
		assertThat(reachable()).contains(ActionTarget.SelfTarget.class, ActionPayload.CommunicatePayload.class, List.class);
	}

	@Test
	void selfAndExitTargetsCarryNoIdentity() {
		assertThat(ActionTarget.SelfTarget.class.getRecordComponents()).extracting(RecordComponent::getName)
				.containsExactly("bodyPart", "specificity");
		assertThat(ActionTarget.ExitTarget.class.getRecordComponents()).extracting(RecordComponent::getType)
				.containsExactly(String.class, TargetSpecificity.class);
	}
}
