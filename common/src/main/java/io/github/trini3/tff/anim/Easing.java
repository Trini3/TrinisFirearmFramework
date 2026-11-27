package io.github.trini3.tff.anim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

/**
 * How a keyframe is reached from the one before it. Names follow the usual easing names and are
 * read in snake_case ({@code ease_out_back}) or camelCase ({@code easeOutBack}, as Blockbench and
 * GeckoLib write them); the {@code ease_} prefix is optional.
 */
public enum Easing {
	LINEAR("linear"),
	/** Holds the previous value, then jumps. */
	STEP("step"),
	/** Smooth curve through the neighbouring keyframes (Catmull-Rom). */
	CATMULLROM("catmullrom"),
	IN_SINE("in_sine"),
	OUT_SINE("out_sine"),
	IN_OUT_SINE("in_out_sine"),
	IN_QUAD("in_quad"),
	OUT_QUAD("out_quad"),
	IN_OUT_QUAD("in_out_quad"),
	IN_CUBIC("in_cubic"),
	OUT_CUBIC("out_cubic"),
	IN_OUT_CUBIC("in_out_cubic"),
	IN_BACK("in_back"),
	OUT_BACK("out_back"),
	IN_OUT_BACK("in_out_back"),
	OUT_BOUNCE("out_bounce");

	public static final Codec<Easing> CODEC = Codec.STRING.comapFlatMap(Easing::parse, Easing::id);

	private static final float BACK = 1.70158f;
	private final String id;

	Easing(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

	public static DataResult<Easing> parse(String name) {
		String key = normalize(name);
		for (Easing easing : values()) {
			if (easing.id.equals(key)) {
				return DataResult.success(easing);
			}
		}
		return DataResult.error(() -> "Unknown easing: " + name);
	}

	private static String normalize(String name) {
		StringBuilder out = new StringBuilder();
		for (char c : name.trim().toCharArray()) {
			if (Character.isUpperCase(c) && !out.isEmpty()) {
				out.append('_');
			}
			out.append(Character.toLowerCase(c));
		}
		String key = out.toString().replace('-', '_').toLowerCase(Locale.ROOT);
		if (key.startsWith("ease_")) {
			key = key.substring(5);
		}
		return switch (key) {
			case "smooth", "catmull_rom" -> "catmullrom";
			case "constant", "hold" -> "step";
			default -> key;
		};
	}

	/** Maps linear progress {@code t} (0 to 1) to eased progress. Back easings overshoot past 0 or 1. */
	public float apply(float t) {
		t = Math.clamp(t, 0f, 1f);
		return switch (this) {
			case LINEAR, CATMULLROM -> t;
			case STEP -> t >= 1f ? 1f : 0f;
			case IN_SINE -> 1f - (float) Math.cos(t * Math.PI / 2.0);
			case OUT_SINE -> (float) Math.sin(t * Math.PI / 2.0);
			case IN_OUT_SINE -> (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
			case IN_QUAD -> t * t;
			case OUT_QUAD -> 1f - (1f - t) * (1f - t);
			case IN_OUT_QUAD -> t < 0.5f ? 2f * t * t : 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f;
			case IN_CUBIC -> t * t * t;
			case OUT_CUBIC -> 1f - (1f - t) * (1f - t) * (1f - t);
			case IN_OUT_CUBIC -> t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
			case IN_BACK -> (BACK + 1f) * t * t * t - BACK * t * t;
			case OUT_BACK -> {
				float u = t - 1f;
				yield 1f + (BACK + 1f) * u * u * u + BACK * u * u;
			}
			case IN_OUT_BACK -> {
				float c = BACK * 1.525f;
				yield t < 0.5f
					? (float) (Math.pow(2f * t, 2) * ((c + 1f) * 2f * t - c)) / 2f
					: (float) (Math.pow(2f * t - 2f, 2) * ((c + 1f) * (t * 2f - 2f) + c) + 2f) / 2f;
			}
			case OUT_BOUNCE -> bounce(t);
		};
	}

	private static float bounce(float t) {
		float n = 7.5625f;
		float d = 2.75f;
		if (t < 1f / d) {
			return n * t * t;
		} else if (t < 2f / d) {
			t -= 1.5f / d;
			return n * t * t + 0.75f;
		} else if (t < 2.5f / d) {
			t -= 2.25f / d;
			return n * t * t + 0.9375f;
		}
		t -= 2.625f / d;
		return n * t * t + 0.984375f;
	}
}
