package io.github.trini3.tff.client.gun;

import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.SoundCue;
import io.github.trini3.tff.gun.GunMath;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The shooter's own sounds and smoke, played the moment the client predicts a shot or reload
 * step. The server sends the same effects to everyone else but skips the shooter.
 */
public final class GunEffects {
	private GunEffects() {}

	/** Plays a sound at the player that only the player hears (the server plays it for others). */
	public static void playLocal(LocalPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		RandomSource random = player.getRandom();
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume,
			pitch * (1.0f + Mth.randomBetween(random, -0.04f, 0.04f)));
	}

	/** Plays the cues that fall on {@code tick} of a reload step. */
	public static void playCues(LocalPlayer player, List<SoundCue> cues, long tick) {
		for (SoundCue cue : SoundCue.at(cues, tick)) {
			playLocal(player, cue.sound(), cue.volume(), cue.pitch());
		}
	}

	public static void fire(LocalPlayer player, GunDefinition gun, boolean aiming) {
		gun.sounds().fire().ifPresent(sound -> playLocal(player, sound, 1f, 1f));
		smoke(player, gun, aiming);
	}

	public static void dryFire(LocalPlayer player, GunDefinition gun) {
		gun.sounds().dryFire().ifPresent(sound -> playLocal(player, sound, 0.8f, 1f));
	}

	/**
	 * Muzzle smoke where the shooter sees the muzzle: at the first-person gun's barrel (converted
	 * from the hand's field of view to the world's) or, in third person, where others see it.
	 */
	private static void smoke(LocalPlayer player, GunDefinition gun, boolean aiming) {
		GunDefinition.Muzzle muzzle = gun.muzzle();
		if (muzzle.count() <= 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = player.getEyePosition();
		Vec3 pos = firstPersonMuzzle(mc, player).orElseGet(() -> GunMath.muzzlePosition(eye, player.getYRot(), player.getXRot(),
			player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f, aiming));
		Vec3 aim = player.getViewVector(1f);
		RandomSource random = player.getRandom();
		for (int i = 0; i < muzzle.count(); i++) {
			double speed = muzzle.speed() * (0.5 + random.nextDouble());
			player.level().addParticle(muzzle.particle(),
				pos.x + random.triangle(0.0, 0.04), pos.y + random.triangle(0.0, 0.04), pos.z + random.triangle(0.0, 0.04),
				aim.x * speed + random.triangle(0.0, 0.01), aim.y * speed + random.triangle(0.0, 0.01) + 0.005, aim.z * speed + random.triangle(0.0, 0.01));
		}
	}

	private static Optional<Vec3> firstPersonMuzzle(Minecraft mc, LocalPlayer player) {
		if (!mc.options.getCameraType().isFirstPerson()) {
			return Optional.empty();
		}
		return LocalGun.muzzleInView().map(view -> {
			// The hand is drawn with a 70 degree field of view; the world with the player's own.
			// Scaling sideways by the ratio of the two keeps the smoke on the barrel on screen.
			float worldFov = mc.gameRenderer.mainCamera().getFov();
			float ratio = (float) (Math.tan(Math.toRadians(worldFov) / 2.0) / Math.tan(Math.toRadians(70.0) / 2.0));
			Vector3f v = new Vector3f(view.x() * ratio, view.y() * ratio, view.z());
			// Camera space (x right, y up, looking down -z) to world space.
			v.rotateX((float) Math.toRadians(-player.getXRot())).rotateY((float) Math.toRadians(-player.getYRot() + 180f));
			Vec3 camera = mc.gameRenderer.mainCamera().position();
			return camera.add(v.x, v.y, v.z);
		});
	}
}
