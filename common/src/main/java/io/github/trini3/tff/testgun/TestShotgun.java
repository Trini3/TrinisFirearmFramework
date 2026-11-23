package io.github.trini3.tff.testgun;

import io.github.trini3.tff.api.TffApi;
import io.github.trini3.tff.gun.GunItem;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The framework's test gun: a break-action shotgun (namespace {@code shotgun}, from Trini3's
 * Simpleguns resource pack). It shows the whole content workflow: these three lines of Java,
 * JSON under {@code data/shotgun/tff/}, and normal assets under {@code assets/shotgun/}.
 * Content branches can delete this package and its {@code shotgun} assets/data.
 */
public final class TestShotgun {
	public static final String NAMESPACE = "shotgun";

	public static Supplier<GunItem> SHOTGUN;
	public static Supplier<Item> SHELL;
	public static Supplier<Item> SCOPE;

	private TestShotgun() {}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}

	public static void init() {
		SHOTGUN = TffApi.registerGun(id("shotgun"));
		SHELL = TffApi.registerItem(id("shell"), p -> p);
		SCOPE = TffApi.registerItem(id("scope"), p -> p.stacksTo(1));
	}
}
