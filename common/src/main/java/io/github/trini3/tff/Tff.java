package io.github.trini3.tff;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-agnostic entry point. Loader modules call {@link #init()} once during mod setup. */
public final class Tff {
	public static final String MOD_ID = "tff";
	public static final Logger LOGGER = LoggerFactory.getLogger("Trini's Firearm Framework");

	private Tff() {}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void init() {
		LOGGER.info("Trini's Firearm Framework initialising");
	}
}
