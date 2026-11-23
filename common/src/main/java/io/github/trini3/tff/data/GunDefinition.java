package io.github.trini3.tff.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.tff.registry.TffRegistries;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

/**
 * One gun's stats and behaviour, loaded from {@code data/<ns>/tff/gun/<name>.json}.
 * The gun item points at its definition through the {@code tff:gun} component.
 *
 * @param ammo               accepted ammo types, in reload preference order
 * @param capacity           rounds held when fully loaded
 * @param fireMode           semi (one shot per click) or auto (hold to fire)
 * @param fireInterval       minimum ticks between shots
 * @param reload             reload timing
 * @param spread             cone half-angles in degrees
 * @param recoil             camera kick per shot in degrees
 * @param aim                aiming-down-sights settings
 * @param sounds             optional sound events
 * @param muzzle             muzzle smoke
 * @param damageMultiplier   multiplies the ammo's damage
 * @param velocityMultiplier multiplies the ammo's speed
 * @param attachments        attachments this gun accepts, per slot
 */
public record GunDefinition(
	List<ResourceKey<AmmoDefinition>> ammo,
	int capacity,
	FireMode fireMode,
	int fireInterval,
	Reload reload,
	Spread spread,
	Recoil recoil,
	Aim aim,
	Sounds sounds,
	Muzzle muzzle,
	float damageMultiplier,
	float velocityMultiplier,
	Map<AttachmentSlot, List<ResourceKey<AttachmentDefinition>>> attachments
) {
	public static final Codec<GunDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
		ExtraCodecs.nonEmptyList(ResourceKey.codec(TffRegistries.AMMO).listOf()).fieldOf("ammo").forGetter(GunDefinition::ammo),
		ExtraCodecs.POSITIVE_INT.fieldOf("capacity").forGetter(GunDefinition::capacity),
		FireMode.CODEC.optionalFieldOf("fire_mode", FireMode.SEMI).forGetter(GunDefinition::fireMode),
		ExtraCodecs.POSITIVE_INT.optionalFieldOf("fire_interval", 10).forGetter(GunDefinition::fireInterval),
		Reload.CODEC.fieldOf("reload").forGetter(GunDefinition::reload),
		Spread.CODEC.optionalFieldOf("spread", Spread.DEFAULT).forGetter(GunDefinition::spread),
		Recoil.CODEC.optionalFieldOf("recoil", Recoil.DEFAULT).forGetter(GunDefinition::recoil),
		Aim.CODEC.optionalFieldOf("aim", Aim.DEFAULT).forGetter(GunDefinition::aim),
		Sounds.CODEC.optionalFieldOf("sounds", Sounds.NONE).forGetter(GunDefinition::sounds),
		Muzzle.CODEC.optionalFieldOf("muzzle", Muzzle.DEFAULT).forGetter(GunDefinition::muzzle),
		ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("damage_multiplier", 1f).forGetter(GunDefinition::damageMultiplier),
		ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("velocity_multiplier", 1f).forGetter(GunDefinition::velocityMultiplier),
		Codec.unboundedMap(AttachmentSlot.CODEC, ResourceKey.codec(TffRegistries.ATTACHMENT).listOf())
			.optionalFieldOf("attachments", Map.of()).forGetter(GunDefinition::attachments)
	).apply(i, GunDefinition::new));

	public boolean accepts(AttachmentSlot slot, ResourceKey<AttachmentDefinition> attachment) {
		return this.attachments.getOrDefault(slot, List.of()).contains(attachment);
	}

	public enum FireMode implements StringRepresentable {
		SEMI("semi"),
		AUTO("auto");

		public static final Codec<FireMode> CODEC = StringRepresentable.fromEnum(FireMode::values);
		private final String name;

		FireMode(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public enum ReloadMode implements StringRepresentable {
		/** Loads every missing round at once after {@code ticks} (magazines, break actions). */
		MAGAZINE("magazine"),
		/** Loads one round after {@code ticks}, then one every {@code round_ticks} (tube shotguns). Firing interrupts it. */
		PER_ROUND("per_round");

		public static final Codec<ReloadMode> CODEC = StringRepresentable.fromEnum(ReloadMode::values);
		private final String name;

		ReloadMode(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public record Reload(ReloadMode mode, int ticks, int roundTicks) {
		public static final Codec<Reload> CODEC = RecordCodecBuilder.create(i -> i.group(
			ReloadMode.CODEC.optionalFieldOf("mode", ReloadMode.MAGAZINE).forGetter(Reload::mode),
			ExtraCodecs.POSITIVE_INT.fieldOf("ticks").forGetter(Reload::ticks),
			ExtraCodecs.POSITIVE_INT.optionalFieldOf("round_ticks", 10).forGetter(Reload::roundTicks)
		).apply(i, Reload::new));
	}

	public record Spread(float hip, float aim) {
		public static final Spread DEFAULT = new Spread(4f, 1f);
		public static final Codec<Spread> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.floatRange(0f, 45f).optionalFieldOf("hip", DEFAULT.hip).forGetter(Spread::hip),
			Codec.floatRange(0f, 45f).optionalFieldOf("aim", DEFAULT.aim).forGetter(Spread::aim)
		).apply(i, Spread::new));
	}

	public record Recoil(float pitch, float yaw) {
		public static final Recoil DEFAULT = new Recoil(2f, 0.5f);
		public static final Codec<Recoil> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.floatRange(0f, 45f).optionalFieldOf("pitch", DEFAULT.pitch).forGetter(Recoil::pitch),
			Codec.floatRange(0f, 45f).optionalFieldOf("yaw", DEFAULT.yaw).forGetter(Recoil::yaw)
		).apply(i, Recoil::new));
	}

	/**
	 * @param zoom          field-of-view multiplier while aiming (1 = no zoom, 0.5 = twice as close)
	 * @param recoilFactor  recoil multiplier while aiming
	 */
	public record Aim(float zoom, float recoilFactor) {
		public static final Aim DEFAULT = new Aim(0.85f, 0.6f);
		public static final Codec<Aim> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.floatRange(0.05f, 1f).optionalFieldOf("zoom", DEFAULT.zoom).forGetter(Aim::zoom),
			Codec.floatRange(0f, 1f).optionalFieldOf("recoil_factor", DEFAULT.recoilFactor).forGetter(Aim::recoilFactor)
		).apply(i, Aim::new));
	}

	public record Sounds(
		Optional<Holder<SoundEvent>> fire,
		Optional<Holder<SoundEvent>> empty,
		Optional<Holder<SoundEvent>> reloadStart,
		Optional<Holder<SoundEvent>> reloadRound,
		Optional<Holder<SoundEvent>> reloadEnd
	) {
		public static final Sounds NONE = new Sounds(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		public static final Codec<Sounds> CODEC = RecordCodecBuilder.create(i -> i.group(
			SoundEvent.CODEC.optionalFieldOf("fire").forGetter(Sounds::fire),
			SoundEvent.CODEC.optionalFieldOf("empty").forGetter(Sounds::empty),
			SoundEvent.CODEC.optionalFieldOf("reload_start").forGetter(Sounds::reloadStart),
			SoundEvent.CODEC.optionalFieldOf("reload_round").forGetter(Sounds::reloadRound),
			SoundEvent.CODEC.optionalFieldOf("reload_end").forGetter(Sounds::reloadEnd)
		).apply(i, Sounds::new));
	}

	/**
	 * Muzzle smoke, spawned with vanilla particles so it works with shader packs.
	 *
	 * @param particle any particle type, e.g. {@code {"type": "minecraft:smoke"}}
	 * @param count    particles per shot (0 disables)
	 * @param speed    particle drift speed
	 */
	public record Muzzle(ParticleOptions particle, int count, float speed) {
		public static final Muzzle DEFAULT = new Muzzle(ParticleTypes.SMOKE, 4, 0.02f);
		public static final Codec<Muzzle> CODEC = RecordCodecBuilder.create(i -> i.group(
			ParticleTypes.CODEC.optionalFieldOf("particle", DEFAULT.particle).forGetter(Muzzle::particle),
			ExtraCodecs.intRange(0, 64).optionalFieldOf("count", DEFAULT.count).forGetter(Muzzle::count),
			Codec.floatRange(0f, 1f).optionalFieldOf("speed", DEFAULT.speed).forGetter(Muzzle::speed)
		).apply(i, Muzzle::new));
	}
}
