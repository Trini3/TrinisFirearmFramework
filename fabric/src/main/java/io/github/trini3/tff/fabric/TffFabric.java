package io.github.trini3.tff.fabric;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.config.TffConfig;
import io.github.trini3.tff.testgun.TestShotgun;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class TffFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		Tff.init();
		TestShotgun.init();
		// Pick up config edits each time a world or server starts.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> TffConfig.load());
	}
}
