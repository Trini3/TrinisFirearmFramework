package io.github.trini3.tff.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.trini3.tff.bench.AttachmentLogic;
import io.github.trini3.tff.client.ScopeView;
import io.github.trini3.tff.client.gun.GunInput;
import io.github.trini3.tff.client.gun.LocalGun;
import io.github.trini3.tff.data.AttachmentSlot;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.testgun.TestShotgun;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * In-game pass over the test shotgun: reload, fire, aim, scope, third person, left-handed and the
 * attachment bench, with screenshots of each animation at several moments for a visual check. Run
 * on demand with {@code ./gradlew :fabric:runClientGameTest} (needs a display; see
 * docs/KNOWN_ISSUES.md). Not part of {@code build}.
 *
 * <p>Each wait-and-screenshot step advances about one tick more than it waits (seen by matching
 * the pictures against the animation), so a series drifts ahead of the tick counts in its names:
 * reload_t12 shows roughly tick 16 of the reload.
 */
public final class ShotgunClientGameTest implements FabricClientGameTest {
	/** The shotgun's reload length in ticks (data/shotgun/tff/gun/shotgun.json). */
	private static final int RELOAD_TICKS = 36;
	/** Long enough for vanilla's re-equip dip after the held stack changes. */
	private static final int EQUIP_TICKS = 15;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("give @a shotgun:shotgun");
			world.getServer().runCommand("give @a shotgun:shell 12");
			BlockPos pigPos = world.getServer().computeOnServer(server -> player(server).blockPosition().south(5));
			world.getServer().runCommand("summon pig " + pigPos.getX() + " " + pigPos.getY() + " " + pigPos.getZ() + " {NoAI:1b}");
			context.waitTicks(20);
			context.getInput().lookAt(pigPos);
			context.waitTicks(5);
			context.takeScreenshot("hip_empty");

			// Reload with the R key binding, a picture every few ticks.
			context.getInput().pressKey(GunInput.reloadKey);
			for (int t = 3; t < RELOAD_TICKS; t += 3) {
				context.waitTicks(3);
				context.takeScreenshot("reload_t" + pad(t));
			}
			context.waitTicks(10);
			expectRounds(context, 2, "after reload");
			int shellsLeft = context.computeOnClient(mc -> mc.player.getInventory().countItem(TestShotgun.SHELL.get()));
			check(shellsLeft == 10, "Expected 10 shells left, got " + shellsLeft);
			context.takeScreenshot("hip_loaded");

			// Fire at the pig: flash, kick and recovery.
			float pigHealth = world.getServer().computeOnServer(server -> pigHealth(server, pigPos));
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.takeScreenshot("fire_t0");
			context.waitTicks(2);
			context.takeScreenshot("fire_t2");
			context.waitTicks(3);
			context.takeScreenshot("fire_t5");
			context.waitTicks(10);
			expectRounds(context, 1, "after a shot");
			float pigAfter = world.getServer().computeOnServer(server -> pigHealth(server, pigPos));
			check(pigAfter < pigHealth, "Pig was not hurt: " + pigHealth + " -> " + pigAfter);

			// Aim down the sights (hold right click), then fire while aiming.
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(2);
			context.takeScreenshot("aim_t2");
			context.waitTicks(10);
			check(context.computeOnClient(mc -> LocalGun.isAiming(mc.player)), "Right click did not aim the gun");
			context.takeScreenshot("aim_t12");
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.takeScreenshot("aim_fire_t0");
			context.waitTicks(3);
			context.takeScreenshot("aim_fire_t3");
			context.waitTicks(10);
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(10);
			expectRounds(context, 0, "after two shots");
			context.takeScreenshot("hip_empty_after_shots");

			// Third person, standing and mid-reload.
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(3);
			context.takeScreenshot("third_person_back");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(3);
			context.takeScreenshot("third_person_front");
			context.getInput().pressKey(GunInput.reloadKey);
			context.waitTicks(12);
			context.takeScreenshot("third_person_front_reload");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(RELOAD_TICKS);
			expectRounds(context, 2, "after the third person reload");

