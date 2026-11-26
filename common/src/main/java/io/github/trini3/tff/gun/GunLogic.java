package io.github.trini3.tff.gun;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.api.ProjectileBehavior;
import io.github.trini3.tff.api.TffApi;
import io.github.trini3.tff.data.AmmoDefinition;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.data.SoundCue;
import io.github.trini3.tff.projectile.BulletEntity;
import io.github.trini3.tff.registry.TffEntities;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Gun rules: firing, reloading and state upkeep. The server is authoritative; the shooter's client
 * runs the same checks ({@link #findAmmo}, {@link GunMath#cooledDown}) to predict its own shots and
 * reloads, and plays its own sounds and effects right away. That is why sounds and smoke sent from
 * here skip the shooter.
 */
public final class GunLogic {
	/** Ticks of fire-rate slack, so a client firing exactly on time is never refused because of jitter. */
	public static final int COOLDOWN_TOLERANCE = 1;
	/**
	 * Ticks before the end of a magazine reload in which a shot finishes the reload instead of being
	 * refused. The shooter's client starts (and so ends) its reload a little earlier than the server.
	 */
	public static final int RELOAD_TOLERANCE = 3;

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
			if (now < state.reloadEnd() - RELOAD_TOLERANCE) {
				return FireResult.RELOADING;
			}
			finishReloadStep(player, stack, level, gun, state);
			state = GunItem.state(stack);
		}
		Optional<AmmoDefinition> ammo = state.ammo().flatMap(key -> TffRegistries.ammo(level.registryAccess(), key));
		if (state.rounds() <= 0 || ammo.isEmpty()) {
			// Remember the click so an empty gun clicks at the fire rate, not every tick.
			GunItem.setState(stack, state.withDryFire(now));
			gun.sounds().dryFire().ifPresent(sound -> playToOthers(level, player, sound, 0.8f, 1f));
			return FireResult.EMPTY;
		}

		boolean aiming = GunItem.isAiming(player, stack);
		// Firing interrupts a per-round reload and always clears reload timing.
		GunItem.setState(stack, state.withShot(now));
		spawnProjectiles(level, player, stack, gun, ammo.get(), yRot, xRot, aiming);
		gun.sounds().fire().ifPresent(sound -> playToOthers(level, player, sound, 1f, 1f));
		muzzleSmokeForOthers(level, player, gun, yRot, xRot, aiming);
		return FireResult.FIRED;
	}

	private static void spawnProjectiles(ServerLevel level, Player player, ItemStack stack, GunDefinition gun, AmmoDefinition ammo,
										 float yRot, float xRot, boolean aiming) {
		ProjectileBehavior behavior = TffApi.projectileBehavior(ammo.behavior()).orElseGet(() -> {
			Tff.LOGGER.warn("Unknown projectile behavior {}, using tff:bullet", ammo.behavior());
			return ProjectileBehavior.BULLET;
		});
		float spread = GunStats.spread(gun, stack, level.registryAccess(), aiming);
		Vec3 aim = Vec3.directionFromRotation(xRot, yRot).normalize();
		Vec3 origin = player.getEyePosition();
		RandomSource random = player.getRandom();
		float speed = ammo.speed() * gun.velocityMultiplier();

		List<Vec2> pattern = ammo.pattern();
		if (pattern.isEmpty()) {
			// No pattern: every pellet lands somewhere random inside the spread.
			for (int i = 0; i < ammo.pellets(); i++) {
				spawnBullet(level, player, gun, ammo, behavior, origin, GunMath.spread(aim, spread, random.nextDouble(), random.nextDouble()), speed);
			}
			return;
		}
		// A pattern keeps its shape; the spread only moves where the whole pattern lands.
		Vec3 centre = GunMath.spread(aim, spread, random.nextDouble(), random.nextDouble());
		Vec3 right = GunMath.viewRight(yRot);
		Vec3 up = GunMath.viewUp(xRot, yRot);
		for (Vec2 point : pattern) {
			spawnBullet(level, player, gun, ammo, behavior, origin, GunMath.offset(centre, right, up, point.x, point.y), speed);
		}
	}

	private static void spawnBullet(ServerLevel level, Player player, GunDefinition gun, AmmoDefinition ammo, ProjectileBehavior behavior,
									Vec3 origin, Vec3 dir, float speed) {
		BulletEntity bullet = new BulletEntity(TffEntities.BULLET.get(), level);
		bullet.setOwner(player);
		bullet.configure(ammo, behavior, gun.damageMultiplier(), origin);
		// The bullet's position is where its hit test starts, so start it exactly at the eye: the
		// pellets then fly along the crosshair line.
		bullet.setPos(origin);
		bullet.setDeltaMovement(dir.scale(speed));
		bullet.alignRotationToMovement();
		level.addFreshEntity(bullet);
	}

	/** Muzzle smoke for everyone but the shooter, whose client puffs its own at the gun it sees. */
	private static void muzzleSmokeForOthers(ServerLevel level, Player player, GunDefinition gun, float yRot, float xRot, boolean aiming) {
		GunDefinition.Muzzle muzzle = gun.muzzle();
		if (muzzle.count() <= 0) {
			return;
		}
		float side = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
		Vec3 pos = GunMath.muzzlePosition(player.getEyePosition(), yRot, xRot, side, aiming);
		for (ServerPlayer viewer : level.players()) {
			if (viewer != player) {
				level.sendParticles(viewer, muzzle.particle(), false, false, pos.x, pos.y, pos.z, muzzle.count(), 0.04, 0.04, 0.04, muzzle.speed());
			}
		}
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
		ReloadResult check = checkReload(player, gun, state, level.registryAccess());
		if (check != ReloadResult.STARTED) {
			return check;
		}
		long now = level.getGameTime();
		GunItem.setState(stack, state.withReload(now, now + gun.reload().ticks(), 0));
		playCues(level, player, gun.reload().sounds(), 0);
		return ReloadResult.STARTED;
	}

	/** Whether a reload could start now. Shared with the client, which predicts its own reloads. */
	public static ReloadResult checkReload(Player player, GunDefinition gun, GunState state, HolderLookup.Provider registries) {
		if (state.reloading()) {
			return ReloadResult.ALREADY_RELOADING;
		}
		if (state.rounds() >= gun.capacity()) {
			return ReloadResult.FULL;
		}
		if (findAmmo(player, gun, state, registries).isEmpty()) {
			return ReloadResult.NO_AMMO;
		}
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
			// Keeps the client's ammo bar right, also after a datapack changes the capacity.
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
		long now = level.getGameTime();
		long elapsed = now - state.reloadStart();
		if (elapsed >= 1) {
			// Tick 0 cues were played when the step started.
			playCues(level, player, state.reloadStep() == 0 ? gun.reload().sounds() : gun.reload().roundSounds(), elapsed);
		}
		if (now >= state.reloadEnd()) {
			finishReloadStep(player, stack, level, gun, state);
		}
	}

	private static void finishReloadStep(Player player, ItemStack stack, ServerLevel level, GunDefinition gun, GunState state) {
		Optional<AmmoMatch> match = findAmmo(player, gun, state, level.registryAccess());
		if (match.isEmpty()) {
			GunItem.setState(stack, state.withoutReload());
			return;
		}
		AmmoMatch ammo = match.get();
		boolean perRound = gun.reload().mode() == GunDefinition.ReloadMode.PER_ROUND;
		int wanted = perRound ? 1 : gun.capacity() - state.rounds();
		int loaded = ammo.take(wanted);
		GunState next = state.withRounds(state.rounds() + loaded, Optional.of(ammo.key()));

		long now = level.getGameTime();
		if (perRound && loaded > 0 && next.rounds() < gun.capacity() && findAmmo(player, gun, next, level.registryAccess()).isPresent()) {
			GunItem.setState(stack, next.withReload(now, now + gun.reload().roundTicks(), state.reloadStep() + 1));
			playCues(level, player, gun.reload().roundSounds(), 0);
		} else {
			GunItem.setState(stack, next.withoutReload());
		}
	}

	/**
	 * An ammo type the player can load, and the stack it comes from (empty for players with
	 * infinite materials, who load for free).
	 */
	public record AmmoMatch(ResourceKey<AmmoDefinition> key, Optional<ItemStack> source) {
		/** Takes up to {@code wanted} rounds and returns how many were taken. */
		int take(int wanted) {
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
	 * Finds ammo for a reload. A partly loaded gun only takes more of the same type; an empty gun
	 * takes the first type, in the definition's order, found in the inventory. Players with infinite
	 * materials (creative) load the first type for free.
	 */
	public static Optional<AmmoMatch> findAmmo(Player player, GunDefinition gun, GunState state, HolderLookup.Provider registries) {
		Inventory inventory = player.getInventory();
		boolean partlyLoaded = state.rounds() > 0 && state.ammo().isPresent();
		for (ResourceKey<AmmoDefinition> key : gun.ammo()) {
			if (partlyLoaded && !state.ammo().get().equals(key)) {
				continue;
			}
			Optional<AmmoDefinition> ammo = TffRegistries.ammo(registries, key);
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
			return Optional.of(new AmmoMatch(partlyLoaded ? state.ammo().get() : gun.ammo().getFirst(), Optional.empty()));
		}
		return Optional.empty();
	}

	private static void playCues(ServerLevel level, Player player, List<SoundCue> cues, long tick) {
		for (SoundCue cue : SoundCue.at(cues, tick)) {
			playToOthers(level, player, cue.sound(), cue.volume(), cue.pitch());
		}
	}

	/** Plays a sound at the player for everyone nearby except the player, whose client plays it already. */
	private static void playToOthers(ServerLevel level, Player player, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(player, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume,
			pitch * (1.0f + Mth.randomBetween(player.getRandom(), -0.04f, 0.04f)));
	}
}
