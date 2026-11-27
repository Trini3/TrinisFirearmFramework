package io.github.trini3.tff.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/**
 * Per-stack gun state, stored in the {@code tff:gun_state} component. Times are level game times.
 * The component ignores the hand swap animation, so changing it on every shot does not replay the
 * equip motion in first person. Timing fields are synced to clients (so other players see reloads
 * and muzzle flashes) but not saved: a world saved mid-reload just loses that reload.
 *
 * @param rounds      rounds loaded
 * @param ammo        ammo type of the loaded rounds (empty when unloaded)
 * @param capacity    capacity copied from the definition, so the client can draw the ammo bar
 * @param lastShot    game time of the last shot (or dry fire)
 * @param reloadStart game time the current reload step started (0 = not reloading)
 * @param reloadEnd   game time the current reload step ends (0 = not reloading)
 * @param reloadStep  0 for the first (or only) reload step, then 1, 2, ... for each further round
 */
public record GunState(int rounds, Optional<ResourceKey<AmmoDefinition>> ammo, int capacity, long lastShot, long reloadStart, long reloadEnd, int reloadStep) {
	public static final long NEVER = Long.MIN_VALUE / 2;
	public static final GunState EMPTY = new GunState(0, Optional.empty(), 0, NEVER, 0L, 0L, 0);

	public static final Codec<GunState> CODEC = RecordCodecBuilder.create(i -> i.group(
		ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("rounds", 0).forGetter(GunState::rounds),
		ResourceKey.codec(TffRegistries.AMMO).optionalFieldOf("ammo").forGetter(GunState::ammo),
		ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("capacity", 0).forGetter(GunState::capacity)
	).apply(i, (rounds, ammo, capacity) -> new GunState(rounds, ammo, capacity, NEVER, 0L, 0L, 0)));

	public static final StreamCodec<RegistryFriendlyByteBuf, GunState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, GunState::rounds,
		ByteBufCodecs.optional(ResourceKey.streamCodec(TffRegistries.AMMO)), GunState::ammo,
		ByteBufCodecs.VAR_INT, GunState::capacity,
		ByteBufCodecs.VAR_LONG, GunState::lastShot,
		ByteBufCodecs.VAR_LONG, GunState::reloadStart,
		ByteBufCodecs.VAR_LONG, GunState::reloadEnd,
		ByteBufCodecs.VAR_INT, GunState::reloadStep,
		GunState::new);

	public boolean reloading() {
		return this.reloadEnd > 0L;
	}

	public boolean isFull() {
		return this.rounds >= this.capacity;
	}

	/** 0 to 1 progress of the current reload step, or 0 when not reloading. */
	public float reloadProgress(float gameTime) {
		if (!this.reloading() || this.reloadEnd <= this.reloadStart) {
			return 0f;
		}
		return Math.clamp((gameTime - this.reloadStart) / (float) (this.reloadEnd - this.reloadStart), 0f, 1f);
	}

	public GunState withRounds(int rounds, Optional<ResourceKey<AmmoDefinition>> ammo) {
		return new GunState(rounds, rounds > 0 ? ammo : Optional.empty(), this.capacity, this.lastShot, this.reloadStart, this.reloadEnd, this.reloadStep);
	}

	public GunState withCapacity(int capacity) {
		return new GunState(Math.min(this.rounds, capacity), this.ammo, capacity, this.lastShot, this.reloadStart, this.reloadEnd, this.reloadStep);
	}

	/** One round fired at {@code gameTime}. Firing always ends a reload. */
	public GunState withShot(long gameTime) {
		int left = Math.max(0, this.rounds - 1);
		return new GunState(left, left > 0 ? this.ammo : Optional.empty(), this.capacity, gameTime, 0L, 0L, 0);
	}

	/** The trigger was pulled on an empty gun: no round, but the click counts toward the fire rate. */
	public GunState withDryFire(long gameTime) {
		return new GunState(0, Optional.empty(), this.capacity, gameTime, this.reloadStart, this.reloadEnd, this.reloadStep);
	}

	/** Starts reload step {@code step} (0 = the first) running from {@code start} to {@code end}. */
	public GunState withReload(long start, long end, int step) {
		return new GunState(this.rounds, this.ammo, this.capacity, this.lastShot, start, end, step);
	}

	public GunState withReload(long start, long end) {
		return this.withReload(start, end, 0);
	}

	public GunState withoutReload() {
		return this.withReload(0L, 0L, 0);
	}
}
