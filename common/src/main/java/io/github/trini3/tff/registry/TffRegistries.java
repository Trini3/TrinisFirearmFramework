package io.github.trini3.tff.registry;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.data.AmmoDefinition;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.platform.TffPlatform;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Datapack registries for gun and ammo definitions. Files live at
 * {@code data/<namespace>/tff/gun/<name>.json} and {@code data/<namespace>/tff/ammo/<name>.json}
 * and are synced to clients, so client code can read stats like zoom and fire rate.
 */
public final class TffRegistries {
	public static final ResourceKey<Registry<GunDefinition>> GUN = ResourceKey.createRegistryKey(Tff.id("gun"));
	public static final ResourceKey<Registry<AmmoDefinition>> AMMO = ResourceKey.createRegistryKey(Tff.id("ammo"));

	private TffRegistries() {}

	public static void init() {
		// Ammo first: guns refer to ammo by key.
		TffPlatform.INSTANCE.registerSyncedDataRegistry(AMMO, AmmoDefinition.CODEC);
		TffPlatform.INSTANCE.registerSyncedDataRegistry(GUN, GunDefinition.CODEC);
	}

	public static Optional<GunDefinition> gun(HolderLookup.Provider registries, ResourceKey<GunDefinition> key) {
		return registries.lookup(GUN).flatMap(l -> l.get(key)).map(h -> h.value());
	}

	public static Optional<AmmoDefinition> ammo(HolderLookup.Provider registries, ResourceKey<AmmoDefinition> key) {
		return registries.lookup(AMMO).flatMap(l -> l.get(key)).map(h -> h.value());
	}
}
