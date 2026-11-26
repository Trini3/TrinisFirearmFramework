package io.github.trini3.tff.client.gun;

import io.github.trini3.tff.anim.Easing;
import io.github.trini3.tff.client.ClientClock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;

/**
 * Camera kick per shot, applied every frame so it is smooth at any frame rate. The view snaps up
 * over about a tick, then the recovered share of the kick eases back down; the rest stays, so
 * firing quickly climbs. Sideways kick stays too. The kick moves the player's actual aim (it goes
 * through {@link LocalPlayer#turn}, like the mouse), so where you see the crosshair is where the
 * next shot goes.
 */
public final class CameraRecoil {
	/** Seconds to reach the full kick. */
	private static final float RISE = 0.05f;
	/** Seconds for the recovered share to settle back. */
	private static final float SETTLE = 0.3f;
	/** {@link LocalPlayer#turn} multiplies its arguments by this to get degrees. */
	private static final double TURN_SCALE = 0.15;

	private record Kick(double start, float pitch, float yaw, float recovery) {}

	private static final List<Kick> KICKS = new ArrayList<>();
	private static float appliedPitch;
	private static float appliedYaw;

	private CameraRecoil() {}

	/**
	 * Adds a kick starting now.
	 *
	 * @param pitch    upward degrees
	 * @param yaw      sideways degrees (positive turns right)
	 * @param recovery share of the upward kick that settles back
	 */
	public static void kick(float pitch, float yaw, float recovery) {
		if (pitch != 0f || yaw != 0f) {
			KICKS.add(new Kick(ClientClock.now(), pitch, yaw, recovery));
		}
	}

	public static void reset() {
		KICKS.clear();
		appliedPitch = 0f;
		appliedYaw = 0f;
	}

	/** Called every frame before mouse movement is applied. */
	public static void apply(LocalPlayer player) {
		if (KICKS.isEmpty()) {
			return;
		}
		double now = ClientClock.now();
		float pitch = 0f;
		float yaw = 0f;
		float settledPitch = 0f;
		float settledYaw = 0f;
		for (Iterator<Kick> it = KICKS.iterator(); it.hasNext(); ) {
			Kick kick = it.next();
			float t = (float) (now - kick.start);
			if (t >= RISE + SETTLE) {
				// Done: its lasting share is already in the player's rotation, so stop tracking it.
				settledPitch += kick.pitch * (1f - kick.recovery);
				settledYaw += kick.yaw;
				it.remove();
				continue;
			}
			float rise = t <= 0f ? 0f : Easing.OUT_CUBIC.apply(Math.min(1f, t / RISE));
			float settle = t <= RISE ? 0f : Easing.IN_OUT_SINE.apply((t - RISE) / SETTLE);
			pitch += kick.pitch * (rise - settle * kick.recovery);
			yaw += kick.yaw * rise;
		}
		appliedPitch -= settledPitch;
		appliedYaw -= settledYaw;
		float dPitch = pitch - appliedPitch;
		float dYaw = yaw - appliedYaw;
		appliedPitch = pitch;
		appliedYaw = yaw;
		if (dPitch != 0f || dYaw != 0f) {
			// Up is negative pitch in Minecraft.
			player.turn(dYaw / TURN_SCALE, -dPitch / TURN_SCALE);
		}
	}
}
