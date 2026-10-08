package com.leeburke.springgame.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leeburke.springgame.game.service.ChronicleService;
import com.leeburke.springgame.game.service.GameViewService;
import com.leeburke.springgame.game.service.RunCreationService;
import com.leeburke.springgame.game.view.ChronicleView;
import com.leeburke.springgame.game.view.CreateRunResponse;
import com.leeburke.springgame.game.view.GameView;

/**
 * Run creation, the current view and the chronicle. Thin: authentication is done by {@link RunTokenInterceptor}
 * for paths with a run ID, and everything else by the application services.
 */
@RestController
@RequestMapping("/api/v1/runs")
class RunController {

	static final String INVITE_HEADER = "X-Invite-Code";
	static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

	private final RunCreationService creation;
	private final GameViewService views;
	private final ChronicleService chronicles;

	RunController(RunCreationService creation, GameViewService views, ChronicleService chronicles) {
		this.creation = Objects.requireNonNull(creation, "creation");
		this.views = Objects.requireNonNull(views, "views");
		this.chronicles = Objects.requireNonNull(chronicles, "chronicles");
	}

	/** Creates a run, or resumes the creation of the run with this key and token. */
	@PostMapping
	ResponseEntity<CreateRunResponse> create(@RequestHeader(name = INVITE_HEADER, required = false) String invite,
			@RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String creationKey,
			@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization, HttpServletRequest request) {
		// Only the socket address identifies a client; X-Forwarded-For and similar headers are never trusted.
		CreateRunResponse created = creation.create(invite, creationKey, authorization, request.getRemoteAddr());
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	/**
	 * A page of the run's story, oldest turn first. Pass the previous page's {@code nextBefore} as
	 * {@code before} for older turns. Never calls AI and never writes.
	 */
	@GetMapping("/{runId}/chronicle")
	ChronicleView chronicle(@PathVariable UUID runId, @RequestParam(required = false) Integer before,
			@RequestParam(defaultValue = "" + ChronicleService.DEFAULT_LIMIT) int limit) {
		return chronicles.chronicle(runId, Optional.ofNullable(before), limit);
	}

	/** The current view: never calls AI and never writes. */
	@GetMapping("/{runId}")
	GameView view(@PathVariable UUID runId) {
		return views.view(runId);
	}
}
