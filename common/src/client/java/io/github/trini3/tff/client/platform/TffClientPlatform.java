package io.github.trini3.tff.client.platform;

import com.mojang.serialization.MapCodec;
import io.github.trini3.tff.platform.TffPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Client-only loader services. Implemented per loader, found through ServiceLoader. */
public interface TffClientPlatform {
	TffClientPlatform INSTANCE = TffPlatform.load(TffClientPlatform.class);

	void sendToServer(CustomPacketPayload payload);

	KeyMapping registerKeyMapping(KeyMapping mapping);

	<E extends Entity> void registerEntityRenderer(EntityType<? extends E> type, EntityRendererProvider<E> provider);

	void registerConditionalProperty(Identifier id, MapCodec<? extends ConditionalItemModelProperty> codec);

	void registerRangeProperty(Identifier id, MapCodec<? extends RangeSelectItemModelProperty> codec);
}
