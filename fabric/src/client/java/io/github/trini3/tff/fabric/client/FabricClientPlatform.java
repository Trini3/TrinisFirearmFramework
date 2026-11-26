package io.github.trini3.tff.fabric.client;

import com.mojang.serialization.MapCodec;
import io.github.trini3.tff.client.platform.TffClientPlatform;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Fabric implementation of the client loader services. */
public final class FabricClientPlatform implements TffClientPlatform {
	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}

	@Override
	public KeyMapping registerKeyMapping(KeyMapping mapping) {
		return KeyMappingHelper.registerKeyMapping(mapping);
	}

	@Override
	public <E extends Entity> void registerEntityRenderer(EntityType<? extends E> type, EntityRendererProvider<E> provider) {
		// Made accessible by Fabric API's transitive access wideners (Fabric's own registry is deprecated).
		EntityRenderers.register(type, provider);
	}

	@Override
	public void registerItemModel(Identifier id, MapCodec<? extends ItemModel.Unbaked> codec) {
		// The id mappers are made accessible by Fabric API's transitive access wideners.
		ItemModels.ID_MAPPER.put(id, codec);
	}

	@Override
	public void registerConditionalProperty(Identifier id, MapCodec<? extends ConditionalItemModelProperty> codec) {
		ConditionalItemModelProperties.ID_MAPPER.put(id, codec);
	}

	@Override
	public void registerRangeProperty(Identifier id, MapCodec<? extends RangeSelectItemModelProperty> codec) {
		RangeSelectItemModelProperties.ID_MAPPER.put(id, codec);
	}

	@Override
	public void registerSelectProperty(Identifier id, SelectItemModelProperty.Type<?, ?> type) {
		SelectItemModelProperties.ID_MAPPER.put(id, type);
	}

	@Override
	public void registerAssetReloadListener(Identifier id, PreparableReloadListener listener) {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id, listener);
	}

	@Override
	public <M extends AbstractContainerMenu, S extends Screen & MenuAccess<M>> void registerMenuScreen(MenuType<? extends M> type, ScreenFactory<M, S> factory) {
		MenuScreens.register(type, factory::create);
	}
}
