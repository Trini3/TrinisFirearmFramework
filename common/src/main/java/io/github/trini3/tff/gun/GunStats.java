package io.github.trini3.tff.gun;

import io.github.trini3.tff.data.AttachmentDefinition;
import io.github.trini3.tff.data.AttachmentSlot;
import io.github.trini3.tff.data.GunAttachments;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.registry.TffComponents;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/** A gun's numbers after its fitted attachments are applied. Used by both server and client. */
public final class GunStats {
	private GunStats() {}

	public static GunAttachments attachments(ItemStack stack) {
		return stack.getOrDefault(TffComponents.ATTACHMENTS.get(), GunAttachments.NONE);
	}

	public static void setAttachments(ItemStack stack, GunAttachments attachments) {
		if (attachments.fitted().isEmpty()) {
			stack.remove(TffComponents.ATTACHMENTS.get());
		} else {
			stack.set(TffComponents.ATTACHMENTS.get(), attachments);
		}
	}

	public static Optional<AttachmentDefinition> fitted(ItemStack stack, AttachmentSlot slot, HolderLookup.Provider registries) {
		return attachments(stack).get(slot).flatMap(key -> TffRegistries.attachment(registries, key));
	}

	/** Field-of-view multiplier while aiming: the scope's zoom if one is fitted, else the gun's own. */
	public static float aimZoom(GunDefinition gun, ItemStack stack, HolderLookup.Provider registries) {
		return fitted(stack, AttachmentSlot.SCOPE, registries).flatMap(AttachmentDefinition::zoom).orElse(gun.aim().zoom());
	}

	public static float spread(GunDefinition gun, ItemStack stack, HolderLookup.Provider registries, boolean aiming) {
		if (!aiming) {
			return gun.spread().hip();
		}
		float multiplier = fitted(stack, AttachmentSlot.SCOPE, registries).map(AttachmentDefinition::aimSpreadMultiplier).orElse(1f);
		return gun.spread().aim() * multiplier;
	}

	/** Recoil multiplier: 1 from the hip; the gun's aim factor times the scope's multiplier while aiming. */
	public static float recoilFactor(GunDefinition gun, ItemStack stack, HolderLookup.Provider registries, boolean aiming) {
		if (!aiming) {
			return 1f;
		}
		float multiplier = fitted(stack, AttachmentSlot.SCOPE, registries).map(AttachmentDefinition::recoilMultiplier).orElse(1f);
		return gun.aim().recoilFactor() * multiplier;
	}
}
