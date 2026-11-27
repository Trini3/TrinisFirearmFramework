package io.github.trini3.tff.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.Test;

class PelletPatternTest {
	private static List<Vec2> parse(String json) {
		return PelletPattern.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}

	@Test
	void pointsAndRingsExpandInOrder() {
		List<Vec2> points = parse("[[0, 0], {\"ring\": 4, \"radius\": 2}, [1.5, -0.5]]");
		assertEquals(6, points.size());
		assertPoint(0, 0, points.get(0));
		// A ring starts straight up and goes clockwise: up, right, down, left.
		assertPoint(0, 2, points.get(1));
		assertPoint(2, 0, points.get(2));
		assertPoint(0, -2, points.get(3));
		assertPoint(-2, 0, points.get(4));
		assertPoint(1.5f, -0.5f, points.get(5));
	}

	@Test
	void rotatedRingSkipsTheAxes() {
		List<Vec2> ring = parse("[{\"ring\": 8, \"radius\": 0.65, \"rotation\": 22.5}]");
		assertEquals(8, ring.size());
		for (Vec2 p : ring) {
			assertEquals(0.65, Math.hypot(p.x, p.y), 1e-5);
			assertTrue(Math.abs(p.x) > 0.2 && Math.abs(p.y) > 0.2, "no point on an axis: " + p.x + ", " + p.y);
		}
		assertPoint((float) (0.65 * Math.sin(Math.toRadians(22.5))), (float) (0.65 * Math.cos(Math.toRadians(22.5))), ring.getFirst());
	}

	@Test
	void encodesAsPlainPoints() {
		List<Vec2> points = parse("[[0, 0], {\"ring\": 3, \"radius\": 1}]");
		JsonElement json = PelletPattern.CODEC.encodeStart(JsonOps.INSTANCE, points).getOrThrow();
		assertEquals(4, json.getAsJsonArray().size());
		List<Vec2> again = parse(json.toString());
		for (int i = 0; i < points.size(); i++) {
			assertPoint(points.get(i).x, points.get(i).y, again.get(i));
		}
	}

	@Test
	void badEntriesAreRejected() {
		assertTrue(PelletPattern.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[[1, 2, 3]]")).isError());
		assertTrue(PelletPattern.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[{\"ring\": 0, \"radius\": 1}]")).isError());
		assertTrue(PelletPattern.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[{\"ring\": 4, \"radius\": -1}]")).isError());
	}

	private static void assertPoint(float x, float y, Vec2 actual) {
		assertEquals(x, actual.x, 1e-5, "x");
		assertEquals(y, actual.y, 1e-5, "y");
	}
}
