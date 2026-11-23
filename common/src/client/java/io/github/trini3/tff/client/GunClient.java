package io.github.trini3.tff.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.trini3.tff.Tff;
import io.github.trini3.tff.client.platform.TffClientPlatform;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.gun.GunMath;
import io.github.trini3.tff.network.FireGunPayload;
import io.github.trini3.tff.network.ReloadGunPayload;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Client input for guns: left click fires, the reload key reloads. Runs at the start of
 * vanilla's keybind handling, so attack clicks are consumed here before vanilla turns them into
 * block breaking or melee (and also while aiming, when vanilla would drop them).
 */
public final class GunClient {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Tff.id("firearms"));
	public static KeyMapping reloadKey;

	/** Last local shot, for client-side fire-rate gating; the server re-checks everything. */
	private static long lastLocalShot = Long.MIN_VALUE / 2;

	private GunClient() {}

	public static void init() {
		reloadKey = TffClientPlatform.INSTANCE.registerKeyMapping(
			new KeyMapping("key.tff.reload", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));
	}

	public static boolean holdsGun(@Nullable LocalPlayer player) {
		return player != null && GunItem.isGun(player.getMainHandItem());
	}

	/** Called from the start of {@code Minecraft#handleKeybinds}. */
	public static void handleInput(Minecraft mc) {
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
			TffClientPlatform.INSTANCE.sendToServer(ReloadGunPayload.INSTANCE);
		}

		boolean wantsFire = false;
		while (mc.options.keyAttack.consumeClick()) {
			wantsFire = true;
		}
		if (gun.fireMode() == GunDefinition.FireMode.AUTO && mc.options.keyAttack.isDown() && mc.mouseHandler.isMouseGrabbed()) {
			wantsFire = true;
		}
		if (!wantsFire) {
			return;
		}

		long now = player.level().getGameTime();
		if (!GunMath.cooledDown(now, lastLocalShot, gun.fireInterval(), 0)) {
			return;
		}
		lastLocalShot = now;
		TffClientPlatform.INSTANCE.sendToServer(new FireGunPayload(player.getYRot(), player.getXRot()));

		GunState state = GunItem.state(stack);
		boolean blocked = state.reloading() && gun.reload().mode() == GunDefinition.ReloadMode.MAGAZINE;
		if (state.rounds() > 0 && !blocked) {
			applyRecoil(player, gun, GunItem.isAiming(player, stack));
		}
	}

	/** A simple camera kick: up by the pitch, a random nudge sideways. */
	private static void applyRecoil(LocalPlayer player, GunDefinition gun, boolean aiming) {
		float factor = aiming ? gun.aim().recoilFactor() : 1f;
		float pitch = gun.recoil().pitch() * factor;
		float yaw = gun.recoil().yaw() * factor * (player.getRandom().nextFloat() * 2f - 1f);
		player.setXRot(Math.clamp(player.getXRot() - pitch, -90f, 90f));
		player.setYRot(player.getYRot() + yaw);
	}

	/** Field-of-view multiplier while aiming down sights (first person only). */
	public static float aimFovMultiplier(LocalPlayer player) {
		if (!player.isUsingItem()) {
			return 1f;
		}
		ItemStack stack = player.getUseItem();
		if (!GunItem.isGun(stack)) {
			return 1f;
		}
		return GunItem.definition(stack, player.registryAccess()).map(g -> g.aim().zoom()).orElse(1f);
	}
}
