package io.github.trini3.tff.client.mixin;

import io.github.trini3.tff.client.ScopeView;
import io.github.trini3.tff.client.gun.CameraRecoil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies gun recoil every frame, and slows the mouse in proportion to a scope's zoom like the spyglass. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
	private void tff$recoil(CallbackInfo ci) {
		if (this.minecraft.player != null && !this.minecraft.isPaused()) {
			CameraRecoil.apply(this.minecraft.player);
		}
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double tff$scopeYaw(double yaw) {
		return yaw * ScopeView.sensitivity(this.minecraft.player);
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double tff$scopePitch(double pitch) {
		return pitch * ScopeView.sensitivity(this.minecraft.player);
	}
}
