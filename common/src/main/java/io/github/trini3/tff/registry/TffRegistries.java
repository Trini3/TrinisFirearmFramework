package io.github.trini3.tff.registry;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.data.AmmoDefinition;
import io.github.trini3.tff.data.AttachmentDefinition;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.platform.TffPlatform;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * Datapack registries for gun, ammo and attachment definitions. Files live at
 * {@code data/<namespace>/tff/gun/<name>.json}, {@code .../tff/ammo/<name>.json} and
 * {@code .../tff/attachment/<name>.json} and are synced to clients.
 */
public final class TffRegistries {
	public static final ResourceKey<Registry<GunDefinition>> GUN = ResourceKey.createRegistryKey(Tff.id("gun"));
	public static final ResourceKey<Registry<AmmoDefinition>> AMMO = ResourceKey.createRegistryKey(Tff.id("ammo"));
	public static final ResourceKey<Registry<AttachmentDefinition>> ATTACHMENT = ResourceKey.createRegistryKey(Tff.id("attachment"));

	private TffRegistries() {}

	public static void init() {
		// Ammo and attachments first: guns refer to them by key.
		TffPlatform.INSTANCE.registerSyncedDataRegistry(AMMO, AmmoDefinition.CODEC);
		TffPlatform.INSTANCE.registerSyncedDataRegistry(ATTACHMENT, AttachmentDefinition.CODEC);
		TffPlatform.INSTANCE.registerSyncedDataRegistry(GUN, GunDefinition.CODEC);
	}

	public static Optional<GunDefinition> gun(HolderLookup.Provider registries, ResourceKey<GunDefinition> key) {
		return registries.lookup(GUN).flatMap(l -> l.get(key)).map(Holder::value);
	}

	public static Optional<AmmoDefinition> ammo(HolderLookup.Provider registries, ResourceKey<AmmoDefinition> key) {
		return registries.lookup(AMMO).flatMap(l -> l.get(key)).map(Holder::value);
	}

	public static Optional<AttachmentDefinition> attachment(HolderLookup.Provider registries, ResourceKey<AttachmentDefinition> key) {
		return registries.lookup(ATTACHMENT).flatMap(l -> l.get(key)).map(Holder::value);
	}

	/** Every attachment {@code stack} can become. Several guns may share an item (a spyglass) with their own stats. */
	public static Stream<Holder.Reference<AttachmentDefinition>> attachmentsFor(HolderLookup.Provider registries, ItemStack stack) {
		if (stack.isEmpty()) {
			return Stream.empty();
		}
		return registries.lookup(ATTACHMENT).stream().flatMap(l -> l.listElements().filter(h -> h.value().matches(stack)));
	}
}
