package io.github.trini3.tff.client.gun;

import io.github.trini3.tff.data.GunState;
import io.github.trini3.tff.gun.GunItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Animation inputs for guns held by other players (and mobs), read from the synced gun state:
 * a change of {@code lastShot} is a shot (or a dry click, when the gun had no rounds), and the
 * reload window runs on the world clock. Only bones that also animate in third person move.
 */
public final class RemoteGuns {
	/** A shot older than this when first seen is not flashed (the holder just came into view). */
	private static final long STALE_TICKS = 10;
	private static final long FORGET_TICKS = 200;

	private static final class Entry {
		long lastShot;
		int rounds;
		double shotTime = Double.NEGATIVE_INFINITY;
		double dryTime = Double.NEGATIVE_INFINITY;
		long shots;
		long seen;
	}

	private static final Int2ObjectMap<Entry> ENTRIES = new Int2ObjectOpenHashMap<>();

	private RemoteGuns() {}

	public static GunView fill(GunView view, LivingEntity owner, ItemStack stack, ClientLevel level, double now, float partialTick) {
		GunState state = GunItem.state(stack);
		long gameTime = level.getGameTime();
		Entry entry = ENTRIES.get(owner.getId());
		if (entry == null) {
			entry = new Entry();
			entry.lastShot = state.lastShot();
			entry.rounds = state.rounds();
			ENTRIES.put(owner.getId(), entry);
		}
		if (state.lastShot() != entry.lastShot) {
			if (gameTime - state.lastShot() <= STALE_TICKS) {
				if (entry.rounds > 0) {
					entry.shotTime = now;
					entry.shots++;
				} else {
					entry.dryTime = now;
				}
			}
			entry.lastShot = state.lastShot();
		}
		entry.rounds = state.rounds();
		entry.seen = gameTime;

		view.clear();
		boolean aiming = GunItem.isAiming(owner, stack);
		view.aim = aiming ? 1f : 0f;
		view.empty = state.rounds() <= 0 && !state.reloading() ? 1f : 0f;
		if (entry.shotTime > Double.NEGATIVE_INFINITY) {
			view.fireAge = (float) Math.max(0.0, now - entry.shotTime);
			view.fireAimed = aiming;
			view.shotNumber = entry.shots;
		}
		if (entry.dryTime > Double.NEGATIVE_INFINITY) {
			view.dryAge = (float) Math.max(0.0, now - entry.dryTime);
		}
		if (state.reloading()) {
			float length = Math.max(1L, state.reloadEnd() - state.reloadStart());
			view.reloadProgress = Mth.clamp((gameTime - state.reloadStart() + partialTick) / length, 0f, 1f);
			view.reloadWeight = 1f;
			view.reloadStep = state.reloadStep();
		}
		return view;
	}

	/** Forgets holders that haven't been drawn for a while. */
	public static void tick(ClientLevel level) {
		long gameTime = level.getGameTime();
		if (gameTime % 100 == 0) {
			ENTRIES.values().removeIf(entry -> gameTime - entry.seen > FORGET_TICKS || entry.seen > gameTime);
		}
	}

	public static void clear() {
		ENTRIES.clear();
	}
}
