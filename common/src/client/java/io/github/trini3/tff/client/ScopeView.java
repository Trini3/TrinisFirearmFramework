package io.github.trini3.tff.client;

import io.github.trini3.tff.client.gun.LocalGun;
import io.github.trini3.tff.data.AttachmentDefinition;
import io.github.trini3.tff.data.AttachmentSlot;
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
 * Spyglass-style scope view. Once a gun with an overlay scope has been raised to the eye in first
 * person, the gun is hidden, the scope texture is drawn over the screen the same way vanilla draws
 * the spyglass (a plain textured HUD blit, so shader packs treat it like the spyglass), and mouse
 * speed scales with the zoom. The crosshair also hides while aiming down iron sights.
 */
public final class ScopeView {
	/** Aim blend from which the scope overlay replaces the gun. */
	private static final float SCOPE_IN = 0.9f;
	/** Aim blend from which the crosshair hides behind the sights. */
	private static final float CROSSHAIR_OUT = 0.5f;

	private static float scale = 0.5f;

	private ScopeView() {}

	/** The fitted scope, when the player has raised a gun whose scope has an overlay, in first person. */
	public static Optional<AttachmentDefinition> activeScope(@Nullable LocalPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		if (player == null || !mc.options.getCameraType().isFirstPerson() || !LocalGun.isAiming(player) || LocalGun.aimWeight() < SCOPE_IN) {
			return Optional.empty();
		}
		ItemStack stack = player.getUseItem();
		return GunStats.fitted(stack, AttachmentSlot.SCOPE, player.registryAccess()).filter(a -> a.overlay().isPresent());
	}

	public static boolean isScoped(@Nullable LocalPlayer player) {
		return activeScope(player).isPresent();
	}

	/** Whether the crosshair should hide: looking through a scope or down the sights. */
	public static boolean hidesCrosshair(@Nullable LocalPlayer player) {
		return player != null && Minecraft.getInstance().options.getCameraType().isFirstPerson() && LocalGun.isAiming(player)
			&& LocalGun.aimWeight() >= CROSSHAIR_OUT;
	}

	/** Mouse speed multiplier: the zoom itself, so the view moves at the same speed on screen. */
	public static double sensitivity(@Nullable LocalPlayer player) {
		return activeScope(player).map(a -> (double) a.zoom().orElse(1f)).orElse(1.0);
	}

	private static Identifier texture(Identifier id) {
		return id.withPath(p -> "textures/" + p + ".png");
	}

	/** Draws the overlay (and the reticle on top), copying vanilla's spyglass layout: a square that grows in, black around it. */
	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Optional<AttachmentDefinition> scope = activeScope(Minecraft.getInstance().player);
		if (scope.isEmpty()) {
			scale = 0.5f;
			return;
		}
		scale = Mth.lerp(0.5f * deltaTracker.getGameTimeDeltaTicks(), scale, 1.125f);
		Identifier texture = texture(scope.get().overlay().orElseThrow());
		int size = Mth.floor(Math.min(graphics.guiWidth(), graphics.guiHeight()) * scale);
		int left = (graphics.guiWidth() - size) / 2;
		int top = (graphics.guiHeight() - size) / 2;
		int right = left + size;
		int bottom = top + size;
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, left, top, 0f, 0f, size, size, size, size);
		scope.get().reticle().ifPresent(reticle ->
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture(reticle), left, top, 0f, 0f, size, size, size, size));
		graphics.fill(RenderPipelines.GUI, 0, bottom, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
		graphics.fill(RenderPipelines.GUI, 0, 0, graphics.guiWidth(), top, 0xFF000000);
		graphics.fill(RenderPipelines.GUI, 0, top, left, bottom, 0xFF000000);
		graphics.fill(RenderPipelines.GUI, right, top, graphics.guiWidth(), bottom, 0xFF000000);
	}
}
