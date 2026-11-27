package io.github.trini3.tff.fabric.gametest;

import io.github.trini3.tff.api.ProjectileBehavior;
import io.github.trini3.tff.config.TffConfig;
import io.github.trini3.tff.data.AmmoDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.data.HeadDefinition;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.gun.GunLogic;
import io.github.trini3.tff.projectile.BulletEntity;
import io.github.trini3.tff.projectile.Headshots;
import io.github.trini3.tff.registry.TffEntities;
import io.github.trini3.tff.registry.TffRegistries;
import io.github.trini3.tff.testgun.TestShotgun;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Headshots: which hits count, and that they do the config's multiplier of damage. */
public final class HeadshotGameTests {
	private static final ResourceKey<AmmoDefinition> BUCKSHOT = ResourceKey.create(TffRegistries.AMMO, Identifier.fromNamespaceAndPath("shotgun", "buckshot"));
	/** A zombie's 2 armor points take 1.6 % off a buckshot pellet, body or head. */
	private static final float ZOMBIE_ARMOR_FACTOR = 1f - 0.4f / 25f;

	private static Holder<EntityType<?>> holder(EntityType<?> type) {
		return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type);
	}

	/** A mob that stands still, facing {@code yaw} (180 = north, toward a shooter at a lower z). */
	private static <E extends Mob> E target(GameTestHelper helper, EntityType<E> type, Vec3 pos, float yaw) {
		E mob = helper.spawn(type, pos);
		mob.setNoAi(true);
		face(mob, yaw);
		return mob;
	}

	private static void face(LivingEntity mob, float yaw) {
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
	}

	private static void setHealth(LivingEntity mob, float health) {
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		mob.setHealth(health);
	}

	/** The point {@code height} blocks up on the north face of {@code mob}'s hitbox. */
	private static Vec3 north(LivingEntity mob, double height) {
		return new Vec3(mob.getX(), mob.getY() + height, mob.getZ() - mob.getBbWidth() / 2);
	}

	/** A buckshot pellet at {@code at} that has not flown anywhere yet, so it does its full 5 damage. */
	private static BulletEntity pellet(GameTestHelper helper, Vec3 at) {
		AmmoDefinition buckshot = TffRegistries.ammo(helper.getLevel().registryAccess(), BUCKSHOT)
			.orElseThrow(() -> helper.assertionException("buckshot missing"));
		BulletEntity bullet = new BulletEntity(TffEntities.BULLET.get(), helper.getLevel());
		bullet.configure(buckshot, ProjectileBehavior.BULLET, 1f, at);
		bullet.setPos(at);
		return bullet;
	}

	@GameTest
	public void headFilesLoad(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		HeadDefinition zombie = Headshots.definition(registries, holder(EntityTypes.ZOMBIE))
			.orElseThrow(() -> helper.assertionException("no head file for zombies"));
		helper.assertValueEqual(zombie.boxes().size(), 1, "zombie head boxes");
		helper.assertTrue(zombie.hasBabyBoxes(), "baby zombies have their own head box");
		helper.assertTrue(Headshots.definition(registries, holder(EntityTypes.PLAYER)).isPresent(), "players have a head file");
		HeadDefinition slime = Headshots.definition(registries, holder(EntityTypes.SLIME))
			.orElseThrow(() -> helper.assertionException("no head file for slimes"));
		helper.assertTrue(slime.boxes().isEmpty(), "slimes have no head");
		helper.succeed();
	}

	/** Every vanilla living thing is in a head file, so none falls back to the guess for unknown mobs. */
	@GameTest
	public void everyVanillaMobHasAHeadFile(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> missing = new ArrayList<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (!"minecraft".equals(id.getNamespace())) {
				continue;
			}
			Entity entity = type.create(level, EntitySpawnReason.LOAD);
			boolean living = entity instanceof LivingEntity || type == EntityTypes.PLAYER;
			if (living && Headshots.definition(level.registryAccess(), holder(type)).isEmpty()) {
				missing.add(id.toString());
			}
			if (entity != null) {
				entity.discard();
			}
		}
		helper.assertTrue(missing.isEmpty(), "No head file for " + missing);
		helper.succeed();
	}

	/** The same pellet on a zombie's face and on its chest: the face takes the multiplier more. */
	@GameTest
	public void headshotDoesTheConfigMultiplier(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie headTarget = target(helper, EntityTypes.ZOMBIE, new Vec3(1.5, 1, 4.5), 180f);
		Zombie bodyTarget = target(helper, EntityTypes.ZOMBIE, new Vec3(4.5, 1, 4.5), 180f);
		setHealth(headTarget, 100f);
		setHealth(bodyTarget, 100f);
		Vec3 face = north(headTarget, 1.75);
		Vec3 chest = north(bodyTarget, 1.0);
		helper.assertTrue(Headshots.isHeadshot(level.registryAccess(), headTarget, face), "a zombie's face is its head");
		helper.assertFalse(Headshots.isHeadshot(level.registryAccess(), bodyTarget, chest), "a zombie's chest is not");
		BulletEntity pellet = pellet(helper, face.add(0, 0, -1));
		helper.assertTrue(pellet.damage(level, new EntityHitResult(headTarget, face)), "the headshot hurt");
		helper.assertTrue(pellet.damage(level, new EntityHitResult(bodyTarget, chest)), "the body shot hurt");
		float headLoss = 100f - headTarget.getHealth();
		float bodyLoss = 100f - bodyTarget.getHealth();
		float multiplier = TffConfig.get().headshotMultiplier();
		helper.assertTrue(Math.abs(bodyLoss - 5f * ZOMBIE_ARMOR_FACTOR) < 0.01f, "body shot took " + bodyLoss);
		helper.assertTrue(Math.abs(headLoss - bodyLoss * multiplier) < 0.01f,
			"headshot took " + headLoss + ", body shot " + bodyLoss + ", multiplier " + multiplier);
		helper.succeed();
	}

	/** The shotgun aimed at a zombie's head from 6 blocks: all 9 pellets are headshots. */
	@GameTest(maxTicks = 20)
	public void buckshotToTheHead(GameTestHelper helper) {
		Zombie zombie = target(helper, EntityTypes.ZOMBIE, new Vec3(1.5, 1, 7.5), 180f);
		zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200)); // no sunburn damage muddling the count
		setHealth(zombie, 100f);
		ItemStack gun = new ItemStack(TestShotgun.SHOTGUN.get());
		GunItem.setState(gun, GunState.EMPTY.withCapacity(1).withRounds(1, Optional.of(BUCKSHOT)));
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
		player.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		// The middle of the head (1.5 to 1.95 blocks up); the hip spread and the pattern stay inside it.
		Vec3 d = zombie.position().add(0, 1.72, 0).subtract(player.getEyePosition());
		float yRot = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float xRot = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
		helper.assertValueEqual(GunLogic.tryFire(player, yRot, xRot), GunLogic.FireResult.FIRED, "shot");
		float expected = 100f - 9 * 5f * TffConfig.get().headshotMultiplier() * ZOMBIE_ARMOR_FACTOR;
		helper.succeedWhen(() -> helper.assertTrue(Math.abs(zombie.getHealth() - expected) < 0.5f,
			"expected 9 headshots (health " + expected + "), health is " + zombie.getHealth()));
	}

	@GameTest
	public void slimesHaveNoHead(GameTestHelper helper) {
		LivingEntity slime = target(helper, EntityTypes.SLIME, new Vec3(1.5, 1, 1.5), 180f);
		Vec3 top = slime.position().add(0, slime.getBbHeight(), 0);
		helper.assertFalse(Headshots.isHeadshot(helper.getLevel().registryAccess(), slime, top), "top of a slime");
		helper.succeed();
	}

	/** A cow's head sticks out at the front: only the front of its hitbox, up high, counts. */
	@GameTest
	public void cowHeadIsAtTheFront(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		LivingEntity cow = target(helper, EntityTypes.COW, new Vec3(2.5, 1, 2.5), 0f); // facing south (+Z)
		double half = cow.getBbWidth() / 2;
		double y = cow.getY() + 1.2;
		helper.assertTrue(Headshots.isHeadshot(registries, cow, new Vec3(cow.getX(), y, cow.getZ() + half)), "front, head height");
		helper.assertFalse(Headshots.isHeadshot(registries, cow, new Vec3(cow.getX(), y, cow.getZ() - half)), "back, head height");
		helper.assertFalse(Headshots.isHeadshot(registries, cow, new Vec3(cow.getX() + half, y, cow.getZ() + half - 0.05)), "side, near the front");
		helper.assertFalse(Headshots.isHeadshot(registries, cow, new Vec3(cow.getX(), cow.getY() + 0.5, cow.getZ() + half)), "front, low down");
		face(cow, 90f); // facing west (-X): the head turns with the body
		helper.assertTrue(Headshots.isHeadshot(registries, cow, new Vec3(cow.getX() - half, y, cow.getZ())), "west face once turned west");
		helper.assertFalse(Headshots.isHeadshot(registries, cow, new Vec3(cow.getX(), y, cow.getZ() + half)), "south face once turned west");
		helper.succeed();
	}

	/** Baby zombies have big heads: everything above 9 of their 15.7 px is head. */
	@GameTest
	public void babyZombiesUseTheirOwnHead(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		Zombie baby = target(helper, EntityTypes.ZOMBIE, new Vec3(1.5, 1, 1.5), 180f);
		baby.setBaby(true);
		helper.assertTrue(baby.getBbHeight() < 1f, "baby size, height " + baby.getBbHeight());
		helper.assertTrue(Headshots.isHeadshot(registries, baby, north(baby, 0.6)), "low on a baby's head");
		helper.assertFalse(Headshots.isHeadshot(registries, baby, north(baby, 0.5)), "a baby's chest");
		helper.succeed();
	}

	@GameTest
	public void noHeadshotsLyingDown(GameTestHelper helper) {
		Zombie zombie = target(helper, EntityTypes.ZOMBIE, new Vec3(1.5, 1, 1.5), 180f);
		zombie.setPose(Pose.SWIMMING);
		helper.assertFalse(Headshots.isHeadshot(helper.getLevel().registryAccess(), zombie, north(zombie, zombie.getBbHeight() - 0.1)), "swimming zombie");
		helper.succeed();
	}

	/** Mobs with no head file: from a little below the eyes up is head. */
	@GameTest
	public void guessForMobsWithoutAHeadFile(GameTestHelper helper) {
		Zombie zombie = target(helper, EntityTypes.ZOMBIE, new Vec3(1.5, 1, 1.5), 180f);
		helper.assertTrue(Headshots.inTopOfHitbox(zombie, north(zombie, 1.75)), "at the eyes");
		helper.assertTrue(Headshots.inTopOfHitbox(zombie, north(zombie, 1.55)), "a little below the eyes");
		helper.assertFalse(Headshots.inTopOfHitbox(zombie, north(zombie, 1.3)), "shoulders");
		helper.succeed();
	}
}
