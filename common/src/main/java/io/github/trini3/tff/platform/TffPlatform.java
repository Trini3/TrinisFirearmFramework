package io.github.trini3.tff.platform;

import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Everything common code needs from the mod loader. Each loader module ships one implementation,
 * found through {@link ServiceLoader} ({@code META-INF/services}). Keep this small: anything vanilla
 * can do directly belongs in common code, not here.
 */
public interface TffPlatform {
	TffPlatform INSTANCE = load(TffPlatform.class);

	static <S> S load(Class<S> service) {
		return ServiceLoader.load(service, service.getClassLoader())
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No loader implementation of " + service.getName()));
	}

	/** Registers a value in a static registry. The factory may run right away (Fabric) or later (NeoForge). */
	<R, T extends R> Supplier<T> register(Registry<R> registry, Identifier id, Supplier<T> factory);

	/** Registers a datapack registry whose entries are also sent to clients. */
	<T> void registerSyncedDataRegistry(ResourceKey<? extends Registry<T>> key, Codec<T> codec);

	/** Registers a client-to-server payload. The handler runs on the server thread. */
	<T extends CustomPacketPayload> void registerServerbound(
		CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler);

	/** Creates a creative tab that does not clash with vanilla tab positions. */
	CreativeModeTab createCreativeTab(Component title, Supplier<ItemStack> icon, Supplier<List<ItemStack>> items);

	/** Creates a menu type for a menu with no extra opening data. */
	<T extends AbstractContainerMenu> MenuType<T> createMenuType(BiFunction<Integer, Inventory, T> factory);

	/** The folder config files go in ({@code config/} in the game folder). */
	Path configDir();
}
