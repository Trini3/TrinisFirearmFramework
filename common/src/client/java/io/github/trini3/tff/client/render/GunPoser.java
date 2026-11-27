package io.github.trini3.tff.client.render;

import io.github.trini3.tff.anim.GunAnimation;
import io.github.trini3.tff.anim.Pose;
import io.github.trini3.tff.client.gun.GunView;
import java.util.Optional;

/**
 * Turns what a gun is doing ({@link GunView}) into a pose, by layering its animation's clips:
 * <ol>
 *   <li>held poses, blended by weight: {@code idle} (looping sway), {@code aim}, {@code sprint},
 *       {@code loaded} and {@code empty}. A looping clip plays on the clock; any other clip
 *       holds its last frame, so a one-keyframe clip is simply a pose</li>
 *   <li>one-shot clips from the moment they started: {@code fire} (or {@code fire_aim} while
 *       aiming), {@code dry_fire}</li>
 *   <li>the reload clip, {@code reload} for the first step and {@code reload_round} for each
 *       further round, stretched over the step's length</li>
 * </ol>
 * Clips add up, so a fire kick plays on top of whatever pose the gun is in.
 */
public final class GunPoser {
	/** How much of the idle sway is left while aiming. */
	private static final float AIM_IDLE_SHARE = 0.25f;
	/** Kick left while aiming, for guns without a {@code fire_aim} clip. */
	private static final float AIM_FIRE_SHARE = 0.6f;

	private GunPoser() {}

	/**
	 * Builds the pose for one frame. Does not compute the matrices, so callers can still offset
	 * bones (the aim-down-sights alignment) first.
	 *
	 * @param viewBones whether this is the first-person view (bones like the arms take part)
	 * @param now       seconds on the animation clock (for looping clips)
	 */
	public static void pose(Pose pose, GunView view, boolean viewBones, double now) {
		GunAnimation animation = pose.animation();
		pose.reset();
		hold(pose, animation, "idle", viewBones ? 1f - (1f - AIM_IDLE_SHARE) * view.aim : 1f, now, viewBones);
		hold(pose, animation, "aim", view.aim, now, viewBones);
		hold(pose, animation, "sprint", view.sprint, now, viewBones);
		hold(pose, animation, "loaded", 1f - view.empty, now, viewBones);
		hold(pose, animation, "empty", view.empty, now, viewBones);

		if (view.fireAge >= 0f) {
			Optional<GunAnimation.Bound> fire = animation.clip("fire");
			Optional<GunAnimation.Bound> fireAim = animation.clip("fire_aim");
			if (fireAim.isPresent()) {
				fire.ifPresent(clip -> once(pose, clip, view.fireAge, 1f - view.aim, viewBones));
				once(pose, fireAim.get(), view.fireAge, view.aim, viewBones);
			} else {
				fire.ifPresent(clip -> once(pose, clip, view.fireAge, 1f - (1f - AIM_FIRE_SHARE) * view.aim, viewBones));
			}
		}
		if (view.dryAge >= 0f) {
			animation.clip("dry_fire").ifPresent(clip -> once(pose, clip, view.dryAge, 1f, viewBones));
		}
		if (view.reloadWeight > 0f) {
			Optional<GunAnimation.Bound> clip = view.reloadStep > 0 ? animation.clip("reload_round") : Optional.empty();
			clip.or(() -> animation.clip("reload")).ifPresent(reload ->
				pose.apply(reload, view.reloadProgress * reload.length(), view.reloadWeight, viewBones));
		}
	}

	/** The pose the gun settles into when fully aimed, used to line the sights up. */
	public static void aimReference(Pose pose) {
		pose.reset();
		pose.animation().clip("aim").ifPresent(clip -> pose.apply(clip, clip.length(), 1f, true));
	}

	private static void hold(Pose pose, GunAnimation animation, String name, float weight, double now, boolean viewBones) {
		if (weight <= 0f) {
			return;
		}
		animation.clip(name).ifPresent(clip -> {
			float length = clip.length();
			float time = clip.clip().loop() && length > 0f ? (float) (now % length) : length;
			pose.apply(clip, time, weight, viewBones);
		});
	}

	private static void once(Pose pose, GunAnimation.Bound clip, float age, float weight, boolean viewBones) {
		if (weight > 0f && age <= clip.length()) {
			pose.apply(clip, age, weight, viewBones);
		}
	}
}
