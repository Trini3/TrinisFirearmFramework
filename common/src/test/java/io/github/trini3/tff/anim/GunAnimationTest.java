package io.github.trini3.tff.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class GunAnimationTest {
	private static DataResult<GunAnimation> parse(String json) {
		return GunAnimation.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
	}

	private static JsonElement resource(String path) throws IOException {
		try (InputStream in = GunAnimationTest.class.getClassLoader().getResourceAsStream(path)) {
			assertNotNull(in, "missing resource " + path);
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader);
			}
		}
	}

	@Test
	void parentsComeBeforeChildrenWhateverTheFileOrder() {
		GunAnimation animation = parse("""
			{"bones": {
			  "shell": {"parent": "barrel"},
			  "barrel": {"parent": "gun"},
			  "gun": {}
			}}""").getOrThrow();
		int gun = animation.boneIndex("gun");
		int barrel = animation.boneIndex("barrel");
		int shell = animation.boneIndex("shell");
		assertTrue(gun < barrel && barrel < shell);
		assertEquals(gun, animation.parent(barrel));
		assertEquals(barrel, animation.parent(shell));
		assertEquals(-1, animation.boneIndex("missing"));
	}

	@Test
	void unknownParentsAndLoopsAreErrors() {
		assertTrue(parse("{\"bones\": {\"a\": {\"parent\": \"b\"}}}").isError());
		assertTrue(parse("{\"bones\": {\"a\": {\"parent\": \"b\"}, \"b\": {\"parent\": \"a\"}}}").isError());
	}

	@Test
	void onlyViewBonesAreViewOnly() {
		GunAnimation animation = parse("""
			{"bones": {"root": {}, "gun": {"parent": "root"}, "right_arm": {}, "left_arm": {}, "bolt": {"parent": "gun"},
			 "round": {"parent": "bolt", "view_only": true}}}""").getOrThrow();
		assertTrue(animation.isViewOnly(animation.boneIndex("root")));
		assertTrue(animation.isViewOnly(animation.boneIndex("gun")));
		assertTrue(animation.isViewOnly(animation.boneIndex("right_arm")));
		assertTrue(animation.isViewOnly(animation.boneIndex("left_arm")));
		assertFalse(animation.isViewOnly(animation.boneIndex("bolt")));
		assertTrue(animation.isViewOnly(animation.boneIndex("round")), "marked view_only");
	}

	@Test
	void clipLengthDefaultsToTheLastKeyframe() {
		GunAnimation animation = parse("""
			{"bones": {"bolt": {}},
			 "clips": {"fire": {"bones": {"bolt": {"position": {"0": [0, 0, 0], "0.3": [0, 0, 2]}}, "ghost": {"rotation": [1, 2, 3]}}}}}""").getOrThrow();
		GunAnimation.Bound fire = animation.clip("fire").orElseThrow();
		assertEquals(0.3f, fire.length(), 1e-6f);
		assertEquals(1, fire.bones().length, "tracks for bones the file doesn't define are dropped");
	}

	@Test
	void loopsWrapAndOneShotsHoldTheirLastFrame() {
		Clip loop = new Clip(2f, true, java.util.Map.of());
		assertEquals(0.5f, loop.clipTime(4.5f), 1e-6f);
		Clip once = new Clip(2f, false, java.util.Map.of());
		assertEquals(2f, once.clipTime(4.5f));
		assertEquals(0f, once.clipTime(-1f));
	}

	/** The test shotgun's animation file must load, and its reload must last as long as the gun's reload. */
	@Test
	void shotgunAnimationMatchesItsGun() throws IOException {
		GunAnimation animation = GunAnimation.CODEC.parse(JsonOps.INSTANCE, resource("assets/shotgun/tff/animations/shotgun.json")).getOrThrow();
		for (String bone : List.of("root", "gun", "barrel", "shell", "lever", "hammer", "right_arm", "left_arm")) {
			assertTrue(animation.boneIndex(bone) >= 0, "bone " + bone);
		}
		for (String clip : List.of("idle", "aim", "sprint", "fire", "fire_aim", "dry_fire", "reload")) {
			assertTrue(animation.clip(clip).isPresent(), "clip " + clip);
		}
		assertTrue(animation.clips().get("idle").loop(), "idle loops");

		JsonObject gun = resource("data/shotgun/tff/gun/shotgun.json").getAsJsonObject();
		int reloadTicks = gun.getAsJsonObject("reload").get("ticks").getAsInt();
		assertEquals(reloadTicks / 20f, animation.clip("reload").orElseThrow().length(), 1e-4f, "reload clip length vs reload ticks");

		// Every animated bone ends the reload where it started, so nothing jumps when the clip ends.
		Pose rest = new Pose(animation);
		rest.compute(true);
		Pose end = new Pose(animation);
		GunAnimation.Bound reload = animation.clip("reload").orElseThrow();
		end.apply(reload, reload.length(), 1f, true);
		end.compute(true);
		for (int i = 0; i < animation.boneCount(); i++) {
			Vector3f a = rest.matrix(i).transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
			Vector3f b = end.matrix(i).transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
			assertTrue(a.distance(b) < 1e-4f, "bone " + animation.boneName(i) + " ends the reload away from rest");
		}

		// Outside first person the shell stays in the barrel: the hand that carries it isn't drawn there.
		int shell = animation.boneIndex("shell");
		int barrel = animation.boneIndex("barrel");
		for (float t = 0f; t <= reload.length(); t += 0.05f) {
			Pose third = new Pose(animation);
			third.apply(reload, t, 1f, false);
			third.compute(false);
			Vector3f s = third.matrix(shell).transformPosition(new Vector3f(0.5f, 0.3f, 0.2f));
			Vector3f b = third.matrix(barrel).transformPosition(new Vector3f(0.5f, 0.3f, 0.2f));
			assertTrue(s.distance(b) < 1e-4f, "shell leaves the barrel in third person at " + t + " s");
		}
	}
}
