package io.github.trini3.tff.data;

import com.mojang.serialization.Codec;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

/** Attachments fitted to a gun stack, stored in the {@code tff:attachments} component. */
public record GunAttachments(Map<AttachmentSlot, ResourceKey<AttachmentDefinition>> fitted) {
	public static final GunAttachments NONE = new GunAttachments(Map.of());

	public static final Codec<GunAttachments> CODEC = Codec.unboundedMap(AttachmentSlot.CODEC, ResourceKey.codec(TffRegistries.ATTACHMENT))
		.xmap(GunAttachments::new, GunAttachments::fitted);

	public static final StreamCodec<RegistryFriendlyByteBuf, GunAttachments> STREAM_CODEC = ByteBufCodecs.<RegistryFriendlyByteBuf, AttachmentSlot, ResourceKey<AttachmentDefinition>, Map<AttachmentSlot, ResourceKey<AttachmentDefinition>>>map(
			n -> new EnumMap<>(AttachmentSlot.class),
			ByteBufCodecs.idMapper(i -> AttachmentSlot.values()[i], AttachmentSlot::ordinal),
			ResourceKey.streamCodec(TffRegistries.ATTACHMENT))
		.map(GunAttachments::new, GunAttachments::fitted);

	public GunAttachments {
		fitted = fitted.isEmpty() ? Map.of() : Map.copyOf(fitted);
	}

	public Optional<ResourceKey<AttachmentDefinition>> get(AttachmentSlot slot) {
		return Optional.ofNullable(this.fitted.get(slot));
	}

	public boolean has(ResourceKey<AttachmentDefinition> attachment) {
		return this.fitted.containsValue(attachment);
	}

	public GunAttachments with(AttachmentSlot slot, Optional<ResourceKey<AttachmentDefinition>> attachment) {
		EnumMap<AttachmentSlot, ResourceKey<AttachmentDefinition>> copy = new EnumMap<>(AttachmentSlot.class);
		copy.putAll(this.fitted);
		attachment.ifPresentOrElse(a -> copy.put(slot, a), () -> copy.remove(slot));
		return new GunAttachments(copy);
	}
}
