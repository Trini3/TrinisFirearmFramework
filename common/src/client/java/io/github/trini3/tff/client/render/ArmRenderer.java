package io.github.trini3.tff.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Draws one of the player's arms as a layer of the gun's item model, through the player
 * renderer's own first-person arm ({@link AvatarRenderer#renderRightHand}), so skins, slim arms,
 * sleeves and shader packs all work as with vanilla's empty hand.
 * <p>
 * The arm is lined up with its animation bone: the middle of the hand sits on the bone's pivot
 * and, with no rotation, the arm points straight ahead (hand toward -Z, shoulder toward +Z) with
 * the back of the forearm facing up, like holding an arm out in front of you. Rotating the bone
 * then swings the arm around the hand.
 */
public final class ArmRenderer implements SpecialModelRenderer<ArmRenderer.Arm> {
	public static final ArmRenderer INSTANCE = new ArmRenderer();

	/** Hand centre height in the arm part's space: 1 pixel up from the end of the arm. */
	private static final float HAND_Y = 9f;
	private static final Matrix4fc RIGHT_WIDE = alignment(true, false);
	private static final Matrix4fc RIGHT_SLIM = alignment(true, true);
	private static final Matrix4fc LEFT_WIDE = alignment(false, false);
	private static final Matrix4fc LEFT_SLIM = alignment(false, true);

	/**
	 * One arm to draw.
	 *
	 * @param renderer the holder's player renderer (its model has the right arm shape)
	 * @param skin     skin texture
	 * @param right    which arm
	 * @param slim     whether the skin uses slim arms
	 * @param sleeve   whether the sleeve layer is shown
	 */
	public record Arm(AvatarRenderer<?> renderer, Identifier skin, boolean right, boolean slim, boolean sleeve) {}

	private ArmRenderer() {}

	/**
	 * Undoes the arm part's own pose (offset and small outward tilt that vanilla gives it in
	 * {@code renderHand}), moves the hand centre to the origin, then turns the hanging arm to
	 * point forward in item model space (y up, model facing -Z): (x, y, z) to (-x, -z, -y).
	 */
	private static Matrix4fc alignment(boolean right, boolean slim) {
		float side = right ? 1f : -1f;
		float pivotX = -5f * side;
		// Arm boxes: wide right (-3..1), slim right (-2..1); left arms mirror them.
		float handX = (slim ? -0.5f : -1f) * side;
		float zRot = 0.1f * side;
		return new Matrix4f(
			-1f, 0f, 0f, 0f,
			0f, 0f, -1f, 0f,
			0f, -1f, 0f, 0f,
			0f, 0f, 0f, 1f)
			.translate(-handX / 16f, -HAND_Y / 16f, 0f)
			.rotateZ(-zRot)
			.translate(-pivotX / 16f, -2f / 16f, 0f);
	}

	@Override
	public void submit(@Nullable Arm arm, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
					   boolean hasFoil, int outlineColor) {
		if (arm == null) {
			return;
		}
		poseStack.pushPose();
		poseStack.mulPose(arm.right ? (arm.slim ? RIGHT_SLIM : RIGHT_WIDE) : (arm.slim ? LEFT_SLIM : LEFT_WIDE));
		if (arm.right) {
			arm.renderer.renderRightHand(poseStack, collector, lightCoords, arm.skin, arm.sleeve);
		} else {
			arm.renderer.renderLeftHand(poseStack, collector, lightCoords, arm.skin, arm.sleeve);
		}
		poseStack.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
	}

	@Override
	public @Nullable Arm extractArgument(ItemStack stack) {
		return null;
	}
}
