package io.github.trini3.tff.api;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.platform.TffPlatform;
import io.github.trini3.tff.registry.TffComponents;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.UseEffects;

/**
 * Entry point for content mods. A gun needs one line of Java:
 * <pre>{@code
 * public static final Supplier<GunItem> SHOTGUN = TffApi.registerGun(Identifier.fromNamespaceAndPath("mymod", "shotgun"));
 * }</pre>
 * plus {@code data/mymod/tff/gun/shotgun.json} (stats), an ammo JSON, and the usual item model,
 * texture, lang and sound assets. The gun definition id defaults to the item id.
 */
public final class TffApi {
	private static final Map<Identifier, ProjectileBehavior> BEHAVIORS = new ConcurrentHashMap<>();

	static {
		BEHAVIORS.put(Tff.id("bullet"), ProjectileBehavior.BULLET);
	}

	private TffApi() {}

	/** Registers a gun item whose definition has the same id. */
	public static Supplier<GunItem> registerGun(Identifier id) {
		return registerGun(id, id, UnaryOperator.identity());
	}

	/**
	 * Registers a gun item.
	 *
	 * @param itemId     the item id
	 * @param definition the gun definition id ({@code data/<ns>/tff/gun/<path>.json})
	 * @param properties extra item properties (rarity, custom use effects, ...)
	 */
	public static Supplier<GunItem> registerGun(Identifier itemId, Identifier definition, UnaryOperator<Item.Properties> properties) {
		ResourceKey<GunDefinition> gunKey = ResourceKey.create(TffRegistries.GUN, definition);
		return TffPlatform.INSTANCE.register(BuiltInRegistries.ITEM, itemId, () -> new GunItem(properties.apply(
			new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, itemId))
				.stacksTo(1)
				.component(TffComponents.GUN.get(), gunKey)
				.component(TffComponents.GUN_STATE.get(), GunState.EMPTY)
				// Aiming is "using" the item: slow walk, no sprint, like drawing a bow but lighter.
				.component(net.minecraft.core.component.DataComponents.USE_EFFECTS, new UseEffects(false, true, 0.5f)))));
	}

	/** Registers a projectile behaviour that ammo JSON can select with {@code "behavior": "<id>"}. */
	public static void registerProjectileBehavior(Identifier id, ProjectileBehavior behavior) {
		if (BEHAVIORS.putIfAbsent(id, behavior) != null) {
			throw new IllegalArgumentException("Projectile behavior already registered: " + id);
		}
	}

	public static Optional<ProjectileBehavior> projectileBehavior(Identifier id) {
		return Optional.ofNullable(BEHAVIORS.get(id));
	}
}
