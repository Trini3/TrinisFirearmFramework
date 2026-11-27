package io.github.trini3.tff.anim;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * One animated value of a bone (position, rotation or scale) over time: a constant, or keyframes.
 * In JSON either {@code [x, y, z]} or an object of {@code "<seconds>": keyframe}, where a keyframe
 * is {@code [x, y, z]} or {@code {"value": [x, y, z], "easing": "ease_out_back"}}. The easing
 * shapes the way into that keyframe from the previous one.
 */
public final class Channel {
	public record Keyframe(float time, Vector3fc value, Easing easing) {}

	private record KeyframeValue(Vector3fc value, Easing easing) {
		static final Codec<KeyframeValue> OBJECT_CODEC = RecordCodecBuilder.create(i -> i.group(
			ExtraCodecs.VECTOR3F.optionalFieldOf("value").forGetter(k -> Optional.of(k.value)),
			ExtraCodecs.VECTOR3F.optionalFieldOf("post").forGetter(k -> Optional.empty()),
			Easing.CODEC.optionalFieldOf("easing").forGetter(k -> Optional.of(k.easing)),
			Codec.STRING.optionalFieldOf("lerp_mode").forGetter(k -> Optional.empty())
		).apply(i, (value, post, easing, lerpMode) -> new KeyframeValue(
			value.or(() -> post).orElse(new Vector3f()),
			easing.orElseGet(() -> lerpMode.flatMap(m -> Easing.parse(m).result()).orElse(Easing.LINEAR)))));

		static final Codec<KeyframeValue> CODEC = Codec.either(ExtraCodecs.VECTOR3F, OBJECT_CODEC).xmap(
			e -> e.map(v -> new KeyframeValue(v, Easing.LINEAR), k -> k),
			k -> k.easing == Easing.LINEAR ? Either.left(k.value) : Either.right(k));
	}

	private static final Codec<Channel> KEYFRAMES_CODEC = Codec.unboundedMap(Codec.STRING, KeyframeValue.CODEC).comapFlatMap(
		Channel::fromMap, Channel::toMap);

	public static final Codec<Channel> CODEC = Codec.either(ExtraCodecs.VECTOR3F, KEYFRAMES_CODEC).xmap(
		e -> e.map(Channel::constant, c -> c),
		c -> c.keyframes.length == 1 && c.keyframes[0].time == 0f ? Either.left(c.keyframes[0].value) : Either.right(c));

	private final Keyframe[] keyframes;

	public Channel(List<Keyframe> keyframes) {
		if (keyframes.isEmpty()) {
			throw new IllegalArgumentException("A channel needs at least one keyframe");
		}
		this.keyframes = keyframes.stream().sorted(Comparator.comparingDouble(Keyframe::time)).toArray(Keyframe[]::new);
	}

	public static Channel constant(Vector3fc value) {
		return new Channel(List.of(new Keyframe(0f, new Vector3f(value), Easing.LINEAR)));
	}

	private static DataResult<Channel> fromMap(Map<String, KeyframeValue> map) {
		if (map.isEmpty()) {
			return DataResult.error(() -> "Keyframe list is empty");
		}
		List<Keyframe> list = new ArrayList<>();
		for (Map.Entry<String, KeyframeValue> entry : map.entrySet()) {
			float time;
			try {
				time = Float.parseFloat(entry.getKey());
			} catch (NumberFormatException e) {
				return DataResult.error(() -> "Keyframe time is not a number: " + entry.getKey());
			}
			if (!(time >= 0f)) {
				return DataResult.error(() -> "Keyframe time must be 0 or more: " + entry.getKey());
			}
			list.add(new Keyframe(time, entry.getValue().value, entry.getValue().easing));
		}
		return DataResult.success(new Channel(list));
	}

	private Map<String, KeyframeValue> toMap() {
		Map<String, KeyframeValue> map = new LinkedHashMap<>();
		for (Keyframe k : this.keyframes) {
			map.put(Float.toString(k.time), new KeyframeValue(k.value, k.easing));
		}
		return map;
	}

	/** Time of the last keyframe, in seconds. */
	public float length() {
		return this.keyframes[this.keyframes.length - 1].time;
	}

	public List<Keyframe> keyframes() {
		return List.of(this.keyframes);
	}

	/** The value at {@code time} seconds, written to {@code out}. Before the first and after the last keyframe the value holds. */
	public Vector3f sample(float time, Vector3f out) {
		Keyframe[] k = this.keyframes;
		if (k.length == 1 || time <= k[0].time) {
			return out.set(k[0].value);
		}
		int last = k.length - 1;
		if (time >= k[last].time) {
			return out.set(k[last].value);
		}
		int lo = 0;
		int hi = last;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (k[mid].time <= time) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		Keyframe a = k[lo];
		Keyframe b = k[hi];
		float span = b.time - a.time;
		float t = span <= 0f ? 1f : (time - a.time) / span;
		if (b.easing == Easing.CATMULLROM) {
			Vector3fc p0 = k[Math.max(lo - 1, 0)].value;
			Vector3fc p3 = k[Math.min(hi + 1, last)].value;
			return out.set(
				catmullRom(t, p0.x(), a.value.x(), b.value.x(), p3.x()),
				catmullRom(t, p0.y(), a.value.y(), b.value.y(), p3.y()),
				catmullRom(t, p0.z(), a.value.z(), b.value.z(), p3.z()));
		}
		float e = b.easing.apply(t);
		return out.set(a.value).lerp(b.value, e);
	}

	static float catmullRom(float t, float p0, float p1, float p2, float p3) {
		float t2 = t * t;
		float t3 = t2 * t;
		return 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
	}
}
