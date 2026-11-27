package io.github.trini3.tff.fabric.gametest;

import io.github.trini3.tff.bench.AttachmentBenchMenu;
import io.github.trini3.tff.bench.AttachmentLogic;
import io.github.trini3.tff.data.AttachmentSlot;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.gun.GunStats;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public final class AttachmentGameTests {
	private static GunDefinition definition(GameTestHelper helper, ItemStack gun) {
		return GunItem.definition(gun, helper.getLevel().registryAccess()).orElseThrow(() -> helper.assertionException("no definition"));
	}

	@GameTest
	public void scopeChangesAimStats(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		GunDefinition def = definition(helper, gun);
		helper.assertValueEqual(GunStats.aimZoom(def, gun, registries), 0.9f, "zoom without scope");
		helper.assertTrue(AttachmentLogic.fit(gun, AttachmentSlot.SCOPE, new ItemStack(Items.SPYGLASS), registries), "scope fits");
		helper.assertValueEqual(GunStats.aimZoom(def, gun, registries), 0.5f, "zoom with scope");
		helper.assertValueEqual(GunStats.spread(def, gun, registries, true), 1.5f, "aimed spread with scope");
		helper.assertValueEqual(GunStats.spread(def, gun, registries, false), 6f, "hip spread unchanged");
		AttachmentLogic.remove(gun, AttachmentSlot.SCOPE);
		helper.assertValueEqual(GunStats.aimZoom(def, gun, registries), 0.9f, "zoom after removal");
		helper.succeed();
	}

	@GameTest
	public void incompatibleItemsAreRejected(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		helper.assertFalse(AttachmentLogic.fit(gun, AttachmentSlot.SCOPE, new ItemStack(Items.STICK), registries), "stick is not a scope");
		ItemStack tube = new ItemStack(TffTestContent.TEST_TUBE.get());
		helper.assertFalse(AttachmentLogic.fit(tube, AttachmentSlot.SCOPE, new ItemStack(Items.SPYGLASS), registries), "tube gun accepts no scope");
		helper.assertTrue(GunStats.attachments(tube).fitted().isEmpty(), "tube unchanged");
		helper.succeed();
	}

	@GameTest
	public void benchFitsAndRemovesWithoutDuplicating(GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		AttachmentBenchMenu menu = new AttachmentBenchMenu(1, player.getInventory());
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());

		menu.getSlot(AttachmentBenchMenu.GUN_SLOT).setByPlayer(gun);
		helper.assertFalse(menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).hasItem(), "no scope shown yet");
		helper.assertFalse(menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).mayPlace(new ItemStack(Items.STICK)), "stick refused");
		helper.assertTrue(menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).mayPlace(new ItemStack(Items.SPYGLASS)), "spyglass accepted");

		menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).setByPlayer(new ItemStack(Items.SPYGLASS));
		helper.assertTrue(GunStats.attachments(gun).get(AttachmentSlot.SCOPE).isPresent(), "gun has scope");
		helper.assertTrue(menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).getItem().is(Items.SPYGLASS), "scope slot shows it");

		ItemStack taken = menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).safeTake(1, 1, player);
		helper.assertTrue(taken.is(Items.SPYGLASS), "took scope back");
		helper.assertTrue(GunStats.attachments(gun).get(AttachmentSlot.SCOPE).isEmpty(), "gun lost scope");
		helper.assertFalse(menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).hasItem(), "scope slot empty");
		helper.succeed();
	}

	@GameTest
	public void removingGunKeepsScopeOnIt(GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		AttachmentBenchMenu menu = new AttachmentBenchMenu(1, player.getInventory());
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		menu.getSlot(AttachmentBenchMenu.GUN_SLOT).setByPlayer(gun);
		menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).setByPlayer(new ItemStack(Items.SPYGLASS));

		ItemStack takenGun = menu.getSlot(AttachmentBenchMenu.GUN_SLOT).safeTake(1, 1, player);
		helper.assertTrue(GunStats.attachments(takenGun).get(AttachmentSlot.SCOPE).isPresent(), "scope stays on the gun");
		helper.assertFalse(menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).hasItem(), "scope slot cleared with the gun gone");
		helper.assertFalse(menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).mayPlace(new ItemStack(Items.SPYGLASS)), "no scope without a gun");
		helper.succeed();
	}

	@GameTest
	public void shiftClickingScopeOutMovesItToInventory(GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		AttachmentBenchMenu menu = new AttachmentBenchMenu(1, player.getInventory());
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		menu.getSlot(AttachmentBenchMenu.GUN_SLOT).setByPlayer(gun);
		menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).setByPlayer(new ItemStack(Items.SPYGLASS));

		menu.quickMoveStack(player, AttachmentBenchMenu.SCOPE_SLOT);
		helper.assertValueEqual(player.getInventory().countItem(Items.SPYGLASS), 1, "one spyglass in inventory");
		helper.assertTrue(GunStats.attachments(gun).get(AttachmentSlot.SCOPE).isEmpty(), "gun lost scope");
		helper.assertFalse(menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).hasItem(), "scope slot empty");
		helper.succeed();
	}

	@GameTest
	public void shiftClickingScopeInFitsOne(GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.SPYGLASS, 3));
		AttachmentBenchMenu menu = new AttachmentBenchMenu(1, player.getInventory());
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		menu.getSlot(AttachmentBenchMenu.GUN_SLOT).setByPlayer(gun);

		// Hotbar slot 0 is the first of the last 9 menu slots.
		int hotbar0 = menu.slots.size() - 9;
		menu.quickMoveStack(player, hotbar0);
		helper.assertTrue(GunStats.attachments(gun).get(AttachmentSlot.SCOPE).isPresent(), "scope fitted");
		helper.assertValueEqual(player.getInventory().countItem(Items.SPYGLASS), 2, "one spyglass used");
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal") // the only mock player with a connection, which handing items back needs
	public void closingBenchReturnsOnlyTheGun(GameTestHelper helper) {
		Player player = helper.makeMockServerPlayerInLevel();
		AttachmentBenchMenu menu = new AttachmentBenchMenu(1, player.getInventory(),
			net.minecraft.world.inventory.ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO)));
		ItemStack gun = new ItemStack(TffTestContent.TEST_GUN.get());
		menu.getSlot(AttachmentBenchMenu.GUN_SLOT).setByPlayer(gun);
		menu.getSlot(AttachmentBenchMenu.SCOPE_SLOT).setByPlayer(new ItemStack(Items.SPYGLASS));
		menu.removed(player);
		helper.assertValueEqual(player.getInventory().countItem(TffTestContent.TEST_GUN.get()), 1, "gun returned");
		helper.assertValueEqual(player.getInventory().countItem(Items.SPYGLASS), 0, "no loose scope");
		helper.succeed();
	}
}
