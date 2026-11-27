package io.github.trini3.tff.client.gun;

import io.github.trini3.tff.anim.Easing;
import io.github.trini3.tff.client.ClientClock;
import io.github.trini3.tff.client.platform.TffClientPlatform;
import io.github.trini3.tff.data.GunDefinition;
import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.gun.GunItem;
import io.github.trini3.tff.gun.GunLogic;
import io.github.trini3.tff.gun.GunStats;
import io.github.trini3.tff.network.FireGunPayload;
import io.github.trini3.tff.network.ReloadGunPayload;
import io.github.trini3.tff.registry.TffComponents;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The local player's gun, predicted. Shots and reloads start here the moment the player asks,
 * with their sounds, smoke, recoil and animation, and the request goes to the server, which
 * decides for real. The synced gun state then confirms (or corrects) the prediction:
 * <ul>
 *   <li>shots: each confirmed shot lowers the server's round count; unconfirmed shots are
 *       forgotten after {@link #CONFIRM_TICKS}</li>
 *   <li>reloads: the server's reload start confirms ours; if none comes, or the server stops the
 *       reload early, ours fades out</li>
 * </ul>
 * Animation times use {@link ClientClock}, so they never jump when the server corrects the
 * world time. Only the main-hand gun is tracked; switching slots resets everything.
 */
public final class LocalGun {
	/** Ticks to wait for the server to confirm a predicted shot or reload. */
	static final int CONFIRM_TICKS = 20;
	/** Ticks left on a reload step below which a server stop counts as the normal end. */
	private static final int END_SLACK = 3;
	private static final float AIM_SECONDS = 0.2f;
	private static final float SPRINT_SECONDS = 0.25f;
	private static final float CANCEL_FADE_SECONDS = 0.2f;
	private static final long NEVER = Long.MIN_VALUE / 4;

	private static int slot = -1;
	private static @Nullable ResourceKey<GunDefinition> gunKey;
	private static GunState server = GunState.EMPTY;

	private static int pendingShots;
	private static long lastShotTick = NEVER;
	private static long lastFireRequestTick = NEVER;
	private static double lastShotTime = Double.NEGATIVE_INFINITY;
	private static boolean lastShotAimed;
	private static long shotNumber;
	private static double lastDryTime = Double.NEGATIVE_INFINITY;
	private static long assumeFullUntil = NEVER;

	private static boolean reloading;
	private static long reloadStartTick;
	private static int reloadTicks;
	private static int reloadStep;
	private static int roundsAtReloadStart;
	private static boolean awaitingReloadConfirm;
	private static long reloadRequestTick = NEVER;
	private static long lastReloadEndTick = NEVER;
	private static int lastReloadStep = -1;
	private static double cancelTime = Double.NEGATIVE_INFINITY;
	private static float cancelProgress;
	private static int cancelStep;

	private static float aimRamp;
	private static float sprintRamp;
	private static float emptyRamp;
	private static double lastFrame = Double.NaN;

	private static final Vector3f MUZZLE_VIEW = new Vector3f();
	private static long muzzleFrameTick = NEVER;

	private LocalGun() {}

	private static void reset() {
		slot = -1;
		gunKey = null;
		server = GunState.EMPTY;
		pendingShots = 0;
		lastShotTick = NEVER;
		lastFireRequestTick = NEVER;
		lastShotTime = Double.NEGATIVE_INFINITY;
		lastDryTime = Double.NEGATIVE_INFINITY;
		assumeFullUntil = NEVER;
		reloading = false;
		awaitingReloadConfirm = false;
		lastReloadStep = -1;
		lastReloadEndTick = NEVER;
		cancelTime = Double.NEGATIVE_INFINITY;
		aimRamp = 0f;
		sprintRamp = 0f;
		emptyRamp = 0f;
		lastFrame = Double.NaN;
		muzzleFrameTick = NEVER;
	}

	/** Whether {@code stack} is the gun this class tracks (the local player's main-hand gun). */
	public static boolean tracks(ItemStack stack) {
		return gunKey != null && GunItem.isGun(stack) && gunKey.equals(stack.get(TffComponents.GUN.get()));
	}

	/** Called at the start of every client tick that isn't paused. */
	public static void tick(@Nullable LocalPlayer player) {
		if (player == null || player.isSpectator()) {
			if (gunKey != null) {
				reset();
			}
			return;
		}
		ItemStack stack = player.getMainHandItem();
		Optional<GunDefinition> maybeGun = GunItem.isGun(stack) ? GunItem.definition(stack, player.registryAccess()) : Optional.empty();
		if (maybeGun.isEmpty()) {
			if (gunKey != null) {
				reset();
			}
			return;
		}
		GunDefinition gun = maybeGun.get();
		ResourceKey<GunDefinition> key = Objects.requireNonNull(stack.get(TffComponents.GUN.get()));
		int selected = player.getInventory().getSelectedSlot();
		GunState state = GunItem.state(stack);
		if (selected != slot || !key.equals(gunKey)) {
			reset();
			slot = selected;
			gunKey = key;
			server = state;
		}
		long now = ClientClock.ticks();
		reconcile(player, gun, state, now);
		advanceReload(player, gun, now);
	}

	/** Lines the prediction up with the server's latest state. */
	private static void reconcile(LocalPlayer player, GunDefinition gun, GunState state, long now) {
		if (state.rounds() < server.rounds()) {
			pendingShots = Math.max(0, pendingShots - (server.rounds() - state.rounds()));
		}
		if (state.rounds() > server.rounds()) {
			// A reload landed on the server.
			assumeFullUntil = NEVER;
		}
		if (pendingShots > 0 && now - lastShotTick > CONFIRM_TICKS) {
			// The server refused (or lost) a shot: show its count again.
			pendingShots = 0;
		}

		boolean serverStarted = state.reloading() && (state.reloadStart() != server.reloadStart() || !server.reloading());
		if (serverStarted) {
			if (awaitingReloadConfirm) {
				awaitingReloadConfirm = false;
			} else if (!reloading && !(state.reloadStep() <= lastReloadStep && now - lastReloadEndTick <= CONFIRM_TICKS)) {
				// The server started a reload (or a further round) that we didn't predict: follow it.
				startStep(player, gun, now, state.reloadStep(), (int) Math.max(1L, state.reloadEnd() - state.reloadStart()),
					state.rounds() - state.reloadStep());
			}
		}
		if (server.reloading() && !state.reloading() && reloading && !awaitingReloadConfirm) {
			// The server stopped. Near the end of our step that's the normal finish arriving;
			// otherwise it was cut short (out of ammo, or a datapack changed the gun).
			if (reloadStartTick + reloadTicks - now > END_SLACK) {
				cancelReload();
			}
		}
		if (awaitingReloadConfirm && now - reloadRequestTick > CONFIRM_TICKS) {
			awaitingReloadConfirm = false;
			if (reloading) {
				cancelReload();
			}
		}
		server = state;
	}

	private static void advanceReload(LocalPlayer player, GunDefinition gun, long now) {
		if (!reloading) {
			return;
		}
		long elapsed = now - reloadStartTick;
		if (elapsed >= 1) {
			GunEffects.playCues(player, reloadStep == 0 ? gun.reload().sounds() : gun.reload().roundSounds(), elapsed);
		}
		if (elapsed < reloadTicks) {
			return;
		}
		reloading = false;
		lastReloadEndTick = now;
		lastReloadStep = reloadStep;
		if (gun.reload().mode() == GunDefinition.ReloadMode.PER_ROUND) {
			int loaded = roundsAtReloadStart + reloadStep + 1;
			if (loaded < gun.capacity()) {
				startStep(player, gun, now, reloadStep + 1, gun.reload().roundTicks(), roundsAtReloadStart);
			}
		} else {
			assumeFullUntil = now + CONFIRM_TICKS;
		}
	}

	private static void startStep(LocalPlayer player, GunDefinition gun, long now, int step, int ticks, int roundsAtStart) {
		reloading = true;
		reloadStartTick = now;
		reloadTicks = Math.max(1, ticks);
		reloadStep = step;
		roundsAtReloadStart = roundsAtStart;
		cancelTime = Double.NEGATIVE_INFINITY;
		GunEffects.playCues(player, step == 0 ? gun.reload().sounds() : gun.reload().roundSounds(), 0);
	}

	private static void cancelReload() {
		cancelProgress = Mth.clamp((ClientClock.ticks() - reloadStartTick) / (float) reloadTicks, 0f, 1f);
		cancelStep = reloadStep;
		cancelTime = ClientClock.now();
		reloading = false;
		lastReloadEndTick = ClientClock.ticks();
		lastReloadStep = reloadStep;
	}

	/** Rounds the player should see: the server's count less shots it hasn't confirmed yet. */
	public static int rounds(GunDefinition gun) {
		int base = ClientClock.ticks() < assumeFullUntil ? gun.capacity() : server.rounds();
		return Math.max(0, base - pendingShots);
	}

	public static boolean isReloading() {
		return reloading;
	}

	/** Whether the player is aiming down the sights of their main-hand gun (reloading lowers them). */
	public static boolean isAiming(LocalPlayer player) {
		return gunKey != null && !reloading && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
			&& tracks(player.getUseItem());
	}

	/** The eased aim blend from the last frame drawn, 0 to 1. */
	public static float aimWeight() {
		return Easing.IN_OUT_SINE.apply(aimRamp);
	}

	/**
	 * Pulls the trigger: fires (or clicks, when empty) if the fire rate allows, predicting the
	 * result. The request goes to the server either way.
	 */
	public static GunLogic.FireResult tryFire(LocalPlayer player, ItemStack stack, GunDefinition gun) {
		long now = ClientClock.ticks();
		if (now - lastFireRequestTick < gun.fireInterval()) {
			return GunLogic.FireResult.COOLDOWN;
		}
		boolean magazine = gun.reload().mode() == GunDefinition.ReloadMode.MAGAZINE;
		if (reloading && magazine) {
			return GunLogic.FireResult.RELOADING;
		}
		lastFireRequestTick = now;
		TffClientPlatform.INSTANCE.sendToServer(new FireGunPayload(player.getYRot(), player.getXRot()));
		if (rounds(gun) <= 0) {
			lastDryTime = ClientClock.now();
			GunEffects.dryFire(player, gun);
			return GunLogic.FireResult.EMPTY;
		}
		boolean aiming = isAiming(player);
		if (reloading) {
			// Firing interrupts a per-round reload, on the server too.
			reloading = false;
			lastReloadEndTick = now;
			lastReloadStep = reloadStep;
		}
		pendingShots++;
		lastShotTick = now;
		lastShotTime = ClientClock.now();
		lastShotAimed = aiming;
		shotNumber++;
		GunEffects.fire(player, gun, aiming);
		float factor = GunStats.recoilFactor(gun, stack, player.registryAccess(), aiming);
		float yaw = gun.recoil().yaw() * factor * (player.getRandom().nextFloat() * 2f - 1f);
		CameraRecoil.kick(gun.recoil().pitch() * factor, yaw, gun.recoil().recovery());
		return GunLogic.FireResult.FIRED;
	}

	/** Starts a reload if one could start, predicting it, and asks the server. */
	public static boolean tryReload(LocalPlayer player, ItemStack stack, GunDefinition gun) {
		if (reloading || awaitingReloadConfirm) {
			return false;
		}
		int rounds = rounds(gun);
		GunState predicted = server.withoutReload().withRounds(rounds, server.ammo()).withCapacity(gun.capacity());
		if (GunLogic.checkReload(player, gun, predicted, player.registryAccess()) != GunLogic.ReloadResult.STARTED) {
			return false;
		}
		long now = ClientClock.ticks();
		awaitingReloadConfirm = true;
		reloadRequestTick = now;
		startStep(player, gun, now, 0, gun.reload().ticks(), rounds);
		TffClientPlatform.INSTANCE.sendToServer(ReloadGunPayload.INSTANCE);
		return true;
	}

	/** Fills {@code view} for the frame at {@code now} seconds, advancing the pose blends. */
	public static GunView fill(GunView view, LocalPlayer player, GunDefinition gun, double now) {
		float dt = Double.isNaN(lastFrame) ? 0f : (float) Math.clamp(now - lastFrame, 0.0, 0.25);
		lastFrame = now;
		boolean aiming = isAiming(player);
		boolean sprinting = player.isSprinting() && !aiming && !reloading;
		boolean empty = rounds(gun) <= 0 && !reloading;
		aimRamp = approach(aimRamp, aiming ? 1f : 0f, dt / AIM_SECONDS);
		sprintRamp = approach(sprintRamp, sprinting ? 1f : 0f, dt / SPRINT_SECONDS);
		// The empty pose snaps in (a hammer falls at once) but eases out (it is drawn back).
		emptyRamp = empty ? 1f : approach(emptyRamp, 0f, dt / GunView.EMPTY_FADE_SECONDS);

		view.clear();
		view.aim = Easing.IN_OUT_SINE.apply(aimRamp);
		view.sprint = Easing.IN_OUT_SINE.apply(sprintRamp);
		view.empty = emptyRamp;
		if (lastShotTime > Double.NEGATIVE_INFINITY) {
			view.fireAge = (float) Math.max(0.0, now - lastShotTime);
			view.fireAimed = lastShotAimed;
			view.shotNumber = shotNumber;
		}
		if (lastDryTime > Double.NEGATIVE_INFINITY) {
			view.dryAge = (float) Math.max(0.0, now - lastDryTime);
		}
		if (reloading) {
			float elapsed = (float) (now * 20.0 - reloadStartTick);
			view.reloadProgress = Mth.clamp(elapsed / reloadTicks, 0f, 1f);
			view.reloadWeight = 1f;
			view.reloadStep = reloadStep;
		} else if (now - cancelTime < CANCEL_FADE_SECONDS) {
			view.reloadProgress = cancelProgress;
			view.reloadWeight = 1f - (float) (now - cancelTime) / CANCEL_FADE_SECONDS;
			view.reloadStep = cancelStep;
		}
		return view;
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	/** Remembers where the first-person muzzle was drawn, in the hand's view space. */
	public static void setMuzzleInView(Vector3fc position) {
		MUZZLE_VIEW.set(position);
		muzzleFrameTick = ClientClock.ticks();
	}

	/** Where the first-person muzzle was last drawn, if that was recent. */
	public static Optional<Vector3fc> muzzleInView() {
		return ClientClock.ticks() - muzzleFrameTick <= 2 ? Optional.of(new Vector3f(MUZZLE_VIEW)) : Optional.empty();
	}
}
