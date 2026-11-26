package io.github.trini3.tff.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Where some mobs' heads are, for headshots. Loaded from {@code data/<namespace>/tff/head/<name>.json}.
 * Boxes are in pixels (1/16 block) from the middle of the mob's feet, turned with its body:
 * {@code x} to its left, {@code y} up, {@code z} forward. They are written for the mob at its normal
 * size and scaled with its hitbox, so babies and scaled mobs fit too. A bullet hits the head when
 * the point where it first touches the hitbox is inside a box. That point is always on the
 * hitbox's surface, so a box must reach out past the hitbox on the sides a head can be hit from.
 * Babies with a model of their own (bigger heads) can have their own boxes, written the same way
 * for the baby at its normal size.
 *
 * @param entities  the mob types (or {@code #tags}) this applies to
 * @param boxes     the head boxes; none means the mob has no head to hit
 * @param babyBoxes boxes for babies; without them babies use {@code boxes}, shrunk to their size
 */
public record HeadDefinition(HolderSet<EntityType<?>> entities, List<Box> boxes, Optional<List<Box>> babyBoxes) {
	public static final Codec<HeadDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
		RegistryCodecs.holderSet(Registries.ENTITY_TYPE).fieldOf("entities").forGetter(HeadDefinition::entities),
		Box.CODEC.listOf().fieldOf("boxes").forGetter(HeadDefinition::boxes),
		Box.CODEC.listOf().optionalFieldOf("baby_boxes").forGetter(HeadDefinition::babyBoxes)
	).apply(i, HeadDefinition::new));

	/** One box, from one corner to the opposite one, in pixels. */
	public record Box(Vector3fc from, Vector3fc to) {
		public static final Codec<Box> CODEC = RecordCodecBuilder.create(i -> i.group(
			ExtraCodecs.VECTOR3F.fieldOf("from").forGetter(Box::from),
			ExtraCodecs.VECTOR3F.fieldOf("to").forGetter(Box::to)
		).apply(i, Box::new));

		/** Whether a point (in pixels, in the mob's own frame) is inside, faces included. */
		public boolean contains(Vec3 local) {
			return within(local.x, this.from.x(), this.to.x())
				&& within(local.y, this.from.y(), this.to.y())
				&& within(local.z, this.from.z(), this.to.z());
		}

		private static boolean within(double value, float a, float b) {
			// The hit point sits exactly on a hitbox face, so allow for rounding.
			return value >= Math.min(a, b) - 1e-3 && value <= Math.max(a, b) + 1e-3;
		}
	}

	/** Whether this has boxes of its own for babies. */
	public boolean hasBabyBoxes() {
		return this.babyBoxes.isPresent();
	}

	/** Whether a point (in pixels, in the mob's own frame) is inside any of the boxes for a baby or an adult. */
	public boolean contains(Vec3 local, boolean baby) {
		for (Box box : baby && this.babyBoxes.isPresent() ? this.babyBoxes.get() : this.boxes) {
			if (box.contains(local)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Converts a world point into a mob's frame, in pixels at the mob's normal size.
	 *
	 * @param point  world position
	 * @param feet   the mob's position (middle of its feet)
	 * @param yaw    the mob's body yaw in degrees
	 * @param scaleX the mob's hitbox width over its type's normal width
	 * @param scaleY the mob's hitbox height over its type's normal height
	 */
	public static Vec3 toLocal(Vec3 point, Vec3 feet, float yaw, double scaleX, double scaleY) {
		double rad = Math.toRadians(yaw);
		double sin = Math.sin(rad);
		double cos = Math.cos(rad);
		double dx = point.x - feet.x;
		double dz = point.z - feet.z;
		// Forward is (-sin, 0, cos) like vanilla's look vector, and left is (cos, 0, sin).
		double forward = -dx * sin + dz * cos;
		double left = dx * cos + dz * sin;
		return new Vec3(left * 16.0 / scaleX, (point.y - feet.y) * 16.0 / scaleY, forward * 16.0 / scaleX);
	}
}
