package io.github.trini3.tff.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec2;

/**
 * A fixed shot pattern: where each pellet goes, as an angle off the aim in degrees, {@code x} to
 * the right and {@code y} up (as the shooter sees it). In JSON each entry is either one point,
 * {@code [right, up]}, or a ring of evenly spaced points:
 * {@code {"ring": 8, "radius": 0.65, "rotation": 22.5}}. A ring's first point sits
 * {@code rotation} degrees clockwise from straight up.
 */
public final class PelletPattern {
	/** {@code count} points evenly spaced on a circle of {@code radius} degrees. */
	public record Ring(int count, float radius, float rotation) {
		public static final Codec<Ring> CODEC = RecordCodecBuilder.create(i -> i.group(
			ExtraCodecs.intRange(1, AmmoDefinition.MAX_PELLETS).fieldOf("ring").forGetter(Ring::count),
			ExtraCodecs.NON_NEGATIVE_FLOAT.fieldOf("radius").forGetter(Ring::radius),
			Codec.FLOAT.optionalFieldOf("rotation", 0f).forGetter(Ring::rotation)
		).apply(i, Ring::new));

		public List<Vec2> points() {
			List<Vec2> points = new ArrayList<>(this.count);
			for (int k = 0; k < this.count; k++) {
				double angle = Math.toRadians(this.rotation + 360.0 * k / this.count);
				points.add(new Vec2((float) (this.radius * Math.sin(angle)), (float) (this.radius * Math.cos(angle))));
			}
			return points;
		}
	}

	/** The whole pattern, expanded to one point per pellet. Encodes back as plain points. */
	public static final Codec<List<Vec2>> CODEC = Codec.either(Vec2.CODEC, Ring.CODEC).listOf().xmap(
		PelletPattern::expand,
		points -> points.stream().<Either<Vec2, Ring>>map(Either::left).toList());

	private PelletPattern() {}

	private static List<Vec2> expand(List<Either<Vec2, Ring>> entries) {
		List<Vec2> points = new ArrayList<>();
		for (Either<Vec2, Ring> entry : entries) {
			entry.ifLeft(points::add).ifRight(ring -> points.addAll(ring.points()));
		}
		return List.copyOf(points);
	}
}
