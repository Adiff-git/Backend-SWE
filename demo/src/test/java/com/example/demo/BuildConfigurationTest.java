package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

class BuildConfigurationTest {

	@Test
	void lombok_shouldGenerateConstructorAndGetter() {
		LombokFixture fixture = new LombokFixture("catalog");

		assertThat(fixture.getName()).isEqualTo("catalog");
	}

	@Getter
	@RequiredArgsConstructor
	private static class LombokFixture {
		private final String name;
	}
}
