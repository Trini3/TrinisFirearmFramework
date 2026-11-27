package io.github.trini3.tff.client.platform;

import com.mojang.serialization.MapCodec;
import io.github.trini3.tff.platform.TffPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Client-only loader services. Implemented per loader, found through ServiceLoader. */
public interface TffClientPlatform {
	TffClientPlatform INSTANCE = TffPlatform.load(TffClientPlatform.class);

	void sendToServer(CustomPacketPayload payload);

	KeyMapping registerKeyMapping(KeyMapping mapping);

	<E extends Entity> void registerEntityRenderer(EntityType<? extends E> type, EntityRendererProvider<E> provider);

	/** Registers an item model type usable as {@code "type"} in {@code assets/<namespace>/items/*.json}. */
	void registerItemModel(Identifier id, MapCodec<? extends ItemModel.Unbaked> codec);

	void registerConditionalProperty(Identifier id, MapCodec<? extends ConditionalItemModelProperty> codec);

	void registerRangeProperty(Identifier id, MapCodec<? extends RangeSelectItemModelProperty> codec);

	void registerSelectProperty(Identifier id, SelectItemModelProperty.Type<?, ?> type);

	/** Registers a listener that runs on every resource (asset) reload. */
	void registerAssetReloadListener(Identifier id, PreparableReloadListener listener);

	<M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerMenuScreen(MenuType<? extends M> type, ScreenFactory<M, S> factory);

	@FunctionalInterface
	interface ScreenFactory<M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> {
		S create(M menu, Inventory inventory, Component title);
	}
}
