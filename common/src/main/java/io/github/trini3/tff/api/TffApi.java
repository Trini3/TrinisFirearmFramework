package io.github.trini3.tff.api;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.platform.TffPlatform;
import io.github.trini3.tff.registry.TffComponents;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.ItemLike;

/**
 * Entry point for content mods. A gun needs one line of Java:
 * <pre>{@code
 * public static final Supplier<GunItem> RIFLE = TffApi.registerGun(Identifier.fromNamespaceAndPath("mymod", "rifle"));
 * }</pre>
 * plus {@code data/mymod/tff/gun/rifle.json} (stats), an ammo JSON, and assets: a {@code tff:gun}
 * item model ({@code assets/mymod/items/rifle.json}) with its part models, an animation file
 * ({@code assets/mymod/tff/animations/rifle.json}), textures, sounds and lang. The gun definition
 * id defaults to the item id.
 */
public final class TffApi {
	/** Swings would fight the gun's own animations, so guns don't swing on attack or use. */
	public static final SwingAnimation NO_SWING = new SwingAnimation(SwingAnimationType.NONE, 0);
	/** Aiming is "using" the item: half walking speed and no sprinting, lighter than drawing a bow. */
	public static final UseEffects AIM_EFFECTS = new UseEffects(false, true, 0.5f);

	private static final Map<Identifier, ProjectileBehavior> BEHAVIORS = new ConcurrentHashMap<>();
	private static final List<Supplier<? extends ItemLike>> CREATIVE_TAB = new CopyOnWriteArrayList<>();

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
	 * @param definition the gun definition id ({@code data/<namespace>/tff/gun/<path>.json})
	 * @param properties extra item properties (rarity, other use effects, ...)
	 */
	public static Supplier<GunItem> registerGun(Identifier itemId, Identifier definition, UnaryOperator<Item.Properties> properties) {
		ResourceKey<GunDefinition> gunKey = ResourceKey.create(TffRegistries.GUN, definition);
		return addToCreativeTab(TffPlatform.INSTANCE.register(BuiltInRegistries.ITEM, itemId, () -> new GunItem(properties.apply(
			new Item.Properties()
				.setId(ResourceKey.create(Registries.ITEM, itemId))
				.stacksTo(1)
				.component(TffComponents.GUN.get(), gunKey)
				.component(TffComponents.GUN_STATE.get(), GunState.EMPTY)
				.component(DataComponents.USE_EFFECTS, AIM_EFFECTS)
				.component(DataComponents.ATTACK_ANIMATION, NO_SWING)
				.component(DataComponents.INTERACT_ANIMATION, NO_SWING)))));
	}

	/**
	 * Registers a plain item (ammo, a scope, ...) and lists it in the Firearms creative tab. Ammo and
	 * attachments are ordinary items; their JSON definitions point at them.
	 */
	public static Supplier<Item> registerItem(Identifier id, UnaryOperator<Item.Properties> properties) {
		return addToCreativeTab(TffPlatform.INSTANCE.register(BuiltInRegistries.ITEM, id,
			() -> new Item(properties.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))))));
	}

	/** Lists an item in the Firearms creative tab (in call order). */
	public static <T extends Supplier<? extends ItemLike>> T addToCreativeTab(T item) {
		CREATIVE_TAB.add(item);
		return item;
	}

	public static List<Supplier<? extends ItemLike>> creativeTabItems() {
		return List.copyOf(CREATIVE_TAB);
	}

	/** Registers a projectile behaviour that ammo JSON can pick with {@code "behavior": "<id>"}. */
	public static void registerProjectileBehavior(Identifier id, ProjectileBehavior behavior) {
		if (BEHAVIORS.putIfAbsent(id, behavior) != null) {
			throw new IllegalArgumentException("Projectile behavior already registered: " + id);
		}
	}

	public static Optional<ProjectileBehavior> projectileBehavior(Identifier id) {
		return Optional.ofNullable(BEHAVIORS.get(id));
	}
}
