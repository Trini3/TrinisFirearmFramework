package io.github.trini3.tff.testgun;

import io.github.trini3.tff.api.TffApi;
import io.github.trini3.tff.gun.GunItem;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The framework's test gun: a break-action shotgun (namespace {@code shotgun}, from Trini3's
 * Simpleguns resource pack). It shows the whole content workflow: these two lines of Java, JSON
 * under {@code data/shotgun/tff/}, and assets under {@code assets/shotgun/} (a {@code tff:gun}
 * item model made of part models, and an animation file). Its scope is a vanilla spyglass fitted
 * at the attachment bench. Content branches can delete this package and its {@code shotgun} files.
 */
public final class TestShotgun {
	public static final String NAMESPACE = "shotgun";

	public static Supplier<GunItem> SHOTGUN;
	public static Supplier<Item> SHELL;

	private TestShotgun() {}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}

	public static void init() {
		SHOTGUN = TffApi.registerGun(id("shotgun"));
		SHELL = TffApi.registerItem(id("shell"), p -> p);
	}
}
