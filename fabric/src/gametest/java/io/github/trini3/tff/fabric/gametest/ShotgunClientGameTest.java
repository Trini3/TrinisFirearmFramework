package io.github.trini3.tff.fabric.gametest;

import io.github.trini3.tff.bench.AttachmentLogic;
import io.github.trini3.tff.client.GunClient;
import io.github.trini3.tff.client.ScopeView;
import io.github.trini3.tff.data.AttachmentSlot;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.testgun.TestShotgun;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * In-game pass over the test shotgun: reload, fire (pellets and muzzle smoke), aim, scope and
 * the attachment bench, with screenshots for a visual check. Run on demand with
 * {@code ./gradlew :fabric:runClientGameTest} (needs a display; see docs/KNOWN_ISSUES.md).
 * Not part of {@code build}.
 */
public final class ShotgunClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("give @a shotgun:shotgun");
			world.getServer().runCommand("give @a shotgun:shell 6");
			BlockPos pigPos = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				return player.blockPosition().south(5);
			});
			world.getServer().runCommand("summon pig " + pigPos.getX() + " " + pigPos.getY() + " " + pigPos.getZ() + " {NoAI:1b}");
			context.waitTicks(20);
			context.getInput().lookAt(pigPos.above(0));
			context.waitTicks(5);
			context.takeScreenshot("shotgun_held_empty");

			// Reload with the R key binding.
			context.getInput().pressKey(GunClient.reloadKey);
			context.waitTicks(10);
			context.takeScreenshot("shotgun_reload_open");
			context.waitTicks(10);
			context.takeScreenshot("shotgun_reload_shell_in");
			context.waitTicks(30);
			int rounds = context.computeOnClient(mc -> GunItem.state(mc.player.getMainHandItem()).rounds());
			if (rounds != 2) {
				throw new AssertionError("Expected 2 rounds after reload, got " + rounds);
			}
			int shellsLeft = context.computeOnClient(mc -> mc.player.getInventory().countItem(TestShotgun.SHELL.get()));
			if (shellsLeft != 4) {
				throw new AssertionError("Expected 4 shells left, got " + shellsLeft);
			}

			// Fire at the pig with left click.
			float pigHealth = world.getServer().computeOnServer(server -> pigHealth(server, pigPos));
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			context.takeScreenshot("shotgun_fired_smoke");
			context.waitTicks(10);
			int afterShot = context.computeOnClient(mc -> GunItem.state(mc.player.getMainHandItem()).rounds());
			if (afterShot != 1) {
				throw new AssertionError("Expected 1 round after firing, got " + afterShot);
			}
			float pigAfter = world.getServer().computeOnServer(server -> pigHealth(server, pigPos));
			if (!(pigAfter < pigHealth)) {
				throw new AssertionError("Pig was not hurt: " + pigHealth + " -> " + pigAfter);
			}

			// Aim down sights (hold right click).
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(10);
			boolean aiming = context.computeOnClient(mc -> mc.player.isUsingItem() && GunItem.isGun(mc.player.getUseItem()));
			context.takeScreenshot("shotgun_aiming");
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			if (!aiming) {
				throw new AssertionError("Right click did not aim the gun");
			}

			// Fit the scope (server side) and aim again.
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				AttachmentLogic.fit(player.getMainHandItem(), AttachmentSlot.SCOPE, new ItemStack(Items.SPYGLASS), server.registryAccess());
			});
			context.waitTicks(5);
			context.takeScreenshot("shotgun_scoped_hip");
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(10);
			context.takeScreenshot("shotgun_scoped_aiming");
			if (!context.computeOnClient(mc -> ScopeView.isScoped(mc.player))) {
				throw new AssertionError("Aiming with the spyglass scope did not open the scope view");
			}
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(5);

			// Reload with the scope fitted: the scope rides on the barrel, then a third person look.
			context.getInput().pressKey(GunClient.reloadKey);
			context.waitTicks(8);
			context.takeScreenshot("shotgun_scoped_reload");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(4);
			context.takeScreenshot("shotgun_third_person_reload");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(40);

			// Third person look at the arm pose.
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("shotgun_third_person");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// Place an attachment bench in front and open it.
			BlockPos benchPos = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().blockPosition().north(2));
			world.getServer().runCommand("kill @e[type=pig]");
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

	private static float pigHealth(net.minecraft.server.MinecraftServer server, BlockPos near) {
		return server.overworld().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(near).inflate(4),
				e -> e.getType() == net.minecraft.world.entity.EntityTypes.PIG)
			.stream().findFirst().map(LivingEntity::getHealth).orElse(-1f);
	}
}
