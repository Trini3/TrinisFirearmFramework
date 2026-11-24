package io.github.trini3.tff.client.mixin;

import io.github.trini3.tff.client.ScopeView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a gun scope's overlay with the other camera overlays, and hides the crosshair behind it. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I"))
	private void tff$scopeOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		ScopeView.extract(graphics, deltaTracker);
	}

	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void tff$hideCrosshairInScope(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (ScopeView.isScoped(Minecraft.getInstance().player)) {
			ci.cancel();
		}
	}
}
