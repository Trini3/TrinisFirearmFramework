package io.github.trini3.tff.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * An attachment, loaded from {@code data/<ns>/tff/attachment/<name>.json}. Guns list the
 * attachments they accept per slot. Fitted attachments are drawn by the gun's item model (see
 * the {@code tff:attachment} select property), so they look like any other item model.
 *
 * @param item                the item that is fitted at the attachment bench
 * @param slot                which slot it occupies
 * @param zoom                replaces the gun's aim zoom (field-of-view multiplier) when present
 * @param aimSpreadMultiplier multiplies the gun's aimed spread
 * @param recoilMultiplier    multiplies recoil while aiming
 */
public record AttachmentDefinition(Holder<Item> item, AttachmentSlot slot, Optional<Float> zoom, float aimSpreadMultiplier, float recoilMultiplier) {
	public static final Codec<AttachmentDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
		Item.CODEC.fieldOf("item").forGetter(AttachmentDefinition::item),
		AttachmentSlot.CODEC.fieldOf("slot").forGetter(AttachmentDefinition::slot),
		Codec.floatRange(0.05f, 1f).optionalFieldOf("zoom").forGetter(AttachmentDefinition::zoom),
		Codec.floatRange(0f, 4f).optionalFieldOf("aim_spread_multiplier", 1f).forGetter(AttachmentDefinition::aimSpreadMultiplier),
		Codec.floatRange(0f, 4f).optionalFieldOf("recoil_multiplier", 1f).forGetter(AttachmentDefinition::recoilMultiplier)
	).apply(i, AttachmentDefinition::new));

	public boolean matches(ItemStack stack) {
		return stack.is(this.item);
	}
}
