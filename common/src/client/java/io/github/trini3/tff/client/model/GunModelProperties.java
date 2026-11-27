package io.github.trini3.tff.client.model;

import com.mojang.serialization.MapCodec;
import io.github.trini3.tff.Tff;
import io.github.trini3.tff.client.platform.TffClientPlatform;
import io.github.trini3.tff.gun.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item model properties for guns, for vanilla {@code condition}, {@code range_dispatch} and
 * {@code select} models in {@code assets/<ns>/items/<gun>.json}. The {@code tff:gun} model already
 * animates the gun; these are for choosing between models, for example a different inventory icon
 * while the gun is empty.
 * <ul>
 *   <li>{@code tff:reloading} (condition): a reload is in progress</li>
 *   <li>{@code tff:aiming} (condition): the holder is aiming down sights with this gun</li>
 *   <li>{@code tff:reload_progress} (range_dispatch, 0 to 1): progress of the current reload step</li>
 *   <li>{@code tff:ammo} (range_dispatch): rounds loaded</li>
 *   <li>{@code tff:attachment} (select, with {@code slot}): id of the fitted attachment, see {@link AttachmentProperty}</li>
 * </ul>
 */
public final class GunModelProperties {
	private GunModelProperties() {}

	public static void init() {
		TffClientPlatform.INSTANCE.registerConditionalProperty(Tff.id("reloading"), Reloading.MAP_CODEC);
		TffClientPlatform.INSTANCE.registerConditionalProperty(Tff.id("aiming"), Aiming.MAP_CODEC);
		TffClientPlatform.INSTANCE.registerRangeProperty(Tff.id("reload_progress"), ReloadProgress.MAP_CODEC);
		TffClientPlatform.INSTANCE.registerRangeProperty(Tff.id("ammo"), Ammo.MAP_CODEC);
		TffClientPlatform.INSTANCE.registerSelectProperty(Tff.id("attachment"), AttachmentProperty.TYPE);
	}

	private static long gameTime(@Nullable ClientLevel level) {
		ClientLevel l = level != null ? level : Minecraft.getInstance().level;
		return l == null ? 0L : l.getGameTime();
	}

	public record Reloading() implements ConditionalItemModelProperty {
		public static final MapCodec<Reloading> MAP_CODEC = MapCodec.unit(new Reloading());

		@Override
		public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
			return GunItem.state(stack).reloading();
		}

		@Override
		public MapCodec<Reloading> type() {
			return MAP_CODEC;
		}
	}

	public record Aiming() implements ConditionalItemModelProperty {
		public static final MapCodec<Aiming> MAP_CODEC = MapCodec.unit(new Aiming());

		@Override
		public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
			return GunItem.isAiming(owner, stack);
		}

		@Override
		public MapCodec<Aiming> type() {
			return MAP_CODEC;
		}
	}

	public record ReloadProgress() implements RangeSelectItemModelProperty {
		public static final MapCodec<ReloadProgress> MAP_CODEC = MapCodec.unit(new ReloadProgress());

		@Override
		public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
			return GunItem.state(stack).reloadProgress(gameTime(level));
		}

		@Override
		public MapCodec<ReloadProgress> type() {
			return MAP_CODEC;
		}
	}

	public record Ammo() implements RangeSelectItemModelProperty {
		public static final MapCodec<Ammo> MAP_CODEC = MapCodec.unit(new Ammo());

		@Override
		public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
			return GunItem.state(stack).rounds();
		}

		@Override
		public MapCodec<Ammo> type() {
			return MAP_CODEC;
		}
	}
}
