package io.github.trini3.tff.api;

import io.github.trini3.tff.projectile.BulletEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * How an ammo type's projectiles act. Ammo JSON picks one by id ({@code "behavior"}); the
 * default {@code tff:bullet} deals damage and stops at blocks. Content mods register new ones
 * (explosive, incendiary, ...) with {@link TffApi#registerProjectileBehavior}. All methods run
 * on the server only.
 */
public interface ProjectileBehavior {
	ProjectileBehavior BULLET = new ProjectileBehavior() {};

	/** Called every server tick before the projectile moves. */
	default void tick(BulletEntity bullet, ServerLevel level) {}

	/** Called when the projectile hits an entity. The default damages it and handles pierce. */
	default void onHitEntity(BulletEntity bullet, ServerLevel level, EntityHitResult hit) {
		bullet.damage(level, hit.getEntity());
		bullet.consumePierce(hit.getEntity());
	}

	/** Called when the projectile hits a block. The default shows impact particles and removes it. */
	default void onHitBlock(BulletEntity bullet, ServerLevel level, BlockHitResult hit) {
		bullet.impactEffects(level, hit);
		bullet.discard();
	}
}