			// Fit the scope (server side) and aim through it.
			world.getServer().runOnServer(server ->
				AttachmentLogic.fit(player(server).getMainHandItem(), AttachmentSlot.SCOPE, new ItemStack(Items.SPYGLASS), server.registryAccess()));
			// Changing the stack plays vanilla's re-equip dip; let it settle before the picture.
			context.waitTicks(EQUIP_TICKS);
			context.takeScreenshot("scoped_hip");
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(3);
			context.takeScreenshot("scoped_aim_t3");
			context.waitTicks(10);
			context.takeScreenshot("scoped_aim_t13");
			check(context.computeOnClient(mc -> ScopeView.isScoped(mc.player)), "Aiming with the spyglass scope did not open the scope view");
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(5);

			// Empty it and reload with the scope on: the scope rides on the barrel.
			fireTwice(context);
			context.getInput().pressKey(GunInput.reloadKey);
			context.waitTicks(9);
			context.takeScreenshot("scoped_reload_t9");
			context.waitTicks(9);
			context.takeScreenshot("scoped_reload_t18");
			context.waitTicks(RELOAD_TICKS);
			expectRounds(context, 2, "after the scoped reload");

			// Left-handed: everything mirrors.
			world.getServer().runOnServer(server -> AttachmentLogic.remove(player(server).getMainHandItem(), AttachmentSlot.SCOPE));
			setMainHand(context, HumanoidArm.LEFT);
			context.waitTicks(EQUIP_TICKS);
			context.takeScreenshot("left_hip");
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(12);
			context.takeScreenshot("left_aim");
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(10);
			fireTwice(context);
			context.getInput().pressKey(GunInput.reloadKey);
			context.waitTicks(9);
			context.takeScreenshot("left_reload_t9");
			context.waitTicks(9);
			context.takeScreenshot("left_reload_t18");
			context.waitTicks(RELOAD_TICKS);
			expectRounds(context, 2, "after the left-handed reload");
			setMainHand(context, HumanoidArm.RIGHT);

			// Sprinting lowers the gun.
			world.getServer().runCommand("kill @e[type=pig]");
			context.getInput().holdKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keySprint);
			context.waitTicks(12);
			context.takeScreenshot("sprint");
			context.getInput().releaseKey(options -> options.keySprint);
			context.getInput().releaseKey(options -> options.keyUp);
			context.waitTicks(10);

			// Place an attachment bench in front and open it.
			BlockPos benchPos = world.getServer().computeOnServer(server -> player(server).blockPosition().north(2));
			world.getServer().runCommand("setblock " + benchPos.getX() + " " + benchPos.getY() + " " + benchPos.getZ() + " tff:attachment_bench");
			context.waitTicks(5);
			context.getInput().lookAt(benchPos);
			context.waitTicks(5);
			context.takeScreenshot("bench_block");
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>, 40);
			context.takeScreenshot("bench_screen");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
		}
	}

	private static String pad(int ticks) {
		return ticks < 10 ? "0" + ticks : Integer.toString(ticks);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void expectRounds(ClientGameTestContext context, int expected, String when) {
		int rounds = context.computeOnClient(mc -> GunItem.state(mc.player.getMainHandItem()).rounds());
		check(rounds == expected, "Expected " + expected + " rounds " + when + ", got " + rounds);
	}

	/** Two shots, waiting out the fire interval between them. */
	private static void fireTwice(ClientGameTestContext context) {
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(10);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(10);
		expectRounds(context, 0, "after emptying the gun");
	}

	/** Changes the main hand option and lets the server sync it back to the player. */
	private static void setMainHand(ClientGameTestContext context, HumanoidArm arm) {
		context.runOnClient(mc -> {
			mc.options.mainHand().set(arm);
			mc.options.broadcastOptions();
		});
		context.waitFor(mc -> mainArm(mc) == arm, 40);
		context.waitTicks(3);
	}

	private static HumanoidArm mainArm(Minecraft mc) {
		return mc.player.getMainArm();
	}

	private static float pigHealth(MinecraftServer server, BlockPos near) {
		return server.overworld().getEntitiesOfClass(LivingEntity.class, new AABB(near).inflate(4), e -> e.getType() == EntityTypes.PIG)
			.stream().findFirst().map(LivingEntity::getHealth).orElse(-1f);
	}
}
