package com.leeburke.springgame.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionFixtures;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.TargetSpecificity;

/** The turn timing line describes a grounding rewrite by kinds only: no aliases, IDs or words. */
class TurnTimingTest {

	@Test
	void shapesAreActionAndTargetKindsOnly() {
		var raw = ActionFixtures.intent(new ActionPayload.MovePayload(MovementType.ADVANCE,
				new ActionTarget.ExitTarget("north_door", TargetSpecificity.EXPLICIT), RelativeGoal.NONE, ActionApproach.NORMAL),
				ActionFixtures.search());

		String shape = TurnTiming.shape(raw);

		assertThat(shape).isEqualTo("MOVE:ADVANCE:EXIT,OBSERVE:SEARCH").doesNotContain("north_door");
	}
}
