package io.github.trini3.tff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TffTest {
	@Test
	void modIdIsStable() {
		assertEquals("tff", Tff.MOD_ID);
	}
}
