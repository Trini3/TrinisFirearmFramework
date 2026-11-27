package io.github.trini3.tff.client.mixin;

import io.github.trini3.tff.client.ClientClock;
import io.github.trini3.tff.client.gun.CameraRecoil;
import io.github.trini3.tff.client.gun.GunInput;
import io.github.trini3.tff.client.gun.LocalGun;
import io.github.trini3.tff.client.gun.RemoteGuns;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ticks the gun clock and prediction, and routes attack clicks to the gun while one is held. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void tff$tickGuns(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player == null || mc.level == null) {
			LocalGun.tick(null);
			CameraRecoil.reset();
			RemoteGuns.clear();
			return;
		}
		if (!mc.isPaused()) {
			ClientClock.tick();
			LocalGun.tick(mc.player);
			RemoteGuns.tick(mc.level);
		}
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void tff$handleGunInput(CallbackInfo ci) {
		GunInput.handle((Minecraft) (Object) this);
	}

	/** Holding attack with a gun fires instead of mining. */
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void tff$noMiningWithGun(boolean down, CallbackInfo ci) {
		if (GunInput.holdsGun(((Minecraft) (Object) this).player)) {
			ci.cancel();
		}
	}
}
