package io.github.trini3.tff.anim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A pose being built for one frame: rest transforms plus weighted clips, turned into one matrix per
 * bone. Matrices are in block units of the item model's space (model pixels / 16), ready to use as an
 * item layer's local transform. Reused between frames; not thread safe.
 */
public final class Pose {
	private static final float DEG = (float) (Math.PI / 180.0);

	private final GunAnimation animation;
	private final float[] position;
	private final float[] rotation;
	private final float[] scale;
	private final Matrix4f[] world;
	private final Vector3f scratch = new Vector3f();

	public Pose(GunAnimation animation) {
		this.animation = animation;
		int n = animation.boneCount();
		this.position = new float[n * 3];
		this.rotation = new float[n * 3];
		this.scale = new float[n * 3];
		this.world = new Matrix4f[n];
		for (int i = 0; i < n; i++) {
			this.world[i] = new Matrix4f();
		}
		this.reset();
	}

	public GunAnimation animation() {
		return this.animation;
	}

	/** Back to the rest pose. */
	public void reset() {
		for (int i = 0; i < this.animation.boneCount(); i++) {
			BoneDef def = this.animation.bone(i);
			set(this.position, i, def.position());
			set(this.rotation, i, def.rotation());
			set(this.scale, i, def.scale());
		}
	}

	private static void set(float[] array, int bone, Vector3fc value) {
		array[bone * 3] = value.x();
		array[bone * 3 + 1] = value.y();
		array[bone * 3 + 2] = value.z();
	}

	/**
	 * Adds a clip at {@code time} seconds with {@code weight} (0 to 1).
	 *
	 * @param viewBones whether bones that only move the first-person view are included
	 */
	public void apply(GunAnimation.Bound clip, float time, float weight, boolean viewBones) {
		if (weight <= 0f) {
			return;
		}
		float t = clip.clip().clipTime(time);
		for (int i = 0; i < clip.bones().length; i++) {
			int bone = clip.bones()[i];
			if (!viewBones && this.animation.isViewOnly(bone)) {
				continue;
			}
			Clip.Track track = clip.tracks()[i];
			if (track.position().isPresent()) {
				Vector3f v = track.position().get().sample(t, this.scratch);
				this.addPosition(bone, v.x * weight, v.y * weight, v.z * weight);
			}
			if (track.rotation().isPresent()) {
				Vector3f v = track.rotation().get().sample(t, this.scratch);
				this.addRotation(bone, v.x * weight, v.y * weight, v.z * weight);
			}
			if (track.scale().isPresent()) {
				Vector3f v = track.scale().get().sample(t, this.scratch);
				this.mulScale(bone, 1f + (v.x - 1f) * weight, 1f + (v.y - 1f) * weight, 1f + (v.z - 1f) * weight);
			}
		}
	}

	public void addPosition(int bone, float x, float y, float z) {
		this.position[bone * 3] += x;
		this.position[bone * 3 + 1] += y;
		this.position[bone * 3 + 2] += z;
	}

	public void addRotation(int bone, float x, float y, float z) {
		this.rotation[bone * 3] += x;
		this.rotation[bone * 3 + 1] += y;
		this.rotation[bone * 3 + 2] += z;
	}

	public void mulScale(int bone, float x, float y, float z) {
		this.scale[bone * 3] *= x;
		this.scale[bone * 3 + 1] *= y;
		this.scale[bone * 3 + 2] *= z;
	}

	/**
	 * Computes every bone's matrix from the accumulated values.
	 *
	 * @param viewBones whether bones that only move the first-person view keep their transforms;
	 *                  when false they are left at identity (third person, inventory, ground, ...)
	 */
	public void compute(boolean viewBones) {
		for (int i = 0; i < this.animation.boneCount(); i++) {
			BoneDef def = this.animation.bone(i);
			int parent = this.animation.parent(i);
			Matrix4f m = this.world[i];
			if (parent >= 0) {
				m.set(this.world[parent]);
			} else {
				m.identity();
			}
			if (!viewBones && this.animation.isViewOnly(i)) {
				continue;
			}
			float px = def.pivot().x() / 16f;
			float py = def.pivot().y() / 16f;
			float pz = def.pivot().z() / 16f;
			m.translate(this.position[i * 3] / 16f + px, this.position[i * 3 + 1] / 16f + py, this.position[i * 3 + 2] / 16f + pz)
				.rotateZYX(this.rotation[i * 3 + 2] * DEG, this.rotation[i * 3 + 1] * DEG, this.rotation[i * 3] * DEG)
				.scale(this.scale[i * 3], this.scale[i * 3 + 1], this.scale[i * 3 + 2])
				.translate(-px, -py, -pz);
		}
	}

	/** The bone's matrix from the last {@link #compute(boolean)}. */
	public Matrix4f matrix(int bone) {
		return this.world[bone];
	}

	/** Whether the bone is scaled away (by itself or a parent) and need not be drawn. */
	public boolean isHidden(int bone) {
		return Math.abs(this.world[bone].determinant3x3()) < 1.0e-9f;
	}
}
