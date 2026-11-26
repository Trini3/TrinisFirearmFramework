package io.github.trini3.tff.client.mixin;

import io.github.trini3.tff.client.ScopeView;
import io.github.trini3.tff.gun.GunItem;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * First-person hands with a gun: both arms belong to the gun model, so the off-hand isn't drawn;
 * starting to aim doesn't replay the equip dip; and looking through a scope hides the hands the
 * same way vanilla does for the spyglass.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
	@Shadow
	private ItemStack mainHandItem;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void tff$hideHandsInScope(LocalPlayer player, float partialTicks, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (ScopeView.isScoped(player)) {
			state.isScoping = true;
		}
	}

	@Inject(method = "evaluateWhichHandsToRender", at = @At("HEAD"), cancellable = true)
	private static void tff$gunTakesBothHands(LocalPlayer player, CallbackInfoReturnable<FirstPersonHandsAndItemsRenderState.HandRenderSelection> cir) {
		if (GunItem.isGun(player.getMainHandItem())) {
			cir.setReturnValue(FirstPersonHandsAndItemsRenderState.HandRenderSelection.RENDER_MAIN_HAND_ONLY);
		}
	}

	@Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
	private void tff$noDipWhenAiming(InteractionHand hand, CallbackInfo ci) {
		if (hand == InteractionHand.MAIN_HAND && GunItem.isGun(this.mainHandItem)) {
			ci.cancel();
		}
	}
}
