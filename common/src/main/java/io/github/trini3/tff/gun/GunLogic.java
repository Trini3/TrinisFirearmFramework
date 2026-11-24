package io.github.trini3.tff.gun;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.api.ProjectileBehavior;
import io.github.trini3.tff.api.TffApi;
import io.github.trini3.tff.data.AmmoDefinition;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.projectile.BulletEntity;
import io.github.trini3.tff.registry.TffEntities;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server-side gun rules: firing, reloading and state upkeep. The server is authoritative. */
public final class GunLogic {
	/** Ticks of fire-rate slack, so a client firing exactly on time is never rejected because of jitter. */
	private static final int COOLDOWN_TOLERANCE = 1;

	public enum FireResult { FIRED, NOT_A_GUN, COOLDOWN, RELOADING, EMPTY }

	public enum ReloadResult { STARTED, NOT_A_GUN, ALREADY_RELOADING, FULL, NO_AMMO }

	private GunLogic() {}

	/**
	 * Fires the gun in the player's main hand.
	 *
	 * @param yRot aim yaw sent by the client (like vanilla's use-item packet)
	 * @param xRot aim pitch sent by the client
	 */
	public static FireResult tryFire(Player player, float yRot, float xRot) {
		if (!(player.level() instanceof ServerLevel level)) {
			return FireResult.NOT_A_GUN;
		}
		ItemStack stack = player.getMainHandItem();
		if (!GunItem.isGun(stack) || player.isSpectator()) {
			return FireResult.NOT_A_GUN;
		}
		Optional<GunDefinition> maybeGun = GunItem.definition(stack, level.registryAccess());
		if (maybeGun.isEmpty()) {
			return FireResult.NOT_A_GUN;
		}
		GunDefinition gun = maybeGun.get();
		GunState state = GunItem.state(stack);
		long now = level.getGameTime();

		if (!GunMath.cooledDown(now, state.lastShot(), gun.fireInterval(), COOLDOWN_TOLERANCE)) {
			return FireResult.COOLDOWN;
		}
		if (state.reloading() && gun.reload().mode() == GunDefinition.ReloadMode.MAGAZINE) {
			return FireResult.RELOADING;
		}
		Optional<AmmoDefinition> ammo = state.ammo().flatMap(key -> TffRegistries.ammo(level.registryAccess(), key));
		if (state.rounds() <= 0 || ammo.isEmpty()) {
			// Remember the click so spamming an empty gun clicks at the fire rate, not every tick.
			GunItem.setState(stack, new GunState(0, Optional.empty(), state.capacity(), now, 0L, 0L));
			playSound(level, player, gun.sounds().empty(), 0.8f);
			return FireResult.EMPTY;
		}

		// Firing interrupts a per-round reload, and always clears reload timing.
		GunItem.setState(stack, state.withShot(now));
		spawnProjectiles(level, player, gun, ammo.get(), yRot, xRot);
		playSound(level, player, gun.sounds().fire(), 1.0f);
		muzzleSmoke(level, player, gun, yRot, xRot);
		return FireResult.FIRED;
	}

	private static void spawnProjectiles(ServerLevel level, Player player, GunDefinition gun, AmmoDefinition ammo, float yRot, float xRot) {
		ProjectileBehavior behavior = TffApi.projectileBehavior(ammo.behavior()).orElseGet(() -> {
			Tff.LOGGER.warn("Unknown projectile behavior {}, using tff:bullet", ammo.behavior());
			return ProjectileBehavior.BULLET;
		});
		ItemStack stack = player.getMainHandItem();
		float spread = GunStats.spread(gun, stack, level.registryAccess(), GunItem.isAiming(player, stack));
		Vec3 aim = Vec3.directionFromRotation(xRot, yRot);
		Vec3 origin = player.getEyePosition();
		RandomSource random = player.getRandom();
		float speed = ammo.speed() * gun.velocityMultiplier();

		for (int i = 0; i < ammo.pellets(); i++) {
			Vec3 dir = GunMath.spread(aim, spread, random.nextDouble(), random.nextDouble());
			BulletEntity bullet = new BulletEntity(TffEntities.BULLET.get(), level);
			bullet.setOwner(player);
			bullet.configure(ammo, behavior, gun.damageMultiplier(), origin);
			bullet.setPos(origin.x, origin.y - bullet.getBbHeight() / 2.0, origin.z);
			bullet.setDeltaMovement(dir.scale(speed));
			bullet.alignRotationToMovement();
			level.addFreshEntity(bullet);
		}
	}

	/** Puffs the gun's muzzle particles just in front of the barrel (vanilla particles: shader safe). */
	private static void muzzleSmoke(ServerLevel level, Player player, GunDefinition gun, float yRot, float xRot) {
		GunDefinition.Muzzle muzzle = gun.muzzle();
		if (muzzle.count() <= 0) {
			return;
		}
		Vec3 pos = muzzlePosition(player, yRot, xRot);
		level.sendParticles(muzzle.particle(), pos.x, pos.y, pos.z, muzzle.count(), 0.04, 0.04, 0.04, muzzle.speed());
	}

	/** Rough barrel tip: in front of the eyes, a little to the main-hand side and down. */
	public static Vec3 muzzlePosition(Player player, float yRot, float xRot) {
		Vec3 forward = Vec3.directionFromRotation(xRot, yRot);
		Vec3 right = Vec3.directionFromRotation(0f, yRot + 90f);
		float side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1f : -1f;
		boolean aiming = GunItem.isAiming(player, player.getMainHandItem());
		return player.getEyePosition()
			.add(forward.scale(1.0))
			.add(right.scale(aiming ? 0.0 : 0.3 * side))
			.add(0, aiming ? -0.08 : -0.2, 0);
	}

