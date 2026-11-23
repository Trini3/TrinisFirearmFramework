package io.github.trini3.tff.registry;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.data.GunAttachments;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.platform.TffPlatform;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

public final class TffComponents {
	/** Which gun definition this item uses. Set as a default component when the gun item is registered. */
	public static final Supplier<DataComponentType<ResourceKey<GunDefinition>>> GUN = TffPlatform.INSTANCE.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE, Tff.id("gun"),
		() -> DataComponentType.<ResourceKey<GunDefinition>>builder()
			.persistent(ResourceKey.codec(TffRegistries.GUN))
			.networkSynchronized(ResourceKey.streamCodec(TffRegistries.GUN))
			.build());

	/** Loaded rounds and reload/shot timing. */
	public static final Supplier<DataComponentType<GunState>> GUN_STATE = TffPlatform.INSTANCE.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE, Tff.id("gun_state"),
		() -> DataComponentType.<GunState>builder()
			.persistent(GunState.CODEC)
			.networkSynchronized(GunState.STREAM_CODEC)
			.ignoreSwapAnimation()
			.build());

	/** Fitted attachments (scopes). */
	public static final Supplier<DataComponentType<GunAttachments>> ATTACHMENTS = TffPlatform.INSTANCE.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE, Tff.id("attachments"),
		() -> DataComponentType.<GunAttachments>builder()
			.persistent(GunAttachments.CODEC)
			.networkSynchronized(GunAttachments.STREAM_CODEC)
			.build());

	private TffComponents() {}

	public static void init() {}
}
