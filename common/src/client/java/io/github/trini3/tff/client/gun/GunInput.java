package io.github.trini3.tff.client.gun;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.trini3.tff.Tff;
import io.github.trini3.tff.client.platform.TffClientPlatform;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.gun.GunStats;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Gun controls: left click fires (held for automatic guns), the reload key (R) reloads, right click
 * aims (vanilla item use). Runs at the start of vanilla's keybind handling, so attack clicks are
 * taken here before vanilla turns them into block breaking or melee, and also while aiming, when
 * vanilla would throw them away.
 */
public final class GunInput {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Tff.id("firearms"));
	public static KeyMapping reloadKey;

	private GunInput() {}

	public static void init() {
		reloadKey = TffClientPlatform.INSTANCE.registerKeyMapping(
			new KeyMapping("key.tff.reload", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));
	}

	public static boolean holdsGun(@Nullable LocalPlayer player) {
		return player != null && GunItem.isGun(player.getMainHandItem());
	}

	/** Called from the start of {@code Minecraft#handleKeybinds}. */
	public static void handle(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || player.isSpectator() || !holdsGun(player)) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		Optional<GunDefinition> maybeGun = GunItem.definition(stack, player.registryAccess());
		if (maybeGun.isEmpty()) {
			return;
		}
		GunDefinition gun = maybeGun.get();
		while (reloadKey.consumeClick()) {
			LocalGun.tryReload(player, stack, gun);
		}
		boolean pulled = false;
		while (mc.options.keyAttack.consumeClick()) {
			pulled = true;
		}
		boolean held = gun.fireMode() == GunDefinition.FireMode.AUTO && mc.options.keyAttack.isDown() && mc.mouseHandler.isMouseGrabbed();
		if (pulled || held) {
			LocalGun.tryFire(player, stack, gun);
		}
	}

	/** Field of view multiplier while aiming down sights (first person only). */
	public static float aimFovMultiplier(LocalPlayer player) {
		if (!LocalGun.isAiming(player)) {
			return 1f;
		}
		ItemStack stack = player.getUseItem();
		return GunItem.definition(stack, player.registryAccess()).map(gun -> GunStats.aimZoom(gun, stack, player.registryAccess())).orElse(1f);
	}
}
