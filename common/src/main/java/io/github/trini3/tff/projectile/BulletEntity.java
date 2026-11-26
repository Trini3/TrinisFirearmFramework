package io.github.trini3.tff.projectile;

import io.github.trini3.tff.api.ProjectileBehavior;
import io.github.trini3.tff.config.TffConfig;
import io.github.trini3.tff.data.AmmoDefinition;
import io.github.trini3.tff.gun.GunMath;
import io.github.trini3.tff.registry.TffDamageTypes;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The projectile every gun fires. Flight and hit numbers come from the {@link AmmoDefinition};
 * what happens on impact comes from its {@link ProjectileBehavior}. Bullets are short-lived and
 * never saved. They have no model (they move several blocks per tick), so nothing custom is
 * rendered.
 */
public class BulletEntity extends Projectile {
	private ProjectileBehavior behavior = ProjectileBehavior.BULLET;
	private float damage = 4f;
	private float minDamage = 4f;
	private float falloffStart = 16f;
	private float falloffEnd = 48f;
	private float gravity = 0.01f;
	private float drag = 0.99f;
	private int lifetime = 60;
	private int pierceLeft;
	private Vec3 origin = Vec3.ZERO;
	private final IntSet hitEntities = new IntOpenHashSet(2);

	public BulletEntity(EntityType<? extends BulletEntity> type, Level level) {
		super(type, level);
	}

	public void configure(AmmoDefinition ammo, ProjectileBehavior behavior, float damageMultiplier, Vec3 origin) {
		this.behavior = behavior;
		this.damage = ammo.damage() * damageMultiplier;
		this.minDamage = ammo.minDamage() * damageMultiplier;
		this.falloffStart = ammo.falloffStart();
		this.falloffEnd = ammo.falloffEnd();
		this.gravity = ammo.gravity();
		this.drag = ammo.drag();
		this.lifetime = ammo.lifetime();
		this.pierceLeft = ammo.pierce();
		this.origin = origin;
	}

	public void alignRotationToMovement() {
		Vec3 movement = this.getDeltaMovement();
		this.setYRot((float) (Mth.atan2(movement.x, movement.z) * Mth.RAD_TO_DEG));
		this.setXRot((float) (Mth.atan2(movement.y, movement.horizontalDistance()) * Mth.RAD_TO_DEG));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel server) {
			if (this.tickCount > this.lifetime) {
				this.discard();
				return;
			}
			this.behavior.tick(this, server);
			if (this.isRemoved()) {
				return;
			}
		}

		Vec3 movement = this.getDeltaMovement();
		HitResult hit = this.findHit(movement);
		Vec3 next = hit.getType() == HitResult.Type.MISS ? this.position().add(movement) : hit.getLocation();
		this.setPos(next);
		this.updateRotation();
		super.tick();

		if (hit.getType() != HitResult.Type.MISS && this.isAlive() && !this.level().isClientSide()) {
			this.hitTargetOrDeflectSelf(hit);
		}
		if (this.isInWater()) {
			this.setDeltaMovement(movement.scale(0.6));
		} else {
			this.setDeltaMovement(movement.scale(this.drag));
		}
		this.applyGravity();
	}

	/**
	 * What the bullet hits on its way through {@code movement}: blocks, then the nearest entity
	 * before them. Unlike vanilla's projectile test, which widens every target by up to 0.3 blocks
	 * once an arrow has flown a few ticks, this uses the exact hitboxes, so a shot pattern hits
	 * at range just as it is drawn up.
	 */
	private HitResult findHit(Vec3 movement) {
		Vec3 from = this.position();
		Vec3 to = from.add(movement);
		HitResult hit = this.level().clipIncludingBorder(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (hit.getType() != HitResult.Type.MISS) {
			to = hit.getLocation();
		}
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
			this.level(), this, from, to, this.getBoundingBox().expandTowards(movement).inflate(1.0), this::canHitEntity, 0f);
		return entityHit != null ? entityHit : hit;
	}

	@Override
	protected double getDefaultGravity() {
		return this.gravity;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return entity != this.getOwner() && !this.hitEntities.contains(entity.getId()) && super.canHitEntity(entity);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (this.level() instanceof ServerLevel server) {
			this.behavior.onHitEntity(this, server, hit);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit); // lets targets, bells, etc. react like they do to arrows
		if (this.level() instanceof ServerLevel server) {
			this.behavior.onHitBlock(this, server, hit);
		}
	}

	/** Damage at the current distance from the muzzle. */
	public float currentDamage() {
		return GunMath.falloff(this.position().distanceTo(this.origin), this.damage, this.minDamage, this.falloffStart, this.falloffEnd);
	}

	/**
	 * Deals this bullet's damage to the entity it hit, times the config's headshot multiplier when
	 * it hit a head ({@link Headshots}). A headshot throws a few critical-hit sparks.
	 */
	public boolean damage(ServerLevel level, EntityHitResult hit) {
		Entity target = hit.getEntity();
		boolean headshot = Headshots.isHeadshot(level.registryAccess(), target, hit.getLocation());
		float amount = this.currentDamage() * (headshot ? TffConfig.get().headshotMultiplier() : 1f);
		boolean hurt = this.hurt(level, target, amount);
		if (hurt && headshot) {
			Vec3 at = hit.getLocation();
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.25);
		}
		return hurt;
	}

	/** Deals this bullet's damage to {@code target}, with no headshot check (for behaviours that hurt things nearby). */
	public boolean damage(ServerLevel level, Entity target) {
		return this.hurt(level, target, this.currentDamage());
	}

	/** Bullet damage is tagged {@code minecraft:bypasses_cooldown}, so every pellet counts. */
	private boolean hurt(ServerLevel level, Entity target, float amount) {
		DamageSource source = new DamageSource(
			level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(TffDamageTypes.BULLET), this, this.getOwner());
		boolean hurt = target.hurtServer(level, source, amount);
		if (hurt) {
			EnchantmentHelper.doPostAttackEffects(level, target, source);
		}
		return hurt;
	}

	/** Lets the bullet continue through {@code target} if it has pierce left, otherwise removes it. */
	public void consumePierce(Entity target) {
		if (this.pierceLeft > 0) {
			this.pierceLeft--;
			this.hitEntities.add(target.getId());
		} else {
			this.discard();
		}
	}

	/** Small puff of block fragments at the impact point (vanilla particles, shader safe). */
	public void impactEffects(ServerLevel level, BlockHitResult hit) {
		BlockState state = level.getBlockState(hit.getBlockPos());
		Vec3 at = hit.getLocation();
		if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 3, 0.02, 0.02, 0.02, 0.05);
		}
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.01);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		// Never saved (EntityType#noSave); nothing to write.
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
