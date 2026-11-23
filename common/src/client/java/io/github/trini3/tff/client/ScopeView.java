package io.github.trini3.tff.client;

import io.github.trini3.tff.data.AttachmentDefinition;
import io.github.trini3.tff.data.AttachmentSlot;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.gun.GunStats;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Spyglass-style scope view. While a gun with an overlay scope is aimed in first person the gun
 * is hidden, the scope texture is drawn over the screen the same way vanilla draws the spyglass
 * (a plain textured HUD blit, so shader packs treat it like the spyglass), the vanilla crosshair
 * is hidden, and mouse speed scales with the zoom.
 */
public final class ScopeView {
	private static float scale = 0.5f;

	private ScopeView() {}

	/** The fitted scope, when {@code player} is aiming a gun whose scope has an overlay in first person. */
	public static Optional<AttachmentDefinition> activeScope(@Nullable LocalPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		if (player == null || !player.isUsingItem() || !mc.options.getCameraType().isFirstPerson()) {
			return Optional.empty();
		}
		ItemStack stack = player.getUseItem();
		if (!GunItem.isGun(stack)) {
			return Optional.empty();
		}
		return GunStats.fitted(stack, AttachmentSlot.SCOPE, player.registryAccess()).filter(a -> a.overlay().isPresent());
	}

	public static boolean isScoped(@Nullable LocalPlayer player) {
		return activeScope(player).isPresent();
	}

	/** Mouse speed multiplier: the zoom itself, so the view moves at the same speed on screen. */
	public static double sensitivity(@Nullable LocalPlayer player) {
		return activeScope(player).map(a -> (double) a.zoom().orElse(1f)).orElse(1.0);
	}

	/** Draws the overlay, copying vanilla's spyglass layout: a square that grows in, black around it. */
	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Optional<AttachmentDefinition> scope = activeScope(Minecraft.getInstance().player);
		if (scope.isEmpty()) {
			scale = 0.5f;
			return;
		}
		scale = Mth.lerp(0.5f * deltaTracker.getGameTimeDeltaTicks(), scale, 1.125f);
		Identifier texture = scope.get().overlay().orElseThrow().withPath(p -> "textures/" + p + ".png");
		int size = Mth.floor(Math.min(graphics.guiWidth(), graphics.guiHeight()) * scale);
		int left = (graphics.guiWidth() - size) / 2;
		int top = (graphics.guiHeight() - size) / 2;
		int right = left + size;
		int bottom = top + size;
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, left, top, 0f, 0f, size, size, size, size);
		graphics.fill(RenderPipelines.GUI, 0, bottom, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
		graphics.fill(RenderPipelines.GUI, 0, 0, graphics.guiWidth(), top, 0xFF000000);
		graphics.fill(RenderPipelines.GUI, 0, top, left, bottom, 0xFF000000);
		graphics.fill(RenderPipelines.GUI, right, top, graphics.guiWidth(), bottom, 0xFF000000);
	}
}
