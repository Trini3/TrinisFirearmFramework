package io.github.trini3.tff.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ChannelTest {
	private static Channel parse(String json) {
		JsonElement element = JsonParser.parseString(json);
		return Channel.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
	}

	private static void assertVec(float x, float y, float z, Vector3f v) {
		assertEquals(x, v.x, 1e-4f, "x");
		assertEquals(y, v.y, 1e-4f, "y");
		assertEquals(z, v.z, 1e-4f, "z");
	}

	@Test
	void plainVectorIsAConstant() {
		Channel channel = parse("[1, 2, 3]");
		assertEquals(0f, channel.length());
		assertVec(1, 2, 3, channel.sample(5f, new Vector3f()));
	}

	@Test
	void keyframesInterpolateLinearlyByDefault() {
		Channel channel = parse("{\"0\": [0, 0, 0], \"1\": [10, -4, 2]}");
		assertEquals(1f, channel.length());
		assertVec(5, -2, 1, channel.sample(0.5f, new Vector3f()));
	}

	@Test
	void valuesHoldBeforeTheFirstAndAfterTheLastKeyframe() {
		Channel channel = parse("{\"0.5\": [1, 1, 1], \"1\": [2, 2, 2]}");
		assertVec(1, 1, 1, channel.sample(0f, new Vector3f()));
		assertVec(2, 2, 2, channel.sample(3f, new Vector3f()));
	}

	@Test
	void easingShapesTheWayIntoAKeyframe() {
		Channel channel = parse("{\"0\": [0, 0, 0], \"1\": {\"value\": [10, 0, 0], \"easing\": \"in_quad\"}}");
		assertVec(2.5f, 0, 0, channel.sample(0.5f, new Vector3f()));
	}

	@Test
	void keyframeOrderInTheFileDoesNotMatter() {
		Channel channel = parse("{\"1\": [10, 0, 0], \"0\": [0, 0, 0], \"0.5\": [8, 0, 0]}");
		assertVec(8, 0, 0, channel.sample(0.5f, new Vector3f()));
		assertVec(9, 0, 0, channel.sample(0.75f, new Vector3f()));
	}

	@Test
	void blockbenchStyleKeyframesAreRead() {
		Channel channel = parse("{\"0\": {\"post\": [0, 0, 0], \"lerp_mode\": \"linear\"}, \"2\": {\"post\": [4, 0, 0], \"lerp_mode\": \"catmullrom\"}}");
		assertVec(4, 0, 0, channel.sample(2f, new Vector3f()));
	}

	@Test
	void badKeyframeTimesAreRejected() {
		JsonElement negative = JsonParser.parseString("{\"-1\": [0, 0, 0]}");
		assertTrue(Channel.CODEC.parse(JsonOps.INSTANCE, negative).isError());
		JsonElement word = JsonParser.parseString("{\"soon\": [0, 0, 0]}");
		assertTrue(Channel.CODEC.parse(JsonOps.INSTANCE, word).isError());
	}

	@Test
	void catmullRomPassesThroughItsKeyframes() {
		assertEquals(3f, Channel.catmullRom(0f, 1f, 3f, 7f, 9f), 1e-6f);
		assertEquals(7f, Channel.catmullRom(1f, 1f, 3f, 7f, 9f), 1e-6f);
	}
}
