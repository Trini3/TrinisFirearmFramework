package io.github.trini3.tff.gun;

import net.minecraft.world.phys.Vec3;

/** Pure maths used by firing. No game state, so it is unit tested directly. */
public final class GunMath {
	private GunMath() {}

	/**
	 * Picks a direction inside a cone around the aim direction, uniformly over the cone's cap.
	 *
	 * @param aim       unit aim vector
	 * @param halfAngle cone half-angle in degrees
	 * @param u         uniform random in [0, 1), radial
	 * @param v         uniform random in [0, 1), around the axis
	 */
	public static Vec3 spread(Vec3 aim, float halfAngle, double u, double v) {
		if (halfAngle <= 0f) {
			return aim;
		}
		double cosMax = Math.cos(Math.toRadians(halfAngle));
		double cosTheta = 1.0 - u * (1.0 - cosMax);
		double sinTheta = Math.sqrt(Math.max(0.0, 1.0 - cosTheta * cosTheta));
		double phi = v * Math.PI * 2.0;

		Vec3 helper = Math.abs(aim.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 right = aim.cross(helper).normalize();
		Vec3 up = right.cross(aim).normalize();
		return aim.scale(cosTheta)
			.add(right.scale(sinTheta * Math.cos(phi)))
			.add(up.scale(sinTheta * Math.sin(phi)))
			.normalize();
	}

	/** Linear damage falloff between {@code start} and {@code end} blocks travelled. */
	public static float falloff(double distance, float damage, float minDamage, float start, float end) {
		if (distance <= start) {
			return damage;
		}
		if (distance >= end || end <= start) {
			return minDamage;
		}
		double t = (distance - start) / (end - start);
		return (float) (damage + (minDamage - damage) * t);
	}

	/** Whether a gun with this interval may fire again; {@code tolerance} ticks absorb network jitter on the server. */
	public static boolean cooledDown(long gameTime, long lastShot, int interval, int tolerance) {
		return gameTime - lastShot >= interval - tolerance;
	}

	/**
	 * Rough barrel tip for effects seen by other players, in world space: in front of the eyes, a
	 * little to the gun side and down (or centred while aiming).
	 *
	 * @param eye     eye position
	 * @param yRot    yaw in degrees
	 * @param xRot    pitch in degrees
	 * @param side    1 for a right-handed player, -1 for left
	 * @param aiming  whether the player aims down sights
	 */
	public static Vec3 muzzlePosition(Vec3 eye, float yRot, float xRot, float side, boolean aiming) {
		Vec3 forward = Vec3.directionFromRotation(xRot, yRot);
		Vec3 right = Vec3.directionFromRotation(0f, yRot + 90f);
		Vec3 up = forward.cross(right).scale(-1.0).normalize();
		return eye.add(forward.scale(1.1))
			.add(right.scale(aiming ? 0.0 : 0.25 * side))
			.add(up.scale(aiming ? -0.1 : -0.25));
	}
}
