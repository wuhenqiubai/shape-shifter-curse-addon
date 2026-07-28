package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;

public class AllayClearMarkerEntity extends ThrowableItemProjectile {

	public AllayClearMarkerEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
		super(entityType, world);
	}

	public AllayClearMarkerEntity(Level world, LivingEntity owner) {
		super(SscAddon.CLEAR_MARKER_ENTITY_TYPE, owner, world);
	}

	public AllayClearMarkerEntity(Level world, double x, double y, double z) {
		super(SscAddon.CLEAR_MARKER_ENTITY_TYPE, x, y, z, world);
	}

	@Override
	protected Item getDefaultItem() {
		return SscAddon.CLEAR_FRIEND_MARKER;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.tickCount > 40) {
			this.discard();
		}
	}

	@Override
	public void handleEntityEvent(byte status) {
		if (status == 3) {
			for (int i = 0; i < 8; ++i) {
				this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()), this.getX(), this.getY(), this.getZ(), ((double) this.random.nextFloat() - 0.5D) * 0.08D, ((double) this.random.nextFloat() - 0.5D) * 0.08D, ((double) this.random.nextFloat() - 0.5D) * 0.08D);
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult entityHitResult) {
		super.onHitEntity(entityHitResult);
		Entity entity = entityHitResult.getEntity();
		Entity owner = this.getOwner();

		if (owner instanceof ServerPlayer player && entity instanceof LivingEntity target) {
			// 从白名单移除 + 移除 ssc_raid_friend
			AllaySPGroupHeal.removeFromWhitelist(player, target);
			target.getTags().remove("ssc_raid_friend");
		}
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (!this.level().isClientSide) {
			this.level().broadcastEntityEvent(this, (byte) 3);
			this.discard();
		}
	}
}
