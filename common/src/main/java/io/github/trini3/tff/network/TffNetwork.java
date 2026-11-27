package io.github.trini3.tff.network;

import io.github.trini3.tff.gun.GunLogic;
import io.github.trini3.tff.platform.TffPlatform;
import net.minecraft.util.Mth;

public final class TffNetwork {
	private TffNetwork() {}

	public static void init() {
		TffPlatform.INSTANCE.registerServerbound(FireGunPayload.TYPE, FireGunPayload.CODEC, (payload, player) -> {
			if (Float.isFinite(payload.yRot()) && Float.isFinite(payload.xRot())) {
				GunLogic.tryFire(player, Mth.wrapDegrees(payload.yRot()), Mth.clamp(payload.xRot(), -90f, 90f));
			}
		});
		TffPlatform.INSTANCE.registerServerbound(ReloadGunPayload.TYPE, ReloadGunPayload.CODEC, (payload, player) -> GunLogic.startReload(player));
	}
}
