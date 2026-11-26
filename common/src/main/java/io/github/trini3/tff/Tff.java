package io.github.trini3.tff;

import io.github.trini3.tff.network.TffNetwork;
import io.github.trini3.tff.registry.TffBlocks;
import io.github.trini3.tff.registry.TffComponents;
import io.github.trini3.tff.registry.TffCreativeTab;
import io.github.trini3.tff.registry.TffEntities;
import io.github.trini3.tff.registry.TffMenus;
import io.github.trini3.tff.registry.TffRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-agnostic entry point. Each loader module calls {@link #init()} once during mod setup. */
public final class Tff {
	public static final String MOD_ID = "tff";
	public static final Logger LOGGER = LoggerFactory.getLogger("Trini's Firearm Framework");

	private Tff() {}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void init() {
		TffRegistries.init();
		TffComponents.init();
		TffEntities.init();
		TffBlocks.init();
		TffMenus.init();
		TffCreativeTab.init();
		TffNetwork.init();
	}
}
