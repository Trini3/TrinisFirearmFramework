package io.github.trini3.tff.fabric.gametest;

import io.github.trini3.tff.Tff;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class TffSmokeGameTests {
	/** Proves the mod loads on a server and the gametest pipeline runs. */
	@GameTest
	public void modLoads(GameTestHelper helper) {
		helper.assertTrue(Tff.id("x").getNamespace().equals(Tff.MOD_ID), "namespace");
		helper.succeed();
	}
}
