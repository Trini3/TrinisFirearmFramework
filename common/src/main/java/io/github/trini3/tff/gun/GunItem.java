package io.github.trini3.tff.gun;

import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.registry.TffComponents;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A gun. Left click fires (handled client side, sent to the server), right click aims down
 * sights by "using" the item, and the reload key reloads. All stats come from the
 * {@link GunDefinition} named by the {@code tff:gun} component.
 */
public class GunItem extends Item {
	private static final int AMMO_BAR_COLOR = 0xD8D8D8;

	public GunItem(Item.Properties properties) {
		super(properties);
	}

	public static boolean isGun(ItemStack stack) {
		return stack.getItem() instanceof GunItem && stack.has(TffComponents.GUN.get());
	}

	public static Optional<GunDefinition> definition(ItemStack stack, HolderLookup.Provider registries) {
		ResourceKey<GunDefinition> key = stack.get(TffComponents.GUN.get());
		return key == null ? Optional.empty() : TffRegistries.gun(registries, key);
	}

	public static GunState state(ItemStack stack) {
		return stack.getOrDefault(TffComponents.GUN_STATE.get(), GunState.EMPTY);
	}

	public static void setState(ItemStack stack, GunState state) {
		stack.set(TffComponents.GUN_STATE.get(), state);
	}

	/** True while {@code entity} is aiming this exact stack. */
	public static boolean isAiming(@Nullable LivingEntity entity, ItemStack stack) {
		return entity != null && entity.isUsingItem() && entity.getUseItem() == stack;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		// NONE keeps vanilla from moving the first-person item; aiming poses come from the item model.
		return ItemUseAnimation.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
		return false;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (owner instanceof Player player) {
			GunLogic.inventoryTick(player, stack, level, slot == EquipmentSlot.MAINHAND);
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return state(stack).capacity() > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		GunState state = state(stack);
		return state.capacity() <= 0 ? 0 : Mth.clamp(Math.round(13f * state.rounds() / state.capacity()), 0, 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return AMMO_BAR_COLOR;
	}
}
