package io.github.trini3.tff.client;

import io.github.trini3.tff.client.bench.AttachmentBenchScreen;
import io.github.trini3.tff.client.gun.GunInput;
import io.github.trini3.tff.client.model.GunModelProperties;
import io.github.trini3.tff.client.platform.TffClientPlatform;
import io.github.trini3.tff.client.render.GunAnimations;
import io.github.trini3.tff.client.render.GunItemModel;
import io.github.trini3.tff.registry.TffEntities;
import io.github.trini3.tff.registry.TffMenus;
import net.minecraft.client.renderer.entity.NoopRenderer;

/** Loader-agnostic client entry point. */
public final class TffClient {
	private TffClient() {}

	public static void init() {
		GunInput.init();
		GunModelProperties.init();
		TffClientPlatform.INSTANCE.registerItemModel(GunItemModel.ID, GunItemModel.Unbaked.MAP_CODEC);
		TffClientPlatform.INSTANCE.registerAssetReloadListener(GunAnimations.ID, GunAnimations.INSTANCE);
		// Bullets travel several blocks per tick, so they are not drawn; impacts and smoke are particles.
		TffClientPlatform.INSTANCE.registerEntityRenderer(TffEntities.BULLET.get(), NoopRenderer::new);
		TffClientPlatform.INSTANCE.registerMenuScreen(TffMenus.ATTACHMENT_BENCH.get(), AttachmentBenchScreen::new);
	}
}
