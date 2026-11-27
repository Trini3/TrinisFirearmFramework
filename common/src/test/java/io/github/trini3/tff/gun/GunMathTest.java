package io.github.trini3.tff.gun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GunMathTest {
	@Test
	void spreadStaysInsideCone() {
		Random random = new Random(42);
		Vec3[] aims = {new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(1, 1, 1).normalize()};
		for (Vec3 aim : aims) {
			for (int i = 0; i < 2000; i++) {
				Vec3 dir = GunMath.spread(aim, 5f, random.nextDouble(), random.nextDouble());
				assertEquals(1.0, dir.length(), 1e-9);
				double angle = Math.toDegrees(Math.acos(Math.min(1.0, dir.dot(aim))));
				assertTrue(angle <= 5.0 + 1e-6, "angle " + angle);
			}
		}
	}

	@Test
	void zeroSpreadIsExact() {
		Vec3 aim = new Vec3(0, 0, 1);
		assertEquals(aim, GunMath.spread(aim, 0f, 0.7, 0.3));
	}

	@Test
	void spreadUsesWholeCone() {
		Vec3 aim = new Vec3(0, 0, 1);
		Vec3 edge = GunMath.spread(aim, 10f, 1.0, 0.0);
		assertEquals(10.0, Math.toDegrees(Math.acos(edge.dot(aim))), 1e-6);
	}

	@Test
	void viewVectorsFollowTheCamera() {
		// Facing south (+Z), the shooter's right is -X (west) and up is +Y.
		assertVec(new Vec3(-1, 0, 0), GunMath.viewRight(0f));
		assertVec(new Vec3(0, 1, 0), GunMath.viewUp(0f, 0f));
		// Looking straight down, up points the way the shooter faces.
		assertVec(new Vec3(0, 0, 1), GunMath.viewUp(90f, 0f));
		for (float pitch : new float[] {-90f, -30f, 0f, 45f, 90f}) {
			Vec3 forward = Vec3.directionFromRotation(pitch, 37f);
			assertEquals(0.0, GunMath.viewUp(pitch, 37f).dot(forward), 1e-4);
			assertEquals(0.0, GunMath.viewRight(37f).dot(forward), 1e-4);
		}
	}

	@Test
	void offsetTurnsByTheGivenAngles() {
		for (float pitch : new float[] {-89f, -20f, 0f, 60f}) {
			// Vanilla's table-based sine leaves this a hair off unit length, enough to skew acos.
			Vec3 aim = Vec3.directionFromRotation(pitch, 120f).normalize();
			Vec3 right = GunMath.viewRight(120f);
			Vec3 up = GunMath.viewUp(pitch, 120f);
			Vec3 dir = GunMath.offset(aim, right, up, 0.65f, 0f);
			assertEquals(1.0, dir.length(), 1e-9);
			assertEquals(0.65, Math.toDegrees(Math.acos(Math.min(1.0, dir.dot(aim)))), 1e-3);
			assertTrue(dir.dot(right) > 0, "turned right");
			Vec3 down = GunMath.offset(aim, right, up, 0f, -1f);
			assertEquals(1.0, Math.toDegrees(Math.acos(Math.min(1.0, down.dot(aim)))), 1e-3);
			assertTrue(down.dot(up) < 0, "turned down");
		}
		Vec3 aim = new Vec3(0, 0, 1);
		assertEquals(aim, GunMath.offset(aim, GunMath.viewRight(0f), GunMath.viewUp(0f, 0f), 0f, 0f));
	}

	private static void assertVec(Vec3 expected, Vec3 actual) {
		assertEquals(0.0, expected.distanceTo(actual), 1e-4, "expected " + expected + " got " + actual);
	}

	@Test
	void falloffIsLinearBetweenStartAndEnd() {
		assertEquals(10f, GunMath.falloff(5, 10f, 2f, 10f, 20f));
		assertEquals(6f, GunMath.falloff(15, 10f, 2f, 10f, 20f), 1e-5);
		assertEquals(2f, GunMath.falloff(25, 10f, 2f, 10f, 20f));
		assertEquals(2f, GunMath.falloff(12, 10f, 2f, 10f, 10f));
	}

	@Test
	void cooldownAllowsToleranceOnly() {
		assertTrue(GunMath.cooledDown(110, 100, 10, 0));
		assertTrue(!GunMath.cooledDown(109, 100, 10, 0));
		assertTrue(GunMath.cooledDown(109, 100, 10, 1));
		assertTrue(!GunMath.cooledDown(108, 100, 10, 1));
		assertTrue(GunMath.cooledDown(0, Long.MIN_VALUE / 2, 10, 0));
	}
}
