package io.github.trini3.tff.anim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A bone of a gun animation. Units are model pixels (the same numbers as the model JSON's
 * elements) and degrees. A bone's local transform is
 * {@code translate(position) * translate(pivot) * rotate(rotation) * scale(scale) * translate(-pivot)},
 * applied on top of its parent's. Rotation angles are applied X first, then Y, then Z; positive X
 * raises the muzzle (the muzzle points to -Z), positive Y turns it left, positive Z rolls the top left.
 *
 * @param parent   parent bone, or none for a top-level bone
 * @param pivot    point that rotation and scale happen around, in the parent's space
 * @param position rest offset
 * @param rotation rest rotation
 * @param scale    rest scale (0 on any axis hides the bone and its children)
 * @param viewOnly whether the bone only moves in first person, as {@code root}, {@code gun} and
 *                 the arms always do: elsewhere it sits exactly on its parent (rest offset
 *                 included). For parts that leave the gun in first person, like a shell that the
 *                 hand brings in, which would float in mid-air in third person
 */
public record BoneDef(Optional<String> parent, Vector3fc pivot, Vector3fc position, Vector3fc rotation, Vector3fc scale,
					  boolean viewOnly) {
	private static final Vector3fc ZERO = new Vector3f();
	private static final Vector3fc ONE = new Vector3f(1f, 1f, 1f);

	public static final Codec<BoneDef> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("parent").forGetter(BoneDef::parent),
		ExtraCodecs.VECTOR3F.optionalFieldOf("pivot", ZERO).forGetter(BoneDef::pivot),
		ExtraCodecs.VECTOR3F.optionalFieldOf("position", ZERO).forGetter(BoneDef::position),
		ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", ZERO).forGetter(BoneDef::rotation),
		ExtraCodecs.VECTOR3F.optionalFieldOf("scale", ONE).forGetter(BoneDef::scale),
		Codec.BOOL.optionalFieldOf("view_only", false).forGetter(BoneDef::viewOnly)
	).apply(i, BoneDef::new));
}
