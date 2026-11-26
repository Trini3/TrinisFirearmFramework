package io.github.trini3.tff.network;

import io.github.trini3.tff.Tff;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client asks to reload the main-hand gun. */
public record ReloadGunPayload() implements CustomPacketPayload {
	public static final ReloadGunPayload INSTANCE = new ReloadGunPayload();
	public static final Type<ReloadGunPayload> TYPE = new Type<>(Tff.id("reload_gun"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ReloadGunPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
