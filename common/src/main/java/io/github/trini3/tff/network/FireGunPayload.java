package io.github.trini3.tff.network;

import io.github.trini3.tff.Tff;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client asks to fire the main-hand gun, with its aim (like vanilla's use-item packet carries rotation). */
public record FireGunPayload(float yRot, float xRot) implements CustomPacketPayload {
	public static final Type<FireGunPayload> TYPE = new Type<>(Tff.id("fire_gun"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FireGunPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.FLOAT, FireGunPayload::yRot,
		ByteBufCodecs.FLOAT, FireGunPayload::xRot,
		FireGunPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
