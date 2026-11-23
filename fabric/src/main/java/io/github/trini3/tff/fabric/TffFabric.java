package io.github.trini3.tff.fabric;

import io.github.trini3.tff.Tff;
import net.fabricmc.api.ModInitializer;

public final class TffFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		Tff.init();
	}
}
