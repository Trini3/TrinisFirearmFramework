package io.github.trini3.tff.fabric.gametest;

import io.github.trini3.tff.api.TffApi;
import io.github.trini3.tff.gun.GunItem;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/** Test-only guns, defined like a content mod would: one line of Java each plus JSON. */
public final class TffTestContent implements ModInitializer {
	public static Supplier<GunItem> TEST_GUN;
	public static Supplier<GunItem> TEST_TUBE;

	@Override
	public void onInitialize() {
		TEST_GUN = TffApi.registerGun(Identifier.fromNamespaceAndPath("tff_test", "test_gun"));
		TEST_TUBE = TffApi.registerGun(Identifier.fromNamespaceAndPath("tff_test", "test_tube"));
	}
}
