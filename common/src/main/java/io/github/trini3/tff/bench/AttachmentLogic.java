package io.github.trini3.tff.bench;

import io.github.trini3.tff.data.AttachmentDefinition;
import io.github.trini3.tff.data.AttachmentSlot;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.gun.GunStats;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/** Rules for fitting and removing attachments. The bench menu and tests both use these. */
public final class AttachmentLogic {
	private AttachmentLogic() {}

	/** The attachment {@code item} would become if fitted to {@code gun} in {@code slot}, if allowed. */
	public static Optional<Holder.Reference<AttachmentDefinition>> fittable(ItemStack gun, AttachmentSlot slot, ItemStack item, HolderLookup.Provider registries) {
		if (!GunItem.isGun(gun) || item.isEmpty()) {
			return Optional.empty();
		}
		Optional<GunDefinition> definition = GunItem.definition(gun, registries);
		if (definition.isEmpty()) {
			return Optional.empty();
		}
		// Several attachments can share an item; pick the one this gun accepts in this slot.
		return TffRegistries.attachmentsFor(registries, item)
			.filter(ref -> ref.value().slot() == slot && definition.get().accepts(slot, ref.key()))
			.findFirst();
	}

	/** Fits {@code item}'s attachment to the gun. Returns false (and changes nothing) if not allowed. */
	public static boolean fit(ItemStack gun, AttachmentSlot slot, ItemStack item, HolderLookup.Provider registries) {
		Optional<Holder.Reference<AttachmentDefinition>> attachment = fittable(gun, slot, item, registries);
		attachment.ifPresent(ref -> GunStats.setAttachments(gun, GunStats.attachments(gun).with(slot, Optional.of(ref.key()))));
		return attachment.isPresent();
	}

	public static void remove(ItemStack gun, AttachmentSlot slot) {
		if (GunItem.isGun(gun)) {
			GunStats.setAttachments(gun, GunStats.attachments(gun).with(slot, Optional.empty()));
		}
	}

	/** The item that represents the attachment fitted in {@code slot}, or empty. */
	public static ItemStack fittedItem(ItemStack gun, AttachmentSlot slot, HolderLookup.Provider registries) {
		return GunStats.fitted(gun, slot, registries).map(a -> new ItemStack(a.item())).orElse(ItemStack.EMPTY);
	}
}
