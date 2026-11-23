package io.github.trini3.tff.platform;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Everything common code needs from the mod loader. Each loader module ships one
 * implementation, found through {@link ServiceLoader} (META-INF/services).
 */
public interface TffPlatform {
	TffPlatform INSTANCE = load(TffPlatform.class);

	static <S> S load(Class<S> service) {
		return ServiceLoader.load(service, service.getClassLoader())
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No loader implementation of " + service.getName()));
	}

	/** Registers a value in a static registry. The supplier may be called right away (Fabric) or later (NeoForge). */
	<R, T extends R> Supplier<T> register(Registry<R> registry, Identifier id, Supplier<T> factory);

	/** Registers a datapack-driven registry whose entries are also sent to clients. */
	<T> void registerSyncedDataRegistry(ResourceKey<? extends Registry<T>> key, Codec<T> codec);

	/** Registers a client-to-server payload. The handler runs on the server thread. */
	<T extends CustomPacketPayload> void registerServerbound(
		CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler);

	/** Registers a server-to-client payload; its handler is registered on the client side. */
	<T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

	void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

	/** Creates a creative tab that does not clash with vanilla tab positions. */
	CreativeModeTab createCreativeTab(Component title, Supplier<ItemStack> icon, Supplier<List<ItemStack>> items);

	/** Creates a menu type for a menu with no extra open data. */
	<T extends AbstractContainerMenu> MenuType<T> createMenuType(BiFunction<Integer, Inventory, T> factory);
}
