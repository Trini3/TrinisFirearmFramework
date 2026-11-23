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
 * Per-stack gun state, stored in the {@code tff:gun_state} component. Times are level game
 * times. The component ignores the hand swap animation, so updating it every shot does not
 * make the gun bob in first person.
 *
 * @param rounds      rounds currently loaded
 * @param ammo        ammo type of the loaded rounds (empty when unloaded)
 * @param capacity    cached capacity from the definition, so the client can draw the ammo bar
 * @param lastShot    game time of the last shot
 * @param reloadStart game time the current reload step started (0 = not reloading)
 * @param reloadEnd   game time the current reload step finishes (0 = not reloading)
 */
public record GunState(int rounds, Optional<ResourceKey<AmmoDefinition>> ammo, int capacity, long lastShot, long reloadStart, long reloadEnd) {
	public static final GunState EMPTY = new GunState(0, Optional.empty(), 0, Long.MIN_VALUE / 2, 0L, 0L);

	public static final Codec<GunState> CODEC = RecordCodecBuilder.create(i -> i.group(
		ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("rounds", 0).forGetter(GunState::rounds),
		ResourceKey.codec(TffRegistries.AMMO).optionalFieldOf("ammo").forGetter(GunState::ammo),
		ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("capacity", 0).forGetter(GunState::capacity)
	).apply(i, (rounds, ammo, capacity) -> new GunState(rounds, ammo, capacity, EMPTY.lastShot, 0L, 0L)));
	// Timing fields are transient on disk (a saved mid-reload simply restarts) but synced to clients.

	public static final StreamCodec<RegistryFriendlyByteBuf, GunState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, GunState::rounds,
		ByteBufCodecs.optional(ResourceKey.streamCodec(TffRegistries.AMMO)), GunState::ammo,
		ByteBufCodecs.VAR_INT, GunState::capacity,
		ByteBufCodecs.VAR_LONG, GunState::lastShot,
		ByteBufCodecs.VAR_LONG, GunState::reloadStart,
		ByteBufCodecs.VAR_LONG, GunState::reloadEnd,
		GunState::new);

	public boolean reloading() {
		return this.reloadEnd > 0L;
	}

	public boolean isFull() {
		return this.rounds >= this.capacity;
	}

	/** 0 to 1 progress of the current reload step, or 0 when not reloading. */
	public float reloadProgress(long gameTime) {
		if (!this.reloading() || this.reloadEnd <= this.reloadStart) {
			return 0f;
		}
		return Math.clamp((float) (gameTime - this.reloadStart) / (float) (this.reloadEnd - this.reloadStart), 0f, 1f);
	}

	public GunState withRounds(int rounds, Optional<ResourceKey<AmmoDefinition>> ammo) {
		return new GunState(rounds, rounds > 0 ? ammo : Optional.empty(), this.capacity, this.lastShot, this.reloadStart, this.reloadEnd);
	}

	public GunState withCapacity(int capacity) {
		return new GunState(Math.min(this.rounds, capacity), this.ammo, capacity, this.lastShot, this.reloadStart, this.reloadEnd);
	}

	public GunState withShot(long gameTime) {
		int left = Math.max(0, this.rounds - 1);
		return new GunState(left, left > 0 ? this.ammo : Optional.empty(), this.capacity, gameTime, 0L, 0L);
	}

	public GunState withReload(long start, long end) {
		return new GunState(this.rounds, this.ammo, this.capacity, this.lastShot, start, end);
	}

	public GunState withoutReload() {
		return this.withReload(0L, 0L);
	}
}
