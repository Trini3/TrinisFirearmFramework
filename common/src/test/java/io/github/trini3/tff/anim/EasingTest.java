package io.github.trini3.tff.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EasingTest {
	@Test
	void everyEasingStartsAtZeroAndEndsAtOne() {
		for (Easing easing : Easing.values()) {
			assertEquals(1f, easing.apply(1f), 1e-5f, easing.id() + " end");
			if (easing != Easing.STEP) {
				assertEquals(0f, easing.apply(0f), 1e-5f, easing.id() + " start");
			}
		}
	}

	@Test
	void progressOutsideZeroToOneIsClamped() {
		assertEquals(0f, Easing.LINEAR.apply(-1f));
		assertEquals(1f, Easing.OUT_QUAD.apply(2f), 1e-6f);
	}

	@Test
	void stepHoldsUntilTheKeyframe() {
		assertEquals(0f, Easing.STEP.apply(0.99f));
		assertEquals(1f, Easing.STEP.apply(1f));
	}

	@Test
	void outBackOvershoots() {
		float max = 0f;
		for (int i = 0; i <= 100; i++) {
			max = Math.max(max, Easing.OUT_BACK.apply(i / 100f));
		}
		assertTrue(max > 1.05f, "overshoot " + max);
	}

	@Test
	void namesAreReadInEveryCommonSpelling() {
		assertEquals(Easing.OUT_BACK, Easing.parse("out_back").getOrThrow());
		assertEquals(Easing.OUT_BACK, Easing.parse("ease_out_back").getOrThrow());
		assertEquals(Easing.OUT_BACK, Easing.parse("easeOutBack").getOrThrow());
		assertEquals(Easing.IN_OUT_SINE, Easing.parse("easeInOutSine").getOrThrow());
		assertEquals(Easing.CATMULLROM, Easing.parse("catmullrom").getOrThrow());
		assertEquals(Easing.CATMULLROM, Easing.parse("smooth").getOrThrow());
		assertEquals(Easing.STEP, Easing.parse("hold").getOrThrow());
		assertTrue(Easing.parse("wobbly").isError());
	}
}
