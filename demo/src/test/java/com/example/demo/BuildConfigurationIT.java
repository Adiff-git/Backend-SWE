package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuildConfigurationIT {

	@Test
	void runtime_shouldUseApprovedJavaVersion() {
		assertThat(Runtime.version().feature()).isEqualTo(25);
	}
}
