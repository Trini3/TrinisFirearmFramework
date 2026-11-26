package io.github.trini3.tff.registry;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.platform.TffPlatform;
import io.github.trini3.tff.projectile.BulletEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class TffEntities {
	public static final Supplier<EntityType<BulletEntity>> BULLET = TffPlatform.INSTANCE.register(
		BuiltInRegistries.ENTITY_TYPE, Tff.id("bullet"),
		() -> EntityType.Builder.<BulletEntity>of(BulletEntity::new, MobCategory.MISC)
			.noLootTable()
			.noSave()
			.sized(0.125f, 0.125f)
			.clientTrackingRange(4)
			.updateInterval(20)
			.build(ResourceKey.create(Registries.ENTITY_TYPE, Tff.id("bullet"))));

	private TffEntities() {}

	public static void init() {}
}
