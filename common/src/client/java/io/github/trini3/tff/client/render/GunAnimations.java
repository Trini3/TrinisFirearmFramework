package io.github.trini3.tff.client.render;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.anim.GunAnimation;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads gun animation files from {@code assets/<namespace>/tff/animations/<path>.json} on every
 * resource reload. Item models look animations up by id when they draw, so the order of this
 * reload and the model reload doesn't matter.
 */
public final class GunAnimations extends SimpleJsonResourceReloadListener<GunAnimation> {
	public static final Identifier ID = Tff.id("gun_animations");
	public static final GunAnimations INSTANCE = new GunAnimations();

	private volatile Map<Identifier, GunAnimation> animations = Map.of();

	private GunAnimations() {
		super(GunAnimation.CODEC, FileToIdConverter.json("tff/animations"));
	}

	@Override
	protected void apply(Map<Identifier, GunAnimation> loaded, ResourceManager manager, ProfilerFiller profiler) {
		this.animations = Map.copyOf(loaded);
		Tff.LOGGER.debug("Loaded {} gun animations", loaded.size());
	}

	/** The animation with this id, or an empty one (no bones, no clips) when it is missing. */
	public static GunAnimation get(Identifier id) {
		return INSTANCE.animations.getOrDefault(id, GunAnimation.EMPTY);
	}
}
