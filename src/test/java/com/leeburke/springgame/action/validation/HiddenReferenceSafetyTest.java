package com.leeburke.springgame.action.validation;

import static com.leeburke.springgame.action.ActionFixtures.attack;
import static com.leeburke.springgame.action.ActionFixtures.entity;
import static com.leeburke.springgame.action.ActionFixtures.intent;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionFixtures;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionPayload.InteractPayload;
import com.leeburke.springgame.action.ActionPayload.MovePayload;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.InteractionKind;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.WorldFixtures;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneViewProjector;

/**
 * Validation sees only the player view, so a hidden object's real ID and an invented ID must be
 * indistinguishable in every validation result.
 */
class HiddenReferenceSafetyTest {

	private static final ActionValidator VALIDATOR = new ActionValidator();

	/** The Stage 8 rich scene: relic_1, warden_1 and secret_stair are hidden; crypt is a hidden zone. */
	private static ActionValidationContext projectedContext() {
		SceneState authoritative = WorldFixtures.richScene();
		PlayerSceneView view = PlayerSceneViewProjector.project(authoritative, WorldFixtures.ENTRANCE,
				Set.of(WorldFixtures.ENTRANCE, WorldFixtures.AISLE, WorldFixtures.ALTAR, WorldFixtures.CRYPT));
		return new ActionValidationContext(view, ActionFixtures.references(), Set.of());
	}

	private static ActionValidationError onlyError(ActionPayload payload) {
		ActionValidationResult result = VALIDATOR.validate(intent(payload), projectedContext());
		assertThat(result.errors()).hasSize(1);
		return result.errors().getFirst();
	}

	private static InteractPayload push(String objectId) {
		return new InteractPayload(InteractionKind.PUSH, new ActionTarget.ObjectTarget(objectId, TargetSpecificity.EXPLICIT),
				Optional.empty(), ActionApproach.NORMAL);
	}

	private static void assertIndistinguishable(ActionValidationError hidden, String hiddenId, ActionValidationError invented,
			String inventedId) {
		assertThat(hidden.code()).isEqualTo(ActionValidationCode.UNKNOWN_SCENE_REFERENCE).isEqualTo(invented.code());
		assertThat(hidden.message().replace(hiddenId, "<id>")).isEqualTo(invented.message().replace(inventedId, "<id>"));
		// The message may echo the ID the intent supplied, but must add nothing that hints at hidden state.
		String addedText = hidden.message().replace(hiddenId, "<id>").toLowerCase();
		for (String forbidden : List.of("hidden", "secret", "discover", "exist", "visible")) {
			assertThat(addedText).doesNotContain(forbidden);
		}
	}

	@Test
	void hiddenObjectLooksExactlyLikeANonexistentOne() {
		ActionValidationError hidden = onlyError(push("relic_1"));
		ActionValidationError invented = onlyError(push("no_such_thing"));
		assertIndistinguishable(hidden, "relic_1", invented, "no_such_thing");
		assertThat(hidden.message()).doesNotContain("CURSED_RELIC");
	}

	@Test
	void hiddenEntityLooksExactlyLikeANonexistentOne() {
		assertIndistinguishable(onlyError(attack(entity("warden_1"))), "warden_1", onlyError(attack(entity("nobody_1"))), "nobody_1");
	}

	@Test
	void hiddenExitLooksExactlyLikeANonexistentOne() {
		MovePayload toSecret = new MovePayload(MovementType.ADVANCE,
				new ActionTarget.ExitTarget("secret_stair", TargetSpecificity.EXPLICIT), RelativeGoal.NONE, ActionApproach.NORMAL);
		MovePayload toNowhere = new MovePayload(MovementType.ADVANCE,
				new ActionTarget.ExitTarget("no_stair", TargetSpecificity.EXPLICIT), RelativeGoal.NONE, ActionApproach.NORMAL);
		assertIndistinguishable(onlyError(toSecret), "secret_stair", onlyError(toNowhere), "no_stair");
	}

	@Test
	void contentInsideAHiddenZoneLooksNonexistent() {
		assertIndistinguishable(onlyError(push("crypt_chest")), "crypt_chest", onlyError(push("no_chest")), "no_chest");
		MovePayload intoCrypt = new MovePayload(MovementType.REPOSITION,
				new ActionTarget.ZoneTarget(WorldFixtures.CRYPT, TargetSpecificity.EXPLICIT), RelativeGoal.NONE, ActionApproach.NORMAL);
		MovePayload intoVoid = new MovePayload(MovementType.REPOSITION,
				new ActionTarget.ZoneTarget("void", TargetSpecificity.EXPLICIT), RelativeGoal.NONE, ActionApproach.NORMAL);
		assertIndistinguishable(onlyError(intoCrypt), WorldFixtures.CRYPT, onlyError(intoVoid), "void");
	}

	@Test
	void visibleContentOfTheSameSceneValidates() {
		assertThat(VALIDATOR.validate(intent(push("pew_1")), projectedContext()).valid()).isTrue();
	}

	@Test
	void validationContextCannotHoldAuthoritativeOrBackendState() {
		Set<Class<?>> reachable = new HashSet<>();
		collect(ActionValidationContext.class, reachable);
		assertThat(reachable).doesNotContain(SceneState.class, SceneInstance.class, UUID.class);
		assertThat(reachable).noneMatch(type -> type.getPackageName().contains("persistence"));
	}

	private static void collect(Type type, Set<Class<?>> seen) {
		if (type instanceof ParameterizedType parameterized) {
			collect(parameterized.getRawType(), seen);
			for (Type argument : parameterized.getActualTypeArguments()) {
				collect(argument, seen);
			}
		} else if (type instanceof Class<?> c && seen.add(c) && c.isRecord()) {
			for (RecordComponent component : c.getRecordComponents()) {
				collect(component.getGenericType(), seen);
			}
		}
	}
}
