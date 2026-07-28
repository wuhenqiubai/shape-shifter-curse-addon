package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.Comparator;
import java.util.List;

/**
 * SP雪狐远程主要技能 - 法术冰球
 * 无下坠，飞行速度10b/s，最多飞行25格
 * 命中施加霜降效果4秒
 */
public class FrostBallEntity extends Projectile implements ItemSupplier {

	private static final double SPEED = 0.75; // 15格/秒 = 0.75格/tick（原10格/秒增加1.5倍）
	private static final double MAX_DISTANCE = 50.0; // 最大飞行距离（原25格增加2倍）
	private static final int FROST_FALL_DURATION = 80; // 霜降持续4秒

	private Vec3 startPos;
	private int ticksAlive = 0;

	// 追踪相关
	private LivingEntity trackingTarget;
	private boolean isChild = false;

	public FrostBallEntity(EntityType<? extends FrostBallEntity> entityType, Level world) {
		super(entityType, world);
		this.startPos = this.position();
	}

	public FrostBallEntity(Level world, LivingEntity owner) {
		super(SscAddon.FROST_BALL_ENTITY, world);
		this.setOwner(owner);
		this.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
		this.startPos = this.position();
	}

	public void setTrackingTarget(LivingEntity target) {
		this.trackingTarget = target;
	}

	public void setIsChild(boolean isChild) {
		this.isChild = isChild;
	}

	/**
	 * 设置冰球的飞行方向
	 */
	public void setDirection(Vec3 direction) {
		Vec3 velocity = direction.normalize().scale(SPEED);
		this.setDeltaMovement(velocity.x, velocity.y, velocity.z);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// 不需要额外的数据追踪器
	}

	@Override
	public void tick() {
		super.tick();
		ticksAlive++;

		// 追踪逻辑
		if (this.trackingTarget != null && this.trackingTarget.isAlive()) {
			Vec3 targetPos = this.trackingTarget.getEyePosition();
			Vec3 currentPos = this.position();
			Vec3 direction = targetPos.subtract(currentPos).normalize();

			// 简单的追踪转向
			Vec3 currentVel = this.getDeltaMovement().normalize();
			// 0.2f 的转向系数
			Vec3 newVel = currentVel.add(direction.scale(0.2)).normalize().scale(SPEED);

			this.setDeltaMovement(newVel.x, newVel.y, newVel.z);
		}

		// 无重力移动
		Vec3 velocity = this.getDeltaMovement();
		this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);

		// 检测碰撞
		HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
		if (hitResult.getType() != HitResult.Type.MISS) {
			this.onHit(hitResult);
		}

		// 如果在碰撞处理中实体被移除了（比如撞到了什么），就不再移动
		if (this.isRemoved()) return;

		// 检查是否超过最大飞行距离
		if (startPos != null && this.distanceToSqr(startPos) > MAX_DISTANCE * MAX_DISTANCE) {
			this.discard();
			return;
		}

		// 超时检查（防止无限飞行）
		if (ticksAlive > 100) { // 5秒超时
			this.discard();
			return;
		}

		// 生成粒子效果
		if (this.level() instanceof ServerLevel serverWorld) {
			net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SNOWFLAKE,
					this.getX(), this.getY(), this.getZ(),
					2, 0.1, 0.1, 0.1, 0.02);
		}
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);

		if (!this.level().isClientSide) {
			// 播放击中音效
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.5f);

			// 生成爆炸粒子
			if (this.level() instanceof ServerLevel serverWorld) {
				net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SNOWFLAKE,
						this.getX(), this.getY(), this.getZ(),
						15, 0.3, 0.3, 0.3, 0.1);
			}

			this.discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult entityHitResult) {
		super.onHitEntity(entityHitResult);

		Entity target = entityHitResult.getEntity();
		if (target instanceof LivingEntity livingTarget && !this.level().isClientSide) {
			// 白名单检查：如果主人在线且目标受保护，则跳过效果与追踪碎片
			if (this.getOwner() instanceof net.minecraft.server.level.ServerPlayer ownerPlayer
					&& WhitelistUtils.isProtected(ownerPlayer, livingTarget)) {
				return;
			}
			// 施加霜降效果
			livingTarget.addEffect(new MobEffectInstance(
					SscAddon.FROST_FALL_ENTRY,
					FROST_FALL_DURATION,
					0,
					false,
					true,
					true
			));

			// 播放击中音效
			this.level().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 1.2f);

			// 霜之护符逻辑：如果是玩家发射的，且不是分裂的子弹，且装备了护符
			if (this.getOwner() instanceof LivingEntity owner && !this.isChild) {
				boolean hasAmulet = TrinketsApi.getTrinketComponent(owner).map(
						c -> c.isEquipped(SscAddon.FROST_AMULET)
				).orElse(false);

				if (hasAmulet) {
					spawnTrackingShards(owner, livingTarget);
				}
			}
		}
	}

	private void spawnTrackingShards(LivingEntity owner, LivingEntity hitTarget) {
		// 直径10格 = 半径5格
		double radius = 5.0;
		AABB box = hitTarget.getBoundingBox().inflate(radius);
		List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e != owner && e != hitTarget && e.isAlive() && !e.isSpectator()
						&& !(owner instanceof net.minecraft.server.level.ServerPlayer sp && WhitelistUtils.isProtected(sp, e)));

		if (nearby.isEmpty()) return;

		// 按距离排序
		nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(hitTarget)));

		int count = 0;
		int maxShards = 2; // 发射两个

		for (LivingEntity target : nearby) {
			if (count >= maxShards) break;

			FrostBallEntity shard = new FrostBallEntity(this.level(), owner);
			// 从被命中者位置发射
			shard.setPos(hitTarget.getX(), hitTarget.getEyeY(), hitTarget.getZ());
			shard.setTrackingTarget(target);
			shard.setIsChild(true); // 标记为子弹，防止递归爆炸

			// 初始朝向目标
			Vec3 direction = target.getEyePosition().subtract(hitTarget.getEyePosition()).normalize();
			shard.setDirection(direction);

			this.level().addFreshEntity(shard);
			count++;
		}
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && entity != this.getOwner() && entity instanceof LivingEntity;
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains("StartX")) {
			this.startPos = new Vec3(
					nbt.getDouble("StartX"),
					nbt.getDouble("StartY"),
					nbt.getDouble("StartZ")
			);
		}
		if (nbt.contains("IsChild")) {
			this.setIsChild(nbt.getBoolean("IsChild"));
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		if (startPos != null) {
			nbt.putDouble("StartX", startPos.x);
			nbt.putDouble("StartY", startPos.y);
			nbt.putDouble("StartZ", startPos.z);
		}
		nbt.putBoolean("IsChild", this.isChild);
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
		return new ClientboundAddEntityPacket(this, entityTrackerEntry);
	}

	/**
	 * FlyingItemEntity接口实现 - 返回雪球作为显示物品
	 */
	@Override
	public ItemStack getItem() {
		return new ItemStack(Items.SNOWBALL);
	}
}