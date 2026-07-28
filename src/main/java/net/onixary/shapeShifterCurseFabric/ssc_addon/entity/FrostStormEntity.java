package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;
import java.util.UUID;

/**
 * SP雪狐远程次要技能 - 冰风暴实体
 * 持续10秒，伤害半径3.5格，每秒2点魔法伤害
 * 吸附速度2b/s（6格内），6-10格吸附减弱
 */
public class FrostStormEntity extends Entity {

	private static final int DURATION = 200; // 10秒
	private static final double DAMAGE_RADIUS = 3.5;
	private static final double PULL_RADIUS_STRONG = 6.0;
	private static final double PULL_RADIUS_WEAK = 10.0;
	private static final float DAMAGE_PER_SECOND = 2.0f;
	private static final double PULL_SPEED = 0.1; // 2格/秒 = 0.1格/tick

	private int ticksAlive = 0;
	private UUID ownerUuid;

	public FrostStormEntity(EntityType<?> entityType, Level world) {
		super(entityType, world);
		this.noPhysics = true;
	}

	public FrostStormEntity(Level world, double x, double y, double z, Player owner) {
		super(SscAddon.FROST_STORM_ENTITY, world);
		this.setPos(x, y, z);
		this.ownerUuid = owner.getUUID();
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// 暂时不需要初始化数据跟踪器
	}

	@Override
	public void tick() {
		super.tick();
		ticksAlive++;

		if (ticksAlive > DURATION) {
			this.discard();
			return;
		}

		if (!this.level().isClientSide && this.level() instanceof ServerLevel serverWorld) {
			// 每0.5秒造成一次伤害（每10tick）
			if (ticksAlive % 10 == 0) {
				dealDamage(serverWorld);
			}

			// 每tick吸附敌人
			pullEntities();

			// 生成粒子效果
			spawnParticles(serverWorld);

			// 播放环境音效
			if (ticksAlive % 40 == 0) {
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.SNOW_GOLEM_AMBIENT, SoundSource.HOSTILE, 0.5f, 0.5f);
			}
		}
	}

	private void dealDamage(ServerLevel world) {
		AABB damageBox = new AABB(
				this.getX() - DAMAGE_RADIUS, this.getY() - 1, this.getZ() - DAMAGE_RADIUS,
				this.getX() + DAMAGE_RADIUS, this.getY() + 3, this.getZ() + DAMAGE_RADIUS
		);

		List<LivingEntity> targets = world.getEntitiesOfClass(
				LivingEntity.class, damageBox,
				entity -> entity.getUUID() != ownerUuid && entity.isAlive()
		);

		Player owner = ownerUuid != null ? world.getPlayerByUUID(ownerUuid) : null;

		for (LivingEntity target : targets) {
			double dist = this.distanceToSqr(target.getX(), this.getY(), target.getZ());
			if (dist <= DAMAGE_RADIUS * DAMAGE_RADIUS) {
				if (WhitelistUtils.isProtected(ownerUuid, world, target)) continue;
				DamageSource source = owner != null
						? target.damageSources().playerAttack(owner)
						: target.damageSources().magic();
				target.hurt(source, DAMAGE_PER_SECOND);
			}
		}
	}

	private void pullEntities() {
		AABB pullBox = new AABB(
				this.getX() - PULL_RADIUS_WEAK, this.getY() - 2, this.getZ() - PULL_RADIUS_WEAK,
				this.getX() + PULL_RADIUS_WEAK, this.getY() + 4, this.getZ() + PULL_RADIUS_WEAK
		);

		List<LivingEntity> targets = this.level().getEntitiesOfClass(
				LivingEntity.class, pullBox,
				entity -> entity.getUUID() != ownerUuid && entity.isAlive()
		);

		Vec3 center = new Vec3(this.getX(), this.getY(), this.getZ());
		ServerLevel pullWorld = this.level() instanceof ServerLevel sw ? sw : null;

		for (LivingEntity target : targets) {
			Vec3 targetPos = target.position();
			double dist = Math.sqrt(target.distanceToSqr(this.getX(), this.getY(), this.getZ()));

			if (dist > PULL_RADIUS_WEAK || dist < 0.5) continue;
			if (pullWorld != null && WhitelistUtils.isProtected(ownerUuid, pullWorld, target)) continue;

			// 计算吸附速度
			double pullStrength;
			if (dist <= PULL_RADIUS_STRONG) {
				pullStrength = PULL_SPEED; // 正常吸附速度
			} else {
				// 6-10格，吸附减弱
				double factor = 1.0 - ((dist - PULL_RADIUS_STRONG) / (PULL_RADIUS_WEAK - PULL_RADIUS_STRONG));
				pullStrength = PULL_SPEED * factor * 0.3; // 骤减吸附
			}

			// 计算吸附方向
			Vec3 direction = center.subtract(targetPos).normalize();
			Vec3 pullVelocity = direction.scale(pullStrength);

			// 应用吸附
			Vec3 newVelocity = target.getDeltaMovement().add(pullVelocity);
			target.setDeltaMovement(newVelocity);
			target.hurtMarked = true;
		}
	}

	private void spawnParticles(ServerLevel world) {
		// 风暴粒子
		for (int i = 0; i < 5; i++) {
			double angle = Math.random() * Math.PI * 2;
			double radius = Math.random() * DAMAGE_RADIUS;
			double x = this.getX() + Math.cos(angle) * radius;
			double z = this.getZ() + Math.sin(angle) * radius;
			double y = this.getY() + Math.random() * 2;

			net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(world, ParticleTypes.SNOWFLAKE, x, y, z, 1, 0, 0, 0, 0.05);
		}

		// 旋转粒子效果
		double rotAngle = (ticksAlive * 0.2) % (Math.PI * 2);
		for (int i = 0; i < 3; i++) {
			double angle = rotAngle + (i * Math.PI * 2 / 3);
			double x = this.getX() + Math.cos(angle) * 2;
			double z = this.getZ() + Math.sin(angle) * 2;
			net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(world, ParticleTypes.CLOUD, x, this.getY() + 1, z, 1, 0, 0.1, 0, 0);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		this.ticksAlive = nbt.getInt("TicksAlive");
		if (nbt.hasUUID("Owner")) {
			this.ownerUuid = nbt.getUUID("Owner");
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		nbt.putInt("TicksAlive", this.ticksAlive);
		if (ownerUuid != null) {
			nbt.putUUID("Owner", ownerUuid);
		}
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
		return super.getAddEntityPacket(entityTrackerEntry);
	}

	@Override
	public boolean canBeCollidedWith() {
		return super.canBeCollidedWith();
	}
}