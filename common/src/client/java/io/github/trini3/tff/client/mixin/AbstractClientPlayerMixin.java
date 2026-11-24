package io.github.trini3.tff.client.mixin;

import io.github.trini3.tff.client.GunClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Aiming down sights zooms the camera, like the spyglass but by the gun's own amount. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void tff$aimZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		if (firstPerson && (Object) this instanceof LocalPlayer local) {
			float zoom = GunClient.aimFovMultiplier(local);
			if (zoom != 1f) {
				cir.setReturnValue(cir.getReturnValueF() * zoom);
			}
		}
	}
}
