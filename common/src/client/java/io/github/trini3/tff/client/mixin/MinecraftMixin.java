package io.github.trini3.tff.client.mixin;

import io.github.trini3.tff.client.GunClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes attack clicks to the gun while one is held. Nothing changes when no gun is in hand. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void tff$handleGunInput(CallbackInfo ci) {
		GunClient.handleInput((Minecraft) (Object) this);
	}

	/** Holding attack with a gun fires instead of mining. */
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void tff$noMiningWithGun(boolean down, CallbackInfo ci) {
		if (GunClient.holdsGun(((Minecraft) (Object) this).player)) {
			ci.cancel();
		}
	}
}