	/** Starts reloading the gun in the player's main hand. */
	public static ReloadResult startReload(Player player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return ReloadResult.NOT_A_GUN;
		}
		ItemStack stack = player.getMainHandItem();
		Optional<GunDefinition> maybeGun = GunItem.isGun(stack) ? GunItem.definition(stack, level.registryAccess()) : Optional.empty();
		if (maybeGun.isEmpty() || player.isSpectator()) {
			return ReloadResult.NOT_A_GUN;
		}
		GunDefinition gun = maybeGun.get();
		GunState state = GunItem.state(stack).withCapacity(gun.capacity());
		if (state.reloading()) {
			return ReloadResult.ALREADY_RELOADING;
		}
		if (state.rounds() >= gun.capacity()) {
			return ReloadResult.FULL;
		}
		if (findAmmo(player, gun, state, level).isEmpty()) {
			return ReloadResult.NO_AMMO;
		}
		long now = level.getGameTime();
		GunItem.setState(stack, state.withReload(now, now + gun.reload().ticks()));
		playSound(level, player, gun.sounds().reloadStart(), 1.0f);
		return ReloadResult.STARTED;
	}

	/** Called every server tick for each gun in a player's inventory. */
	public static void inventoryTick(Player player, ItemStack stack, ServerLevel level, boolean selected) {
		Optional<GunDefinition> maybeGun = GunItem.definition(stack, level.registryAccess());
		if (maybeGun.isEmpty()) {
			return;
		}
		GunDefinition gun = maybeGun.get();
		GunState state = GunItem.state(stack);
		if (state.capacity() != gun.capacity()) {
			// Keeps the client's ammo bar right, including after a datapack changes capacity.
			state = state.withCapacity(gun.capacity());
			GunItem.setState(stack, state);
		}
		if (!state.reloading()) {
			return;
		}
		if (!selected) {
			GunItem.setState(stack, state.withoutReload());
			return;
		}
		if (level.getGameTime() >= state.reloadEnd()) {
			finishReloadStep(player, stack, level, gun, state);
		}
	}

	private static void finishReloadStep(Player player, ItemStack stack, ServerLevel level, GunDefinition gun, GunState state) {
		Optional<AmmoMatch> match = findAmmo(player, gun, state, level);
		if (match.isEmpty()) {
			GunItem.setState(stack, state.withoutReload());
			return;
		}
		AmmoMatch ammo = match.get();
		boolean perRound = gun.reload().mode() == GunDefinition.ReloadMode.PER_ROUND;
		int wanted = perRound ? 1 : gun.capacity() - state.rounds();
		int loaded = ammo.take(player, wanted);
		GunState next = state.withRounds(state.rounds() + loaded, Optional.of(ammo.key()));

		long now = level.getGameTime();
		if (perRound && loaded > 0 && next.rounds() < gun.capacity() && findAmmo(player, gun, next, level).isPresent()) {
			GunItem.setState(stack, next.withReload(now, now + gun.reload().roundTicks()));
			playSound(level, player, gun.sounds().reloadRound(), 1.0f);
		} else {
			GunItem.setState(stack, next.withoutReload());
			if (perRound) {
				playSound(level, player, gun.sounds().reloadRound(), 1.0f);
			}
			playSound(level, player, gun.sounds().reloadEnd(), 1.0f);
		}
	}

	/** An ammo type the player can load, and where it comes from. */
	private record AmmoMatch(ResourceKey<AmmoDefinition> key, Optional<ItemStack> source) {
		/** Takes up to {@code wanted} rounds and returns how many were taken. Creative takes nothing. */
		int take(Player player, int wanted) {
			if (this.source.isEmpty()) {
				return wanted;
			}
			ItemStack from = this.source.get();
			int taken = Math.min(wanted, from.getCount());
			from.shrink(taken);
			return taken;
		}
	}

	/**
	 * Finds ammo for a reload. A partly loaded gun only accepts more of the same type; an empty gun
	 * takes the first type, in the definition's order, found in the inventory. Players with
	 * infinite materials (creative) load the first type for free.
	 */
	private static Optional<AmmoMatch> findAmmo(Player player, GunDefinition gun, GunState state, ServerLevel level) {
		Inventory inventory = player.getInventory();
		for (ResourceKey<AmmoDefinition> key : gun.ammo()) {
			if (state.rounds() > 0 && state.ammo().isPresent() && !state.ammo().get().equals(key)) {
				continue;
			}
			Optional<AmmoDefinition> ammo = TffRegistries.ammo(level.registryAccess(), key);
			if (ammo.isEmpty()) {
				continue;
			}
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack candidate = inventory.getItem(slot);
				if (!candidate.isEmpty() && ammo.get().matches(candidate)) {
					return Optional.of(new AmmoMatch(key, player.hasInfiniteMaterials() ? Optional.empty() : Optional.of(candidate)));
				}
			}
		}
		if (player.hasInfiniteMaterials()) {
			ResourceKey<AmmoDefinition> key = state.rounds() > 0 && state.ammo().isPresent() ? state.ammo().get() : gun.ammo().getFirst();
			return Optional.of(new AmmoMatch(key, Optional.empty()));
		}
		return Optional.empty();
	}

	private static void playSound(ServerLevel level, Player player, Optional<Holder<SoundEvent>> sound, float volume) {
		sound.ifPresent(s -> level.playSound(null, player.getX(), player.getY(), player.getZ(), s, SoundSource.PLAYERS, volume,
			1.0f + Mth.randomBetween(player.getRandom(), -0.04f, 0.04f)));
	}
}
