package io.github.trini3.tff.fabric.gametest;

import io.github.trini3.tff.data.AmmoDefinition;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.gun.GunLogic;
import io.github.trini3.tff.projectile.BulletEntity;
import io.github.trini3.tff.registry.TffEntities;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GunGameTests {
	private static final ResourceKey<AmmoDefinition> SHELL = ResourceKey.create(TffRegistries.AMMO, Identifier.fromNamespaceAndPath("tff_test", "test_shell"));

	/** A survival player standing in the test area, facing +Z (south), holding a loaded test gun. */
	private static Player shooter(GameTestHelper helper, GameType mode, ItemStack gun, int rounds) {
		Player player = helper.makeMockServerPlayer(mode);
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
		player.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
		GunItem.setState(gun, GunState.EMPTY.withCapacity(2).withRounds(rounds, Optional.of(SHELL)));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
		return player;
	}

	/** Bullets fired by this player (tests in a batch run side by side, so filter by owner). */
	private static int bulletsFrom(GameTestHelper helper, Player player) {
		AABB box = new AABB(player.blockPosition()).inflate(32);
		return helper.getLevel().getEntitiesOfClass(BulletEntity.class, box, b -> b.getOwner() == player).size();
	}

	@GameTest
	public void definitionsLoadFromDatapack(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		GunDefinition gun = TffRegistries.gun(registries, ResourceKey.create(TffRegistries.GUN, Identifier.fromNamespaceAndPath("tff_test", "test_gun")))
			.orElseThrow(() -> helper.assertionException("test_gun definition missing"));
		helper.assertValueEqual(gun.capacity(), 2, "capacity");
		helper.assertValueEqual(gun.reload().mode(), GunDefinition.ReloadMode.MAGAZINE, "reload mode");
		AmmoDefinition shell = TffRegistries.ammo(registries, SHELL).orElseThrow(() -> helper.assertionException("test_shell missing"));
		helper.assertValueEqual(shell.pellets(), 6, "pellets");
		helper.assertTrue(shell.matches(new ItemStack(Items.GOLD_NUGGET)), "ammo item");
		helper.assertValueEqual(shell.minDamage(), 2.0f, "min_damage defaults to damage");
		helper.succeed();
	}

	@GameTest
	public void firingUsesRoundAndSpawnsEveryPellet(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 2);
		helper.assertValueEqual(GunLogic.tryFire(player, 0f, 0f), GunLogic.FireResult.FIRED, "first shot");
		helper.assertValueEqual(GunItem.state(gun).rounds(), 1, "rounds after shot");
		helper.assertValueEqual(bulletsFrom(helper, player), 6, "pellets spawned");
		helper.succeed();
	}

	@GameTest
	public void fireRateIsEnforced(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 2);
		helper.assertValueEqual(GunLogic.tryFire(player, 0f, 0f), GunLogic.FireResult.FIRED, "first shot");
		helper.assertValueEqual(GunLogic.tryFire(player, 0f, 0f), GunLogic.FireResult.COOLDOWN, "immediate second shot");
		helper.runAfterDelay(8, () -> {
			helper.assertValueEqual(GunLogic.tryFire(player, 0f, 0f), GunLogic.FireResult.FIRED, "shot after interval");
			helper.succeed();
		});
	}

	@GameTest
	public void emptyGunClicks(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 0);
		helper.assertValueEqual(GunLogic.tryFire(player, 0f, 0f), GunLogic.FireResult.EMPTY, "empty");
		helper.assertValueEqual(bulletsFrom(helper, player), 0, "no pellets");
		helper.succeed();
	}

	@GameTest
	public void magazineReloadTakesAmmoFromInventory(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 0);
		player.getInventory().add(new ItemStack(Items.GOLD_NUGGET, 5));
		helper.assertValueEqual(GunLogic.startReload(player), GunLogic.ReloadResult.STARTED, "reload start");
		helper.assertValueEqual(GunLogic.tryFire(player, 0f, 0f), GunLogic.FireResult.RELOADING, "cannot fire mid-reload");
		helper.runAfterDelay(11, () -> {
			GunLogic.inventoryTick(player, gun, helper.getLevel(), true);
			GunState state = GunItem.state(gun);
			helper.assertValueEqual(state.rounds(), 2, "rounds after reload");
			helper.assertFalse(state.reloading(), "reload finished");
			helper.assertValueEqual(player.getInventory().countItem(Items.GOLD_NUGGET), 3, "nuggets left");
			helper.succeed();
		});
	}

	/** The shooter's client finishes its reload a moment before the server: a shot in that gap completes the reload. */
	@GameTest
	public void lateShotFinishesMagazineReload(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 0);
		player.getInventory().add(new ItemStack(Items.GOLD_NUGGET, 5));
		helper.assertValueEqual(GunLogic.startReload(player), GunLogic.ReloadResult.STARTED, "reload start");
		// The test gun reloads in 10 ticks; one tick of scheduling slack either way still lands in the window.
		helper.runAfterDelay(10 - GunLogic.RELOAD_TOLERANCE + 1, () -> {
			helper.assertValueEqual(GunLogic.tryFire(player, 0f, 0f), GunLogic.FireResult.FIRED, "late shot");
			GunState state = GunItem.state(gun);
			helper.assertFalse(state.reloading(), "reload finished by the shot");
			helper.assertValueEqual(state.rounds(), 1, "two loaded, one fired");
			helper.assertValueEqual(player.getInventory().countItem(Items.GOLD_NUGGET), 3, "nuggets left");
			helper.succeed();
		});
	}

	@GameTest
	public void earlyShotDuringMagazineReloadIsRefused(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 0);
		player.getInventory().add(new ItemStack(Items.GOLD_NUGGET, 5));
		GunLogic.startReload(player);
		helper.runAfterDelay(3, () -> {
			helper.assertValueEqual(GunLogic.tryFire(player, 0f, 0f), GunLogic.FireResult.RELOADING, "early shot");
			helper.assertTrue(GunItem.state(gun).reloading(), "still reloading");
			helper.assertValueEqual(player.getInventory().countItem(Items.GOLD_NUGGET), 5, "nothing loaded yet");
			helper.succeed();
		});
	}

	/** The shipped test shotgun's data (with its sound cues) decodes. */
	@GameTest
	public void shotgunDefinitionLoads(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		GunDefinition gun = TffRegistries.gun(registries, ResourceKey.create(TffRegistries.GUN, Identifier.fromNamespaceAndPath("shotgun", "shotgun")))
			.orElseThrow(() -> helper.assertionException("shotgun definition missing"));
		helper.assertValueEqual(gun.capacity(), 2, "capacity");
		helper.assertValueEqual(gun.reload().sounds().size(), 4, "reload sound cues");
		helper.assertTrue(gun.sounds().fire().isPresent(), "fire sound");
		helper.assertTrue(gun.sounds().dryFire().isPresent(), "dry fire sound");
		AmmoDefinition buckshot = TffRegistries.ammo(registries, ResourceKey.create(TffRegistries.AMMO, Identifier.fromNamespaceAndPath("shotgun", "buckshot")))
			.orElseThrow(() -> helper.assertionException("buckshot missing"));
		helper.assertValueEqual(buckshot.pellets(), 8, "pellets");
		helper.succeed();
	}

	@GameTest
	public void reloadWithoutAmmoFails(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 0);
		helper.assertValueEqual(GunLogic.startReload(player), GunLogic.ReloadResult.NO_AMMO, "no ammo");
		helper.succeed();
	}

	@GameTest
	public void creativeReloadIsFree(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.CREATIVE, gun, 0);
		helper.assertValueEqual(GunLogic.startReload(player), GunLogic.ReloadResult.STARTED, "reload start");
		helper.runAfterDelay(11, () -> {
			GunLogic.inventoryTick(player, gun, helper.getLevel(), true);
			helper.assertValueEqual(GunItem.state(gun).rounds(), 2, "rounds after creative reload");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void perRoundReloadLoadsOneAtATimeAndFiringInterrupts(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_TUBE.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 0);
		GunItem.setState(gun, GunState.EMPTY.withCapacity(4));
		player.getInventory().add(new ItemStack(Items.GOLD_NUGGET, 10));
		helper.assertValueEqual(GunLogic.startReload(player), GunLogic.ReloadResult.STARTED, "reload start");
		helper.runAfterDelay(6, () -> {
			GunLogic.inventoryTick(player, gun, helper.getLevel(), true);
			helper.assertValueEqual(GunItem.state(gun).rounds(), 1, "first round in");
			helper.assertTrue(GunItem.state(gun).reloading(), "keeps reloading");
			helper.assertValueEqual(GunLogic.tryFire(player, 0f, 0f), GunLogic.FireResult.FIRED, "fire interrupts per-round reload");
			helper.assertFalse(GunItem.state(gun).reloading(), "reload interrupted");
			helper.assertValueEqual(player.getInventory().countItem(Items.GOLD_NUGGET), 9, "one nugget used");
			helper.succeed();
		});
	}

	@GameTest
	public void switchingAwayCancelsReload(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 0);
		player.getInventory().add(new ItemStack(Items.GOLD_NUGGET, 5));
		GunLogic.startReload(player);
		GunLogic.inventoryTick(player, gun, helper.getLevel(), false);
		helper.assertFalse(GunItem.state(gun).reloading(), "reload cancelled");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void pelletsDamageTarget(GameTestHelper helper) {
		Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(1.5, 1, 5.5));
		pig.setNoAi(true);
		float health = pig.getHealth();
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 2);
		// Aim from eye height down at the pig's body, 4 blocks south.
		Vec3 eye = player.getEyePosition();
		Vec3 target = pig.position().add(0, pig.getBbHeight() / 2, 0);
		Vec3 d = target.subtract(eye);
		float yRot = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
		float xRot = (float) (-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
		helper.assertValueEqual(GunLogic.tryFire(player, yRot, xRot), GunLogic.FireResult.FIRED, "shot");
		helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < health - 2.0f, "several pellets hit the pig"));
	}

	@GameTest(maxTicks = 100)
	public void bulletsDespawn(GameTestHelper helper) {
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		Player player = shooter(helper, GameType.SURVIVAL, gun, 2);
		GunLogic.tryFire(player, 0f, -60f);
		helper.succeedWhen(() -> helper.assertValueEqual(helper.getLevel().getEntities(TffEntities.BULLET.get(), b -> true).size(), 0, "bullets gone"));
	}
}
