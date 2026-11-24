package io.github.trini3.tff.client.mixin;

import io.github.trini3.tff.gun.GunItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Third-person arm poses for guns, reusing vanilla poses: held like a loaded crossbow, raised
 * like a drawn bow while aiming. Only applies to the hand holding a gun.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At("HEAD"), cancellable = true)
	private static void tff$gunArmPose(Avatar avatar, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		if (hand == InteractionHand.MAIN_HAND && GunItem.isGun(stack) && !avatar.isSwinging()) {
			cir.setReturnValue(GunItem.isAiming(avatar, stack) ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.CROSSBOW_HOLD);
		}
	}
}
