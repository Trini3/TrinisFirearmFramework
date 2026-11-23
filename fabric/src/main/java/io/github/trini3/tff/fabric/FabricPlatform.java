package io.github.trini3.tff.fabric;

import com.mojang.serialization.Codec;
import io.github.trini3.tff.platform.TffPlatform;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class FabricPlatform implements TffPlatform {
	@Override
	public <R, T extends R> Supplier<T> register(Registry<R> registry, Identifier id, Supplier<T> factory) {
		T value = Registry.register(registry, id, factory.get());
		return () -> value;
	}

	@Override
	public <T> void registerSyncedDataRegistry(ResourceKey<? extends Registry<T>> key, Codec<T> codec) {
		DynamicRegistries.registerSynced(key, codec);
	}

	@Override
	public <T extends CustomPacketPayload> void registerServerbound(
		CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
		PayloadTypeRegistry.serverboundPlay().register(type, codec);
		ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload, context.player()));
	}

	@Override
	public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.clientboundPlay().register(type, codec);
	}

	@Override
	public CreativeModeTab createCreativeTab(Component title, Supplier<ItemStack> icon, Supplier<List<ItemStack>> items) {
		return FabricCreativeModeTab.builder()
			.title(title)
			.icon(icon)
			.displayItems((parameters, output) -> items.get().forEach(output::accept))
			.build();
	}

	@Override
	public <T extends AbstractContainerMenu> MenuType<T> createMenuType(BiFunction<Integer, Inventory, T> factory) {
		return new MenuType<>(factory::apply, FeatureFlags.VANILLA_SET);
	}

	@Override
	public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}
}
