package io.github.trini3.tff.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class HeadDefinitionTest {
	private static final double EPS = 1e-9;

	private static void assertVec(Vec3 expected, Vec3 actual) {
		assertEquals(expected.x, actual.x, EPS, "x of " + actual);
		assertEquals(expected.y, actual.y, EPS, "y of " + actual);
		assertEquals(expected.z, actual.z, EPS, "z of " + actual);
	}

	/** Yaw 0 faces south (+Z): forward is +Z and the mob's left is east (+X). */
	@Test
	void facingSouth() {
		Vec3 feet = new Vec3(10, 64, -5);
		assertVec(new Vec3(16, 32, 48), HeadDefinition.toLocal(feet.add(1, 2, 3), feet, 0f, 1.0, 1.0));
	}

	/** Yaw 90 faces west (-X): forward is -X and the mob's left is south (+Z). */
	@Test
	void facingWest() {
		Vec3 feet = Vec3.ZERO;
		assertVec(new Vec3(8, 0, 16), HeadDefinition.toLocal(new Vec3(-1, 0, 0.5), feet, 90f, 1.0, 1.0));
		// Yaw 180 faces north: a point to the north is in front.
		assertVec(new Vec3(0, 0, 16), HeadDefinition.toLocal(new Vec3(0, 0, -1), feet, 180f, 1.0, 1.0));
	}

	/** Boxes are written for the normal size: on a half-size mob a point is twice as far out. */
	@Test
	void scalesWithTheHitbox() {
		assertVec(new Vec3(32, 32, 0), HeadDefinition.toLocal(new Vec3(1, 1, 0), Vec3.ZERO, 0f, 0.5, 0.5));
		assertVec(new Vec3(16, 8, 0), HeadDefinition.toLocal(new Vec3(1, 1, 0), Vec3.ZERO, 0f, 1.0, 2.0));
	}

	@Test
	void boxIncludesItsFacesWithSomeRoundingRoom() {
		HeadDefinition.Box box = new HeadDefinition.Box(new Vector3f(-4, 24, -4), new Vector3f(4, 32, 4));
		assertTrue(box.contains(new Vec3(0, 28, 0)), "middle");
		assertTrue(box.contains(new Vec3(4, 24, -4)), "corner");
		assertTrue(box.contains(new Vec3(4.0005, 28, 0)), "rounding past a face");
		assertFalse(box.contains(new Vec3(4.01, 28, 0)), "past a face");
		assertFalse(box.contains(new Vec3(0, 23.9, 0)), "below");
	}

	@Test
	void boxCornersCanBeGivenInAnyOrder() {
		HeadDefinition.Box box = new HeadDefinition.Box(new Vector3f(4, 32, 4), new Vector3f(-4, 24, -4));
		assertTrue(box.contains(new Vec3(-2, 30, 3)));
	}

	@Test
	void noBoxesMeansNoHead() {
		HeadDefinition none = new HeadDefinition(HolderSet.empty(), List.of(), Optional.empty());
		assertFalse(none.contains(Vec3.ZERO, false));
		assertFalse(none.contains(Vec3.ZERO, true), "babies too");
	}

	@Test
	void babiesUseTheirOwnBoxesWhenThereAreSome() {
		HeadDefinition.Box adult = new HeadDefinition.Box(new Vector3f(-16, 24, -16), new Vector3f(16, 40, 16));
		HeadDefinition.Box baby = new HeadDefinition.Box(new Vector3f(-16, 9, -16), new Vector3f(16, 24, 16));
		HeadDefinition both = new HeadDefinition(HolderSet.empty(), List.of(adult), Optional.of(List.of(baby)));
		assertTrue(both.contains(new Vec3(0, 10, 0), true), "baby head");
		assertFalse(both.contains(new Vec3(0, 30, 0), true), "adult head height on a baby");
		assertFalse(both.contains(new Vec3(0, 10, 0), false), "baby head height on an adult");
		HeadDefinition adultOnly = new HeadDefinition(HolderSet.empty(), List.of(adult), Optional.empty());
		assertTrue(adultOnly.contains(new Vec3(0, 30, 0), true), "no baby boxes: babies use the adult ones");
	}
}
