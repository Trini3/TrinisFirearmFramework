package io.github.trini3.tff.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PoseTest {
	/** gun (view only) > barrel hinged at 8 4 4 > shell; plus a clip that opens the barrel 90 degrees. */
	private static final GunAnimation ANIMATION = GunAnimation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
		{"bones": {
		  "gun": {"position": [0, 1, 0]},
		  "barrel": {"parent": "gun", "pivot": [8, 4, 4]},
		  "shell": {"parent": "barrel"},
		  "hidden": {"scale": [0, 1, 1]}
		 },
		 "clips": {
		  "open": {"bones": {"barrel": {"rotation": {"0": [0, 0, 0], "1": [-90, 0, 0]}}}},
		  "kick": {"bones": {"barrel": {"position": [0, 0, 4]}}}
		 }}""")).getOrThrow();

	private static Vector3f transform(Pose pose, String bone, float x, float y, float z) {
		return pose.matrix(ANIMATION.boneIndex(bone)).transformPosition(new Vector3f(x, y, z).div(16f)).mul(16f);
	}

	private static void assertVec(float x, float y, float z, Vector3f v) {
		assertEquals(x, v.x, 1e-3f, "x");
		assertEquals(y, v.y, 1e-3f, "y");
		assertEquals(z, v.z, 1e-3f, "z");
	}

	@Test
	void restPoseAppliesPositionsDownTheChain() {
		Pose pose = new Pose(ANIMATION);
		pose.compute(true);
		assertVec(8, 5, 0, transform(pose, "shell", 8, 4, 0));
	}

	@Test
	void rotationTurnsAroundThePivot() {
		Pose pose = new Pose(ANIMATION);
		pose.apply(ANIMATION.clip("open").orElseThrow(), 1f, 1f, true);
		pose.compute(true);
		// Negative X rotation drops the muzzle: a point 4 pixels in front of the hinge (toward -Z) swings down 4.
		assertVec(8, 1, 4, transform(pose, "barrel", 8, 4, 0));
		assertVec(8, 4 + 1, 4, transform(pose, "barrel", 8, 4, 4));
		// Children follow.
		assertVec(8, 1, 4, transform(pose, "shell", 8, 4, 0));
	}

	@Test
	void weightScalesAClip() {
		Pose pose = new Pose(ANIMATION);
		pose.apply(ANIMATION.clip("kick").orElseThrow(), 0f, 0.25f, true);
		pose.compute(true);
		assertVec(8, 5, 1, transform(pose, "barrel", 8, 4, 0));
	}

	@Test
	void clipsAddUpAndResetClearsThem() {
		Pose pose = new Pose(ANIMATION);
		pose.apply(ANIMATION.clip("kick").orElseThrow(), 0f, 1f, true);
		pose.apply(ANIMATION.clip("kick").orElseThrow(), 0f, 1f, true);
		pose.compute(true);
		assertVec(8, 5, 8, transform(pose, "barrel", 8, 4, 0));
		pose.reset();
		pose.compute(true);
		assertVec(8, 5, 0, transform(pose, "barrel", 8, 4, 0));
	}

	@Test
	void viewBonesStayStillOutsideFirstPerson() {
		Pose pose = new Pose(ANIMATION);
		pose.apply(ANIMATION.clip("open").orElseThrow(), 1f, 1f, false);
		pose.compute(false);
		// The gun bone's rest offset is ignored, the barrel still opens.
		assertVec(8, 0, 4, transform(pose, "barrel", 8, 4, 0));
	}

	@Test
	void zeroScaleHidesABone() {
		Pose pose = new Pose(ANIMATION);
		pose.compute(true);
		assertTrue(pose.isHidden(ANIMATION.boneIndex("hidden")));
		assertFalse(pose.isHidden(ANIMATION.boneIndex("shell")));
	}
}
