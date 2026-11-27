package io.github.trini3.tff.projectile;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.data.HeadDefinition;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.Vec3;

/**
 * Decides whether a bullet hit a head. Mobs listed in a {@link HeadDefinition} use its boxes. Any
 * other living mob is taken to have its head on top: everything from a little below its eyes up
 * counts. The ender dragon's head part always counts. Mobs lying down, swimming or gliding have
 * their heads where the boxes don't expect them, so they take no headshots.
 */
public final class Headshots {
	/** For mobs with no head file: the head starts this share of the hitbox height below the eyes. */
	static final double DEFAULT_HEAD_BELOW_EYES = 0.12;

	private static Registry<HeadDefinition> cachedRegistry;
	private static List<Holder.Reference<HeadDefinition>> ordered = List.of();

	private Headshots() {}

	/** Whether a bullet that first touched {@code target}'s hitbox at {@code hit} hit its head. */
	public static boolean isHeadshot(RegistryAccess registries, Entity target, Vec3 hit) {
		if (target instanceof EnderDragonPart part) {
			return "head".equals(part.name);
		}
		if (!(target instanceof LivingEntity living) || !headUpright(living.getPose())) {
			return false;
		}
		Optional<HeadDefinition> definition = definition(registries, living.typeHolder());
		return definition.isPresent() ? inBoxes(definition.get(), living, hit) : inTopOfHitbox(living, hit);
	}

	/** Whether {@code hit} is inside one of {@code definition}'s boxes, placed and sized on {@code living}. */
	public static boolean inBoxes(HeadDefinition definition, LivingEntity living, Vec3 hit) {
		boolean babyBoxes = living.isBaby() && definition.hasBabyBoxes();
		// The size the boxes were written for: the type's normal size, or the baby's for baby boxes.
		// Scaling by the hitbox over that covers the scale attribute and crouching too.
		EntityDimensions normal = babyBoxes
			? living.getDimensions(Pose.STANDING).scale(1f / living.getScale())
			: living.getType().getDimensions();
		double scaleX = normal.width() > 0f ? living.getBbWidth() / normal.width() : 1.0;
		double scaleY = normal.height() > 0f ? living.getBbHeight() / normal.height() : 1.0;
		return definition.contains(HeadDefinition.toLocal(hit, living.position(), living.yBodyRot, scaleX, scaleY), living.isBaby());
	}

	/** The rule for mobs with no head file: the head is everything from a little below the eyes up. */
	public static boolean inTopOfHitbox(LivingEntity living, Vec3 hit) {
		double headBottom = living.getEyeHeight() - DEFAULT_HEAD_BELOW_EYES * living.getBbHeight();
		return hit.y - living.getY() >= headBottom - 1e-4;
	}

	private static boolean headUpright(Pose pose) {
		return pose != Pose.SLEEPING && pose != Pose.SWIMMING && pose != Pose.FALL_FLYING && pose != Pose.SPIN_ATTACK && pose != Pose.DYING;
	}

	/**
	 * The head file that lists {@code type}. If several do, the one whose id sorts last wins, so a
	 * datapack can override a file by giving its own a later name, or replace it by using the same id.
	 */
	public static Optional<HeadDefinition> definition(RegistryAccess registries, Holder<EntityType<?>> type) {
		Optional<Registry<HeadDefinition>> registry = registries.lookup(TffRegistries.HEAD);
		if (registry.isEmpty()) {
			return Optional.empty();
		}
		List<Holder.Reference<HeadDefinition>> heads = ordered(registry.get());
		for (int i = heads.size() - 1; i >= 0; i--) {
			if (heads.get(i).value().entities().contains(type)) {
				return Optional.of(heads.get(i).value());
			}
		}
		return Optional.empty();
	}

	private static List<Holder.Reference<HeadDefinition>> ordered(Registry<HeadDefinition> registry) {
		// Datapack registries are rebuilt for each world, so remember the sorted list per registry.
		if (registry != cachedRegistry) {
			List<Holder.Reference<HeadDefinition>> list = new ArrayList<>(registry.listElements().toList());
			list.sort(Comparator.comparing(h -> h.key().identifier().toString()));
			ordered = List.copyOf(list);
			cachedRegistry = registry;
			Tff.LOGGER.debug("{} head definitions", ordered.size());
		}
		return ordered;
	}
}
