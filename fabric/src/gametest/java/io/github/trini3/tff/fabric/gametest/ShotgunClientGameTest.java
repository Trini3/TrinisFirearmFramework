package io.github.trini3.tff.fabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.trini3.tff.bench.AttachmentLogic;
import io.github.trini3.tff.client.ScopeView;
import io.github.trini3.tff.client.gun.GunInput;
import io.github.trini3.tff.client.gun.LocalGun;
import io.github.trini3.tff.config.TffConfig;
import io.github.trini3.tff.data.AttachmentSlot;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.platform.TffPlatform;
import io.github.trini3.tff.testgun.TestShotgun;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * In-game pass over the test shotgun: reload, fire, aim, scope, third person, left-handed, a
 * buckshot range check on zombies at 50 and 70 blocks, a headshot against a body shot and the
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
			expectRounds(context, 1, "after reload");
			int shellsLeft = context.computeOnClient(mc -> mc.player.getInventory().countItem(TestShotgun.SHELL.get()));
			check(shellsLeft == 11, "Expected 11 shells left (it takes one), got " + shellsLeft);
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
			expectRounds(context, 0, "after a shot");
			float pigAfter = world.getServer().computeOnServer(server -> pigHealth(server, pigPos));
			check(pigAfter < pigHealth, "Pig was not hurt: " + pigHealth + " -> " + pigAfter);
			// Single shot: the hammer stays down until the reload cocks it.
			context.takeScreenshot("hip_fired");
			reload(context);

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
			expectRounds(context, 0, "after the aimed shot");
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
			expectRounds(context, 1, "after the third person reload");

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
			fireOnce(context);
			context.getInput().pressKey(GunInput.reloadKey);
			context.waitTicks(9);
			context.takeScreenshot("scoped_reload_t9");
			context.waitTicks(9);
			context.takeScreenshot("scoped_reload_t18");
			context.waitTicks(RELOAD_TICKS);
			expectRounds(context, 1, "after the scoped reload");

			// Range check through the scope: buckshot kills a zombie at 50 blocks, not at 70.
			world.getServer().runCommand("kill @e[type=pig]");
			world.getServer().runCommand("difficulty normal");
			rangeShot(context, world, 50, true);
			reload(context);
			rangeShot(context, world, 70, false);
			reload(context);

			// Headshots, from the hip with the scope off so the zombie shows: the same
			// shot at 8 blocks does the config's multiplier more damage to the head than to the body.
			// The hip spread (1 degree) and the pattern stay inside the head or the body there.
			world.getServer().runOnServer(server -> AttachmentLogic.remove(player(server).getMainHandItem(), AttachmentSlot.SCOPE));
			world.getServer().runCommand("kill @e[type=item]");
			context.waitTicks(EQUIP_TICKS);
			float body = shotDamage(context, world, 8, 1.0, "body_shot");
			reload(context);
			float head = shotDamage(context, world, 8, 1.72, "headshot");
			reload(context);
			float multiplier = TffConfig.get().headshotMultiplier();
			System.out.println("Headshot check at 8 blocks: body shot " + body + ", headshot " + head + ", multiplier " + multiplier);
			check(body > 40f, "Expected all 9 pellets in the body at 8 blocks, damage " + body);
			check(Math.abs(head - body * multiplier) < 1f, "Headshot did " + head + ", body shot " + body + ", multiplier " + multiplier);
			// Sparks are off by default; turn them on in the file, reload it as a world start would, and shoot again.
			check(!TffConfig.get().headshotSparks(), "Headshot sparks should be off by default");
			Path config = TffPlatform.INSTANCE.configDir().resolve(TffConfig.FILE_NAME);
			String saved = readConfig(config);
			writeConfig(config, "{\"headshot_multiplier\": " + multiplier + ", \"headshot_sparks\": true}");
			TffConfig.load();
			check(TffConfig.get().headshotSparks(), "headshot_sparks: true in the file did not turn sparks on");
			float sparked = shotDamage(context, world, 8, 1.72, "headshot_sparks");
			reload(context);
			writeConfig(config, saved);
			TffConfig.load();
			check(Math.abs(sparked - head) < 1f, "Headshot with sparks did " + sparked + ", without " + head);

			// Left-handed: everything mirrors.
			setMainHand(context, HumanoidArm.LEFT);
			context.waitTicks(EQUIP_TICKS);
			context.takeScreenshot("left_hip");
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(12);
			context.takeScreenshot("left_aim");
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(10);
			fireOnce(context);
			context.getInput().pressKey(GunInput.reloadKey);
			context.waitTicks(9);
			context.takeScreenshot("left_reload_t9");
			context.waitTicks(9);
			context.takeScreenshot("left_reload_t18");
			context.waitTicks(RELOAD_TICKS);
			expectRounds(context, 1, "after the left-handed reload");
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

	/** One shot, which empties the single-shot gun. */
	private static void fireOnce(ClientGameTestContext context) {
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(10);
		expectRounds(context, 0, "after emptying the gun");
	}

	/** Reloads with the R key and waits for it to finish. */
	private static void reload(ClientGameTestContext context) {
		context.getInput().pressKey(GunInput.reloadKey);
		context.waitTicks(RELOAD_TICKS + 10);
		expectRounds(context, 1, "after reloading");
	}

	/**
	 * Stands a zombie {@code blocks} south, aims through the sights at its middle and fires once.
	 * A stone block over its head keeps the sun off it.
	 */
	private static void rangeShot(ClientGameTestContext context, TestSingleplayerContext world, int blocks, boolean expectKill) {
		BlockPos at = world.getServer().computeOnServer(server -> player(server).blockPosition().south(blocks));
		// Shade first: a zombie that ticks once in full sun can catch fire.
		world.getServer().runCommand("setblock " + at.getX() + " " + (at.getY() + 2) + " " + at.getZ() + " stone");
		world.getServer().runCommand("summon zombie " + at.getX() + " " + at.getY() + " " + at.getZ() + " {NoAI:1b,PersistenceRequired:1b}");
		context.waitTicks(10);
		float[] rot = world.getServer().computeOnServer(server -> {
			Zombie zombie = zombieNear(server, at);
			if (zombie == null) {
				return null;
			}
			Vec3 d = zombie.position().add(0, zombie.getBbHeight() / 2, 0).subtract(player(server).getEyePosition());
			return new float[] {(float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))};
		});
		check(rot != null, "No zombie at " + blocks + " blocks");
		context.getInput().lookAt(rot[0], rot[1]);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(15);
		context.takeScreenshot("range_" + blocks + "_aim");
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		context.takeScreenshot("range_" + blocks + "_hit");
		context.waitTicks(10);
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		float health = world.getServer().computeOnServer(server -> {
			Zombie zombie = zombieNear(server, at);
			return zombie == null || !zombie.isAlive() ? 0f : zombie.getHealth();
		});
		if (expectKill) {
			check(health <= 0f, "Buckshot did not kill the zombie at " + blocks + " blocks, health left " + health);
		} else {
			check(health > 0f && health < 20f, "Expected the zombie at " + blocks + " blocks hurt but alive, health " + health);
		}
		world.getServer().runCommand("kill @e[type=zombie]");
		world.getServer().runCommand("setblock " + at.getX() + " " + (at.getY() + 2) + " " + at.getZ() + " air");
		context.waitTicks(5);
	}

	/**
	 * Stands a 200-health zombie {@code blocks} south, facing the player, aims from the hip at
	 * {@code height} blocks above its feet and fires once. Returns the damage done.
	 */
	private static float shotDamage(ClientGameTestContext context, TestSingleplayerContext world, int blocks, double height, String name) {
		BlockPos at = world.getServer().computeOnServer(server -> player(server).blockPosition().south(blocks));
		world.getServer().runCommand("setblock " + at.getX() + " " + (at.getY() + 2) + " " + at.getZ() + " stone");
		world.getServer().runCommand("summon zombie " + at.getX() + " " + at.getY() + " " + at.getZ() + " {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
		context.waitTicks(10);
		float[] rot = world.getServer().computeOnServer(server -> {
			Zombie zombie = zombieNear(server, at);
			if (zombie == null) {
				return null;
			}
			zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
			zombie.setHealth(200f);
			// Pellets knock it back out of the shade; keep it there, and unburnt either way.
			zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
			zombie.clearFire();
			zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0, false, false));
			Vec3 d = zombie.position().add(0, height, 0).subtract(player(server).getEyePosition());
			return new float[] {(float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))};
		});
		check(rot != null, "No zombie at " + blocks + " blocks");
		context.getInput().lookAt(rot[0], rot[1]);
		context.waitTicks(10);
		context.takeScreenshot(name + "_aim");
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(1);
		context.takeScreenshot(name + "_t1");
		context.waitTicks(1);
		context.takeScreenshot(name + "_t2");
		context.waitTicks(2);
		context.takeScreenshot(name + "_t4");
		context.waitTicks(10);
		float health = world.getServer().computeOnServer(server -> {
			Zombie zombie = zombieNear(server, at);
			return zombie == null || !zombie.isAlive() ? 0f : zombie.getHealth();
		});
		world.getServer().runCommand("kill @e[type=zombie]");
		world.getServer().runCommand("setblock " + at.getX() + " " + (at.getY() + 2) + " " + at.getZ() + " air");
		context.waitTicks(5);
		return 200f - health;
	}

	private static String readConfig(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void writeConfig(Path file, String text) {
		try {
			Files.writeString(file, text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static @Nullable Zombie zombieNear(MinecraftServer server, BlockPos at) {
		return server.overworld().getEntitiesOfClass(Zombie.class, new AABB(at).inflate(3)).stream().findFirst().orElse(null);
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
