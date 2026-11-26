package io.github.trini3.tff.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.tff.Tff;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One ammo type, loaded from {@code data/<namespace>/tff/ammo/<name>.json}. Ammo decides how its
 * projectiles fly and hit; guns only scale them.
 *
 * @param item         the inventory item used up when reloading
 * @param behavior     id of a registered {@link io.github.trini3.tff.api.ProjectileBehavior}
 * @param pellets      projectiles per shot (buckshot has several)
 * @param damage       damage per projectile at close range
 * @param minDamage    damage per projectile at or beyond {@code falloffEnd}
 * @param falloffStart distance in blocks where damage starts to drop
 * @param falloffEnd   distance in blocks where damage reaches {@code minDamage}
 * @param speed        starting speed in blocks per tick
 * @param gravity      downward acceleration in blocks per tick squared
 * @param drag         speed multiplier applied every tick (1 = none)
 * @param lifetime     ticks before the projectile disappears
 * @param pierce       extra entities the projectile can pass through
 */
public record AmmoDefinition(
	Holder<Item> item,
	Identifier behavior,
	int pellets,
	float damage,
	float minDamage,
	float falloffStart,
	float falloffEnd,
	float speed,
	float gravity,
	float drag,
	int lifetime,
	int pierce
) {
	public static final Identifier DEFAULT_BEHAVIOR = Tff.id("bullet");

	public static final Codec<AmmoDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
		Item.CODEC.fieldOf("item").forGetter(AmmoDefinition::item),
		Identifier.CODEC.optionalFieldOf("behavior", DEFAULT_BEHAVIOR).forGetter(AmmoDefinition::behavior),
		ExtraCodecs.intRange(1, 64).optionalFieldOf("pellets", 1).forGetter(AmmoDefinition::pellets),
		ExtraCodecs.POSITIVE_FLOAT.fieldOf("damage").forGetter(AmmoDefinition::damage),
		ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("min_damage").forGetter(a -> Optional.of(a.minDamage())),
		ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("falloff_start", 16f).forGetter(AmmoDefinition::falloffStart),
		ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("falloff_end", 48f).forGetter(AmmoDefinition::falloffEnd),
		ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("speed", 6f).forGetter(AmmoDefinition::speed),
		Codec.FLOAT.optionalFieldOf("gravity", 0.01f).forGetter(AmmoDefinition::gravity),
		Codec.floatRange(0f, 1f).optionalFieldOf("drag", 0.99f).forGetter(AmmoDefinition::drag),
		ExtraCodecs.POSITIVE_INT.optionalFieldOf("lifetime", 60).forGetter(AmmoDefinition::lifetime),
		ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("pierce", 0).forGetter(AmmoDefinition::pierce)
	).apply(i, (item, behavior, pellets, damage, minDamage, start, end, speed, gravity, drag, lifetime, pierce) ->
		new AmmoDefinition(item, behavior, pellets, damage, minDamage.orElse(damage), start, Math.max(start, end), speed, gravity, drag, lifetime, pierce)));

	public boolean matches(ItemStack stack) {
		return stack.is(this.item);
	}
}
