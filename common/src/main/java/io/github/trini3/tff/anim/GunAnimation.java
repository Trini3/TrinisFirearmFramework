package io.github.trini3.tff.anim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A gun's animation file ({@code assets/<namespace>/tff/animations/<name>.json}): its bones and
 * clips. Bones carry the gun's parts (by name, see the {@code tff:gun} item model) and the
 * first-person arms. The framework plays clips by name:
 * <ul>
 *   <li>{@code idle}, {@code aim}, {@code sprint}: poses blended by what the player is doing</li>
 *   <li>{@code loaded}, {@code empty}: poses held while the gun has rounds, or none</li>
 *   <li>{@code fire} (and {@code fire_aim} while aiming), {@code dry_fire}: per trigger pull</li>
 *   <li>{@code reload}, then {@code reload_round} for each further round of a per-round reload,
 *       stretched to the reload's length in ticks</li>
 * </ul>
 * Every clip is optional. Bones named {@code root}, {@code gun}, {@code right_arm} and
 * {@code left_arm}, and bones marked {@code view_only}, only move the first-person view; all other
 * bones also animate in third person.
 */
public final class GunAnimation {
	public static final String ROOT = "root";
	public static final String GUN = "gun";
	public static final String RIGHT_ARM = "right_arm";
	public static final String LEFT_ARM = "left_arm";

	private record Raw(Map<String, BoneDef> bones, Map<String, Clip> clips) {}

	private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(Codec.STRING, BoneDef.CODEC).optionalFieldOf("bones", Map.of()).forGetter(Raw::bones),
		Codec.unboundedMap(Codec.STRING, Clip.CODEC).optionalFieldOf("clips", Map.of()).forGetter(Raw::clips)
	).apply(i, Raw::new));

	public static final Codec<GunAnimation> CODEC = RAW_CODEC.comapFlatMap(GunAnimation::create, a -> new Raw(a.boneDefs, a.clips));

	public static final GunAnimation EMPTY = create(new Raw(Map.of(), Map.of())).getOrThrow();

	private final Map<String, BoneDef> boneDefs;
	private final Map<String, Clip> clips;
	private final String[] names;
	private final BoneDef[] bones;
	private final int[] parents;
	private final boolean[] viewOnly;
	private final Map<String, Integer> indices;
	private final Map<String, Bound> bound;

	/** A clip with its bone names resolved to indices. Bones the file doesn't define are dropped. */
	public record Bound(Clip clip, int[] bones, Clip.Track[] tracks) {
		public float length() {
			return this.clip.length();
		}
	}

	private GunAnimation(Map<String, BoneDef> boneDefs, Map<String, Clip> clips, List<String> order) {
		this.boneDefs = boneDefs;
		this.clips = clips;
		int n = order.size();
		this.names = order.toArray(String[]::new);
		this.bones = new BoneDef[n];
		this.parents = new int[n];
		this.viewOnly = new boolean[n];
		this.indices = new HashMap<>();
		for (int i = 0; i < n; i++) {
			this.indices.put(this.names[i], i);
		}
		for (int i = 0; i < n; i++) {
			BoneDef def = boneDefs.get(this.names[i]);
			this.bones[i] = def;
			this.parents[i] = def.parent().map(this.indices::get).orElse(-1);
			String name = this.names[i];
			this.viewOnly[i] = def.viewOnly() || name.equals(ROOT) || name.equals(GUN) || name.equals(RIGHT_ARM) || name.equals(LEFT_ARM);
		}
		this.bound = new HashMap<>();
		clips.forEach((name, clip) -> {
			List<Integer> ids = new ArrayList<>();
			List<Clip.Track> tracks = new ArrayList<>();
			clip.bones().forEach((bone, track) -> {
				Integer index = this.indices.get(bone);
				if (index != null) {
					ids.add(index);
					tracks.add(track);
				}
			});
			this.bound.put(name, new Bound(clip, ids.stream().mapToInt(Integer::intValue).toArray(), tracks.toArray(Clip.Track[]::new)));
		});
	}

	private static DataResult<GunAnimation> create(Raw raw) {
		Map<String, BoneDef> defs = new LinkedHashMap<>(raw.bones);
		for (Map.Entry<String, BoneDef> entry : defs.entrySet()) {
			Optional<String> parent = entry.getValue().parent();
			if (parent.isPresent() && !defs.containsKey(parent.get())) {
				return DataResult.error(() -> "Bone " + entry.getKey() + " has unknown parent " + parent.get());
			}
		}
		// Parents before children; anything left over is a cycle.
		List<String> order = new ArrayList<>();
		List<String> remaining = new ArrayList<>(defs.keySet());
		boolean progress = true;
		while (!remaining.isEmpty() && progress) {
			progress = false;
			for (int i = 0; i < remaining.size(); i++) {
				String name = remaining.get(i);
				Optional<String> parent = defs.get(name).parent();
				if (parent.isEmpty() || order.contains(parent.get())) {
					order.add(name);
					remaining.remove(i--);
					progress = true;
				}
			}
		}
		if (!remaining.isEmpty()) {
			return DataResult.error(() -> "Bone parents form a loop: " + remaining);
		}
		return DataResult.success(new GunAnimation(Map.copyOf(defs), Map.copyOf(raw.clips), order));
	}

	public int boneCount() {
		return this.names.length;
	}

	/** Index of the named bone, or -1. */
	public int boneIndex(String name) {
		Integer index = this.indices.get(name);
		return index == null ? -1 : index;
	}

	public String boneName(int index) {
		return this.names[index];
	}

	public BoneDef bone(int index) {
		return this.bones[index];
	}

	/** Parent index, or -1. Parents always come before their children. */
	public int parent(int index) {
		return this.parents[index];
	}

	/** Whether the bone only moves the first-person view (root, gun, the arms and bones marked view_only). */
	public boolean isViewOnly(int index) {
		return this.viewOnly[index];
	}

	public Optional<Bound> clip(String name) {
		return Optional.ofNullable(this.bound.get(name));
	}

	public Map<String, Clip> clips() {
		return this.clips;
	}
}
