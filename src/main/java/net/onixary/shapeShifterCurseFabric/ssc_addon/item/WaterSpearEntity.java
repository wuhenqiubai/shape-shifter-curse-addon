package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.List;

public class WaterSpearEntity extends ThrownTrident {

	private ItemStack waterSpearStack = new ItemStack(SscAddon.WATER_SPEAR);

	public WaterSpearEntity(EntityType<? extends ThrownTrident> entityType, Level world) {
		super(SscAddon.WATER_SPEAR_ENTITY, world);
		// 不可拾取：水矛只能合成获得（走 5 秒 CD + 最多一把），扔出即消耗
		this.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
	}

	public WaterSpearEntity(Level world, LivingEntity owner, ItemStack stack) {
		super(SscAddon.WATER_SPEAR_ENTITY, world);
		this.setOwner(owner);
		this.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		this.waterSpearStack = stack.copy();
		// 不可拾取：水矛只能合成获得（走 5 秒 CD + 最多一把），扔出即消耗
		this.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
	}

	public ItemStack getWeaponItem() {
		return this.waterSpearStack;
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains("WaterSpear", 10)) {
			net.minecraft.core.HolderLookup.Provider registries = this.level() != null ? this.level().registryAccess() : net.minecraft.core.RegistryAccess.EMPTY;
			ItemStack.parse(registries, nbt.getCompound("WaterSpear")).ifPresent(s -> this.waterSpearStack = s);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		net.minecraft.core.HolderLookup.Provider registries2 = this.level() != null ? this.level().registryAccess() : net.minecraft.core.RegistryAccess.EMPTY;
		nbt.put("WaterSpear", this.waterSpearStack.save(registries2));
	}


	@Override
	protected void onHitEntity(EntityHitResult entityHitResult) {
		Entity entity = entityHitResult.getEntity();
		Level world = this.level();

		if (!world.isClientSide && entity instanceof LivingEntity target) {
			// Direct damage
			float damage = 10.0f;
			target.hurt(this.damageSources().trident(this, this.getOwner()), damage);

			// Apply slowness
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));

			// Area damage
			doAreaDamage(target.position().add(0, target.getBbHeight() / 2, 0), target);
		}

		// Remove the spear after hitting
		this.discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult blockHitResult) {
		Level world = this.level();

		if (!world.isClientSide) {
			doAreaDamage(this.position(), null);
		}

		// Remove the spear after hitting block
		this.discard();
	}

	private void doAreaDamage(Vec3 pos, Entity directTarget) {
		Level world = this.level();
		double x = pos.x;
		double y = pos.y;
		double z = pos.z;

		// Get entities within 1.5 block radius (3 block diameter)
		List<Entity> nearbyEntities = world.getEntities(this.getOwner(), new AABB(x - 1.5, y - 1.5, z - 1.5, x + 1.5, y + 1.5, z + 1.5));
		for (Entity nearEntity : nearbyEntities) {
			if (nearEntity instanceof LivingEntity living && nearEntity != this.getOwner() && nearEntity != directTarget) {
				living.hurt(this.damageSources().trident(this, this.getOwner()), 4.0f);
			}
		}

		// Play splash sound and particles
		world.playSound(null, x, y, z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0F, 0.8F);

		// Spawn particles on server - 落地水花爆开（仿 RC-4 药水破碎特效）
		if (world instanceof net.minecraft.server.level.ServerLevel serverWorld) {
			net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnWaterBurst(serverWorld, x, y, z, 1.0);
		}
	}

	@Override
	public void tick() {
		super.tick();

		// Spawn water trail particles
		Level world = this.level();
		if (world.isClientSide && !this.inGround) {
			for (int i = 0; i < 2; i++) {
				world.addParticle(ParticleTypes.DRIPPING_WATER, true,
						this.getX() + (world.random.nextDouble() - 0.5) * 0.3,
						this.getY() + (world.random.nextDouble() - 0.5) * 0.3,
						this.getZ() + (world.random.nextDouble() - 0.5) * 0.3,
						0, 0, 0);
			}
		}
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return SoundEvents.GENERIC_SPLASH;
	}

	@Override
	public boolean isNoGravity() {
		return false;
	}

	@Override
	public void playerTouch(Player player) {
		// 水矛扔出即消耗、永远不可拾取（含重启前旧 ALLOWED 实体）：碰撞不做任何拾取处理。
		// 唯一获取途径=合成（5 秒 CD + 最多一把）。
	}

}
