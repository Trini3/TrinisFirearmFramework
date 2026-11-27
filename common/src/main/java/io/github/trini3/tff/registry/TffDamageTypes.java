package io.github.trini3.tff.registry;

import io.github.trini3.tff.Tff;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Damage types are datapack entries; see {@code data/tff/damage_type/}. */
public final class TffDamageTypes {
	public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, Tff.id("bullet"));

	private TffDamageTypes() {}
}
