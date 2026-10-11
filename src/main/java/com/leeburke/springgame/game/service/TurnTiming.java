package com.leeburke.springgame.game.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionTarget;

/**
 * One safe diagnostic line per new turn request: how long each phase took (interpretation, route
 * grounding, mechanics, narration) and, when grounding rewrote the interpreted journey, both shapes
 * as action types, movement kinds and target kinds only. Never player text, run or request IDs,
 * aliases, tokens or credentials.
 */
final class TurnTiming {

	private static final Logger LOG = LoggerFactory.getLogger(TurnTiming.class);

	private final long start = System.nanoTime();
	private long mark = start;
	private final Map<String, Long> phases = new LinkedHashMap<>();
	private String grounding = "-";

	TurnTiming() {
		for (String phase : new String[] { "interpret", "grounding", "mechanics", "narration" }) {
			phases.put(phase, 0L);
		}
	}

	/** Ends the named phase now; time since the last mark is added to it. */
	void phase(String name) {
		long now = System.nanoTime();
		phases.merge(name, (now - mark) / 1_000_000, Long::sum);
		mark = now;
	}

	/** Records a rewrite by route grounding. */
	void grounded(ActionIntent raw, ActionIntent grounded) {
		grounding = "raw[" + shape(raw) + "]->[" + shape(grounded) + "]";
	}

	void log(String outcome) {
		LOG.info("turn timing outcome={} interpretMs={} groundingMs={} mechanicsMs={} narrationMs={} totalMs={} grounding={}",
				outcome, phases.get("interpret"), phases.get("grounding"), phases.get("mechanics"), phases.get("narration"),
				(System.nanoTime() - start) / 1_000_000, grounding);
	}

	/** For example {@code MOVE:ADVANCE:NONE,OBSERVE:SEARCH}: kinds only, no aliases or words. */
	static String shape(ActionIntent intent) {
		return intent.steps().stream().map(step -> switch (step.payload()) {
			case ActionPayload.MovePayload m -> "MOVE:" + m.movementType() + ":" + kind(m.target());
			case ActionPayload.ObservePayload o -> "OBSERVE:" + o.kind();
			default -> step.actionType().name();
		}).collect(Collectors.joining(","));
	}

	private static String kind(ActionTarget target) {
		return switch (target) {
			case ActionTarget.ZoneTarget z -> "ZONE";
			case ActionTarget.ExitTarget x -> "EXIT";
			case ActionTarget.Unspecified u -> "NONE";
			default -> "OTHER";
		};
	}
}
