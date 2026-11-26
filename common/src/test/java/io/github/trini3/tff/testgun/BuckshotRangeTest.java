package io.github.trini3.tff.testgun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.trini3.tff.data.PelletPattern;
import io.github.trini3.tff.gun.GunMath;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Checks the shipped buckshot against a zombie (0.6 x 1.95 hitbox, 20 health, 2 armor) standing
 * on flat ground, shot from eye height and aimed at its middle. Flies each pellet the way
 * {@code BulletEntity#tick} does: hit test along the move, move, then drag and gravity.
 */
class BuckshotRangeTest {
	private static final double EYE = 1.62;
	private static final double ZOMBIE_HEALTH = 20.0;
	private static final double ZOMBIE_ARMOR = 2.0;

	private record Shot(int hits, double damage) {
		boolean kills() {
			return this.damage >= ZOMBIE_HEALTH;
		}
	}

	private static JsonObject buckshot() throws Exception {
		try (Reader reader = new InputStreamReader(Objects.requireNonNull(
			BuckshotRangeTest.class.getResourceAsStream("/data/shotgun/tff/ammo/buckshot.json")), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	private static Shot shoot(JsonObject ammo, double distance) {
		List<Vec2> pattern = PelletPattern.CODEC.parse(JsonOps.INSTANCE, ammo.get("pattern")).getOrThrow();
		float speed = ammo.get("speed").getAsFloat();
		float drag = ammo.get("drag").getAsFloat();
		float gravity = ammo.get("gravity").getAsFloat();
		int lifetime = ammo.get("lifetime").getAsInt();
		AABB zombie = new AABB(-0.3, 0, distance, 0.3, 1.95, distance + 0.6);
		Vec3 eye = new Vec3(0, EYE, 0);
		Vec3 d = new Vec3(0, 1.95 / 2, distance).subtract(eye);
		float yRot = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float xRot = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
		Vec3 aim = Vec3.directionFromRotation(xRot, yRot);

		int hits = 0;
		double damage = 0;
		for (Vec2 point : pattern) {
			Vec3 pos = eye;
			Vec3 v = GunMath.offset(aim, GunMath.viewRight(yRot), GunMath.viewUp(xRot, yRot), point.x, point.y).scale(speed);
			for (int tick = 1; tick <= lifetime && pos.y > 0; tick++) {
				Optional<Vec3> hit = zombie.clip(pos, pos.add(v));
				if (hit.isPresent()) {
					hits++;
					float dealt = GunMath.falloff(hit.get().distanceTo(eye), ammo.get("damage").getAsFloat(),
						ammo.get("min_damage").getAsFloat(), ammo.get("falloff_start").getAsFloat(), ammo.get("falloff_end").getAsFloat());
					damage += afterArmor(dealt);
					break;
				}
				pos = pos.add(v);
				v = v.scale(drag).add(0, -gravity, 0);
			}
		}
		return new Shot(hits, damage);
	}

	/** Vanilla's armor formula (no toughness). */
	private static double afterArmor(double damage) {
		double armor = Math.min(20.0, Math.max(ZOMBIE_ARMOR / 5.0, ZOMBIE_ARMOR - damage / 2.0));
		return damage * (1.0 - armor / 25.0);
	}

	@Test
	void nineInARingAroundOne() throws Exception {
		List<Vec2> pattern = PelletPattern.CODEC.parse(JsonOps.INSTANCE, buckshot().get("pattern")).getOrThrow();
		assertEquals(9, pattern.size());
		assertEquals(1, pattern.stream().filter(p -> p.x == 0f && p.y == 0f).count(), "one centre pellet");
	}

	@Test
	void everyPelletHitsUpClose() throws Exception {
		JsonObject ammo = buckshot();
		for (int distance = 2; distance <= 20; distance += 2) {
			assertEquals(9, shoot(ammo, distance).hits(), "hits at " + distance + " blocks");
		}
	}

	@Test
	void killsAZombieOutToFiftyBlocks() throws Exception {
		JsonObject ammo = buckshot();
		for (int distance = 2; distance <= 50; distance++) {
			Shot shot = shoot(ammo, distance);
			assertTrue(shot.hits() >= 5 && shot.kills(), distance + " blocks: " + shot);
		}
	}

	@Test
	void fallsOffPastFiftyBlocks() throws Exception {
		JsonObject ammo = buckshot();
		double previous = Double.MAX_VALUE;
		for (int distance = 50; distance <= 100; distance += 5) {
			Shot shot = shoot(ammo, distance);
			assertTrue(shot.damage() <= previous + 1e-6, distance + " blocks: " + shot + " did more than nearer in");
			previous = shot.damage();
		}
		for (int distance = 66; distance <= 100; distance += 2) {
			Shot shot = shoot(ammo, distance);
			assertTrue(!shot.kills(), distance + " blocks should not kill: " + shot);
		}
	}
}
