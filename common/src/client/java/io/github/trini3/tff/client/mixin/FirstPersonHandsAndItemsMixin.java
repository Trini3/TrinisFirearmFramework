package io.github.trini3.tff.client.mixin;

import io.github.trini3.tff.client.ScopeView;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the hands and gun while looking through a scope, the same way vanilla does for the spyglass. */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void tff$hideHandsInScope(LocalPlayer player, float partialTicks, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (ScopeView.isScoped(player)) {
			state.isScoping = true;
		}
	}
}
