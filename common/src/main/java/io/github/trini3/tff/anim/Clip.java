package io.github.trini3.tff.anim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;

/**
 * A named animation: keyframed offsets for some bones, added on top of their rest transforms
 * (positions and rotations add, scales multiply).
 *
 * @param length seconds; defaults to the last keyframe's time
 * @param loop   whether the clip repeats (idle and sprint sway); one-shot clips hold their last frame
 * @param bones  channels per bone name
 */
public record Clip(float length, boolean loop, Map<String, Track> bones) {
	/** The channels of one bone. */
	public record Track(Optional<Channel> position, Optional<Channel> rotation, Optional<Channel> scale) {
		public static final Codec<Track> CODEC = RecordCodecBuilder.create(i -> i.group(
			Channel.CODEC.optionalFieldOf("position").forGetter(Track::position),
			Channel.CODEC.optionalFieldOf("rotation").forGetter(Track::rotation),
			Channel.CODEC.optionalFieldOf("scale").forGetter(Track::scale)
		).apply(i, Track::new));

		float length() {
			float length = 0f;
			length = Math.max(length, this.position.map(Channel::length).orElse(0f));
			length = Math.max(length, this.rotation.map(Channel::length).orElse(0f));
			return Math.max(length, this.scale.map(Channel::length).orElse(0f));
		}
	}

	public static final Codec<Clip> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.floatRange(0f, 600f).optionalFieldOf("length").forGetter(c -> Optional.of(c.length)),
		Codec.BOOL.optionalFieldOf("loop", false).forGetter(Clip::loop),
		Codec.unboundedMap(Codec.STRING, Track.CODEC).optionalFieldOf("bones", Map.of()).forGetter(Clip::bones)
	).apply(i, (length, loop, bones) -> new Clip(
		length.orElseGet(() -> (float) bones.values().stream().mapToDouble(Track::length).max().orElse(0.0)), loop, Map.copyOf(bones))));

	/** Maps playback time to clip time: wraps for loops, holds the last frame otherwise. */
	public float clipTime(float time) {
		if (this.loop && this.length > 0f) {
			float t = time % this.length;
			return t < 0f ? t + this.length : t;
		}
		return Math.clamp(time, 0f, this.length);
	}
}
