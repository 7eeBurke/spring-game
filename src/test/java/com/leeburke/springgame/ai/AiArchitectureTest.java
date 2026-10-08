package com.leeburke.springgame.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** The vendor SDK stays at the edge, and the rules engine knows nothing about AI. */
class AiArchitectureTest {

	private static final Path MAIN = Path.of("src/main/java/com/leeburke/springgame");

	private static List<Path> javaFiles(Path root) throws IOException {
		try (Stream<Path> walk = Files.walk(root)) {
			return walk.filter(p -> p.toString().endsWith(".java")).toList();
		}
	}

	@Test
	void onlyTheAdapterImportsTheVendorSdk() throws IOException {
		Path adapter = MAIN.resolve("ai/openai");
		for (Path file : javaFiles(MAIN)) {
			if (!file.startsWith(adapter)) {
				assertThat(Files.readString(file)).as(file.toString()).doesNotContain("com.openai");
			}
		}
	}

	@Test
	void gameDomainImportsNoAi() throws IOException {
		for (String domain : List.of("action", "enemy", "world", "mechanics", "character", "content")) {
			for (Path file : javaFiles(MAIN.resolve(domain))) {
				assertThat(Files.readString(file)).as(file.toString()).doesNotContain("springgame.ai");
			}
		}
	}

	@Test
	void roleCodeIsPlainJava() throws IOException {
		for (String pkg : List.of("ai", "ai/interpreter", "ai/narration")) {
			try (Stream<Path> list = Files.list(MAIN.resolve(pkg))) {
				for (Path file : list.filter(p -> p.toString().endsWith(".java")).toList()) {
					String source = Files.readString(file);
					assertThat(source).as(file.toString()).doesNotContain("org.springframework", "jakarta.persistence",
							"springgame.persistence", "com.openai");
				}
			}
		}
	}
}
