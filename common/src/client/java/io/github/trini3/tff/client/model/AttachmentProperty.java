package io.github.trini3.tff.client.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.tff.data.AttachmentSlot;
import io.github.trini3.tff.gun.GunStats;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * {@code tff:attachment} select property: the id of the attachment fitted in {@code slot}.
 * Gun item models use it inside a {@code minecraft:composite} model to add the scope model:
 * <pre>{@code
 * {"type": "minecraft:select", "property": "tff:attachment", "slot": "scope",
 *  "cases": [{"when": "mymod:scope", "model": {"type": "minecraft:model", "model": "mymod:item/scope_on_gun"}}],
 *  "fallback": {"type": "minecraft:empty"}}
 * }</pre>
 */
public record AttachmentProperty(AttachmentSlot slot) implements SelectItemModelProperty<Identifier> {
	public static final MapCodec<AttachmentProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		AttachmentSlot.CODEC.fieldOf("slot").forGetter(AttachmentProperty::slot)
	).apply(i, AttachmentProperty::new));
	public static final SelectItemModelProperty.Type<AttachmentProperty, Identifier> TYPE = SelectItemModelProperty.Type.create(MAP_CODEC, Identifier.CODEC);

	@Override
	public @Nullable Identifier get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
		return GunStats.attachments(stack).get(this.slot).map(key -> key.identifier()).orElse(null);
	}

	@Override
	public Codec<Identifier> valueCodec() {
		return Identifier.CODEC;
	}

	@Override
	public SelectItemModelProperty.Type<AttachmentProperty, Identifier> type() {
		return TYPE;
	}
}
