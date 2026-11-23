package io.github.trini3.tff.fabric.client;

import io.github.trini3.tff.client.TffClient;
import net.fabricmc.api.ClientModInitializer;

public final class TffFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TffClient.init();
	}
}
