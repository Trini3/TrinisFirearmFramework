package io.github.trini3.tff.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * An attachment, loaded from {@code data/<namespace>/tff/attachment/<name>.json}. Guns list the
 * attachments they accept per slot. A fitted attachment is drawn by the gun's item model (a part
 * with a matching {@code attachment} field), so it is ordinary item geometry.
 *
 * @param item                the item fitted at the attachment bench
 * @param slot                which slot it takes
 * @param zoom                replaces the gun's aim zoom (field of view multiplier) when present
 * @param aimSpreadMultiplier multiplies the gun's aimed spread
 * @param recoilMultiplier    multiplies recoil while aiming
 * @param overlay             optional spyglass-style scope view: a texture id resolved as
 *                            {@code <namespace>:textures/<path>.png}. While aiming in first person
 *                            it covers the screen, the gun is hidden and mouse speed follows the
 *                            zoom. {@code minecraft:misc/spyglass_scope} reuses the spyglass view.
 * @param reticle             optional texture drawn over the overlay on the same square
 */
public record AttachmentDefinition(Holder<Item> item, AttachmentSlot slot, Optional<Float> zoom, float aimSpreadMultiplier,
								   float recoilMultiplier, Optional<Identifier> overlay, Optional<Identifier> reticle) {
	public static final Codec<AttachmentDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
		Item.CODEC.fieldOf("item").forGetter(AttachmentDefinition::item),
		AttachmentSlot.CODEC.fieldOf("slot").forGetter(AttachmentDefinition::slot),
		Codec.floatRange(0.05f, 1f).optionalFieldOf("zoom").forGetter(AttachmentDefinition::zoom),
		Codec.floatRange(0f, 4f).optionalFieldOf("aim_spread_multiplier", 1f).forGetter(AttachmentDefinition::aimSpreadMultiplier),
		Codec.floatRange(0f, 4f).optionalFieldOf("recoil_multiplier", 1f).forGetter(AttachmentDefinition::recoilMultiplier),
		Identifier.CODEC.optionalFieldOf("overlay").forGetter(AttachmentDefinition::overlay),
		Identifier.CODEC.optionalFieldOf("reticle").forGetter(AttachmentDefinition::reticle)
	).apply(i, AttachmentDefinition::new));

	public boolean matches(ItemStack stack) {
		return stack.is(this.item);
	}
}
