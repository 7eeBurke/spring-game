package com.leeburke.springgame.ai;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A scripted AI provider for tests: returns queued responses in order and records every request.
 * It never touches the network. Running out of scripted responses fails the test.
 */
public final class FakeAiProvider implements AiProvider {

	private final Deque<AiResponse> responses = new ArrayDeque<>();
	private final List<AiStructuredRequest> structured = new ArrayList<>();
	private final List<AiTextRequest> text = new ArrayList<>();
	private Runnable onCall = () -> {
	};

	public static FakeAiProvider answering(String... outputs) {
		FakeAiProvider provider = new FakeAiProvider();
		for (String output : outputs) {
			provider.then(output);
		}
		return provider;
	}

	public FakeAiProvider then(String output) {
		responses.add(new AiResponse.Success(output, Duration.ofMillis(5), java.util.Optional.empty()));
		return this;
	}

	/** A successful answer that also reports token usage. */
	public FakeAiProvider then(String output, AiUsage usage) {
		responses.add(new AiResponse.Success(output, Duration.ofMillis(5), java.util.Optional.of(usage)));
		return this;
	}

	public FakeAiProvider thenFail(AiFailureKind kind) {
		responses.add(AiResponse.Failure.of(kind));
		return this;
	}

	/** Runs before answering each call, for example to assert that no transaction is open. */
	public FakeAiProvider onCall(Runnable check) {
		this.onCall = check;
		return this;
	}

	@Override
	public AiResponse generateStructured(AiStructuredRequest request) {
		structured.add(request);
		return next();
	}

	@Override
	public AiResponse generateText(AiTextRequest request) {
		text.add(request);
		return next();
	}

	private AiResponse next() {
		onCall.run();
		if (responses.isEmpty()) {
			throw new AssertionError("The fake AI provider was called more often than scripted");
		}
		return responses.poll();
	}

	public List<AiStructuredRequest> structuredRequests() {
		return List.copyOf(structured);
	}

	public List<AiTextRequest> textRequests() {
		return List.copyOf(text);
	}

	public int calls() {
		return structured.size() + text.size();
	}

	public static AiGenerationSettings settings() {
		return new AiGenerationSettings("test-model", 500, Duration.ofSeconds(5), java.util.Optional.empty());
	}
}
