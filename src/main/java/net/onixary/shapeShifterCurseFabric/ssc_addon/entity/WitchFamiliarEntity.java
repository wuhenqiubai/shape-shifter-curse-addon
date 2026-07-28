package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 女巫使魔 - 一种伴随女巫自然生成的敌对生物
 * 使用原版使魔外观，拥有火环技能，属于劫掠阵营
 */
public class WitchFamiliarEntity extends Monster implements GeoEntity {

	// 火环参数
	private static final int FIRE_RING_COOLDOWN_MAX = 240;  // 12秒冷却
	private static final int FIRE_RING_POWER = 3;           // 爆炸威力（原版explosion_damage_entity power=3）
	private static final float FIRE_RING_EFFECT_RADIUS = FIRE_RING_POWER * 2.0f; // 实际影响半径=6.0格
	private static final float FIRE_RING_DAMAGE = 6.0f;     // on_fire伤害（原版8降低2点）
	private static final int FIRE_RING_IGNITE_SECONDS = 10; // 着火10秒
	private static final float PARTICLE_OUTER_RADIUS = 4.0f; // 粒子外圈半径4格
	private static final float PARTICLE_INNER_RADIUS = 1.5f; // 粒子内圈半径1.5格
	// 原版使魔形态ID（用于友军判定）
	// 注意：PlayerFormBase.FormID 不带 "form_" 前缀，getOriginID() 才会拼接
	private static final ResourceLocation VANILLA_FAMILIAR_FOX_3 = ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "familiar_fox_3");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int fireRingCooldown = 0;
	// 主人（女巫）UUID，用于跟随和攻击同步
	private UUID ownerUuid;

	public WitchFamiliarEntity(EntityType<? extends Monster> entityType, Level world) {
		super(entityType, world);
	}

	public static AttributeSupplier.Builder createWitchFamiliarAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 14.0)       // 7颗心（与使魔形态一致）
				.add(Attributes.ATTACK_DAMAGE, 4.0)     // 2颗心近战伤害
				.add(Attributes.MOVEMENT_SPEED, 0.35)   // 与狼相近的移速
				.add(Attributes.FOLLOW_RANGE, 20.0);    // 追踪范围
	}

	public UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	public void setOwnerUuid(UUID uuid) {
		this.ownerUuid = uuid;
	}

	/**
	 * 从世界中获取主人女巫实体
	 */
	public Witch getOwnerWitch() {
		if (this.ownerUuid == null) return null;
		if (!(this.level() instanceof ServerLevel serverWorld)) return null;
		var entity = serverWorld.getEntity(this.ownerUuid);
		if (entity instanceof Witch witch && witch.isAlive()) return witch;
		return null;
	}

	@Override
	protected void registerGoals() {
		// 移动/战斗目标
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, false));
		this.goalSelector.addGoal(2, new FollowOwnerWitchGoal(this, 1.0, 10.0f, 3.0f, 16.0f));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

		// 索敌目标（排除友军）
		this.targetSelector.addGoal(1, new CopyOwnerTargetGoal(this));
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this, Raider.class, WitchFamiliarEntity.class));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true, this::shouldAttackPlayer));
	}

	/**
	 * 判断是否应该攻击该玩家
	 * 不攻击：原版使魔、堕落悦灵形态的玩家（劫掠阵营友军）
	 */
	private boolean shouldAttackPlayer(LivingEntity target) {
		if (!(target instanceof Player player)) return false;
		return !FormUtils.isAnyForm(player,
				FormIdentifiers.FALLEN_ALLAY_SP,
				VANILLA_FAMILIAR_FOX_3
		);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		// 不攻击劫掠阵营、恼鬼、女巫、其他女巫使魔
		if (target instanceof Raider) return false;
		if (target instanceof Vex) return false;
		if (target instanceof Witch) return false;
		if (target instanceof WitchFamiliarEntity) return false;
		return super.canAttack(target);
	}

	@Override
	public void customServerAiStep() {
		super.customServerAiStep();

		if (fireRingCooldown > 0) fireRingCooldown--;

		// 灵魂火焰粒子效果（与原版使魔形态一致，每10tick产生一次）
		if (!this.level().isClientSide() && this.tickCount % 10 == 0 && this.level() instanceof ServerLevel sw) {
			ParticleUtils.spawnParticles(sw, ParticleTypes.SOUL_FIRE_FLAME,
					this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
		}

		// 仅在有攻击目标且目标在火环范围内时释放火环
		if (!this.level().isClientSide() && fireRingCooldown <= 0 && this.getTarget() != null
				&& this.distanceToSqr(this.getTarget()) <= FIRE_RING_EFFECT_RADIUS * FIRE_RING_EFFECT_RADIUS) {
			useFireRing();
			fireRingCooldown = FIRE_RING_COOLDOWN_MAX;
		}

		// 无主使魔认主逻辑：每秒检查一次，视线范围内有女巫则认主
		if (!this.level().isClientSide() && this.ownerUuid == null && this.tickCount % 20 == 0) {
			tryBondWithNearbyWitch();
		}
	}

	/**
	 * 无主使魔认主逻辑：扫描视线范围内的女巫，认最近的为主
	 * 认主后会自动被 FollowOwnerWitchGoal 和 CopyOwnerTargetGoal 管理
	 */
	private void tryBondWithNearbyWitch() {
		if (!(this.level() instanceof ServerLevel serverWorld)) return;

		double searchRange = 16.0; // 16格搜索范围
		List<Witch> witches = serverWorld.getEntitiesOfClass(
				Witch.class,
				this.getBoundingBox().inflate(searchRange),
				witch -> witch.isAlive() && this.hasLineOfSight(witch)
		);

		if (witches.isEmpty()) return;

		// 找最近的女巫
		Witch nearest = null;
		double nearestDistSq = Double.MAX_VALUE;
		for (Witch witch : witches) {
			double distSq = this.distanceToSqr(witch);
			if (distSq < nearestDistSq) {
				nearestDistSq = distSq;
				nearest = witch;
			}
		}

		if (nearest != null) {
			this.setOwnerUuid(nearest.getUUID());
			// 产生爱心粒子表示认主成功
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.HEART,
					this.getX(), this.getY() + 0.8, this.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		}
	}

	/**
	 * 释放火环技能（完全复刻原版SSC ExplosionDamageEntityAction逻辑）
	 * 原版参数：power=3, explosion_damage_entity=false, entity_action={damage 8 on_fire + set_on_fire 10}
	 */
	private void useFireRing() {
		if (!(this.level() instanceof ServerLevel serverWorld)) return;

		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();

		// 音效
		serverWorld.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.5f, 1.0f);
		serverWorld.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 0.5f, 1.0f);
		serverWorld.playSound(null, x, y, z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 0.5f, 1.0f);

		// 粒子效果 - 外圈火焰（半径4格，64个采样点）
		for (int i = 0; i < 64; i++) {
			double angle = 2 * Math.PI * i / 64;
			double px = x + Math.cos(angle) * PARTICLE_OUTER_RADIUS;
			double pz = z + Math.sin(angle) * PARTICLE_OUTER_RADIUS;
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLAME, px, y + 0.5, pz, 1, 0.4, 0.6, 0.4, 0.01);
		}
		// 内圈火焰粒子
		for (int i = 0; i < 8; i++) {
			double angle = 2 * Math.PI * i / 8;
			double px = x + Math.cos(angle) * PARTICLE_INNER_RADIUS;
			double pz = z + Math.sin(angle) * PARTICLE_INNER_RADIUS;
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.FLAME, px, y + 0.5, pz, 1, 0.2, 0.3, 0.2, 0.04);
		}
		// 灵魂火焰粒子
		ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.5, z, 8, 2.0, 1.0, 2.0, 0.0);

		// 爆炸游戏事件（原版会触发）
		serverWorld.gameEvent(this, GameEvent.EXPLODE, this.position());

		// === 复刻原版 ExplosionDamageEntityAction 逻辑 ===
		Vec3 explosionPos = this.position();
		float q = FIRE_RING_EFFECT_RADIUS; // power * 2.0 = 6.0

		int k = Mth.floor(explosionPos.x() - q - 1.0);
		int l = Mth.floor(explosionPos.x() + q + 1.0);
		int r = Mth.floor(explosionPos.y() - q - 1.0);
		int s = Mth.floor(explosionPos.y() + q + 1.0);
		int t = Mth.floor(explosionPos.z() - q - 1.0);
		int u = Mth.floor(explosionPos.z() + q + 1.0);

		// 获取范围内所有实体（排除自身）
		List<Entity> entityList = serverWorld.getEntities(
				this, new AABB(k, r, t, l, s, u));

		ResourceKey<DamageType> onFireKey = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "on_fire"));

		for (Entity targetEntity : entityList) {
			// 跳过爆炸免疫实体
			if (targetEntity.ignoreExplosion(null)) continue;
			// 跳过非生物实体或不应受火环影响的实体
			if (!(targetEntity instanceof LivingEntity living)) continue;
			if (!shouldFireRingAffect(living)) continue;

			// 计算归一化距离
			double w = Math.sqrt(targetEntity.distanceToSqr(explosionPos)) / (double) q;
			if (w > 1.0) continue;

			double dx = targetEntity.getX() - explosionPos.x();
			double dy = (targetEntity instanceof PrimedTnt ? targetEntity.getY() : targetEntity.getEyeY()) - explosionPos.y();
			double dz = targetEntity.getZ() - explosionPos.z();
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist == 0.0) continue;

			dx /= dist;
			dy /= dist;
			dz /= dist;

			// 暴露度计算（视线检查）
			double exposure = Explosion.getSeenPercent(explosionPos, targetEntity);
			double intensity = (1.0 - w) * exposure;

			// explosion_damage_entity = false，不造成爆炸伤害
			// 但应用爆炸击退
			double knockbackIntensity;
			if (targetEntity instanceof LivingEntity le) {
				knockbackIntensity = intensity;  // ProtectionEnchantment removed in 1.21.1
			} else {
				knockbackIntensity = intensity;
			}
			targetEntity.setDeltaMovement(targetEntity.getDeltaMovement().add(
					dx * knockbackIntensity, dy * knockbackIntensity, dz * knockbackIntensity));

			// entity_action: damage 8 on_fire + set_on_fire 10秒
			living.hurt(living.damageSources().source(onFireKey, this), FIRE_RING_DAMAGE);
			living.igniteForSeconds(FIRE_RING_IGNITE_SECONDS);
		}
	}

	/**
	 * 火环伤害的目标筛选
	 */
	private boolean shouldFireRingAffect(LivingEntity entity) {
		if (!entity.isAlive()) return false;
		// 不影响劫掠阵营
		if (entity instanceof Raider) return false;
		if (entity instanceof Vex) return false;
		if (entity instanceof Witch) return false;
		if (entity instanceof WitchFamiliarEntity) return false;
		// 不影响原版使魔/堕落悦灵形态的玩家（劫掠阵营友军）
		if (entity instanceof Player player) {
			return !FormUtils.isAnyForm(player,
					FormIdentifiers.FALLEN_ALLAY_SP,
					VANILLA_FAMILIAR_FOX_3);
		}
		return true;
	}

	// ========== 持久化 ==========

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putInt("FireRingCooldown", fireRingCooldown);
		if (this.ownerUuid != null) {
			nbt.put("OwnerUUID", NbtUtils.createUUID(this.ownerUuid));
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		fireRingCooldown = nbt.getInt("FireRingCooldown");
		if (nbt.hasUUID("OwnerUUID")) {
			this.ownerUuid = nbt.getUUID("OwnerUUID");
		}
	}

	// ========== 音效 ==========

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.FOX_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FOX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FOX_DEATH;
	}

	// ========== GeoEntity 实现 ==========

	@Override
	public boolean fireImmune() {
		return true;
	}

	/**
	 * 归属劫掠阵营（ILLAGER组）
	 * 使 SSC 的 MobEntityTeamMixin 能正确识别女巫使魔为劫掠阵营成员，
	 * 从而让拥有 PillagerFriendlyPower 的使魔形态玩家被视为队友
	 */


	// ========== 免疫系统（与SP使魔形态一致） ==========

	/**
	 * 免疫与SSC原版使魔形态相同的14种药水效果
	 * （对应 form_familiar_fox_3_no_buff_effect.json）
	 */
	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		MobEffect type = effect.getEffect().value();
		if (type == MobEffects.POISON
				|| type == MobEffects.HUNGER
				|| type == MobEffects.MOVEMENT_SPEED
				|| type == MobEffects.DIG_SPEED
				|| type == MobEffects.DAMAGE_BOOST
				|| type == MobEffects.REGENERATION
				|| type == MobEffects.FIRE_RESISTANCE
				|| type == MobEffects.WATER_BREATHING
				|| type == MobEffects.NIGHT_VISION
				|| type == MobEffects.DAMAGE_RESISTANCE
				|| type == MobEffects.INVISIBILITY
				|| type == MobEffects.HEALTH_BOOST
				|| type == MobEffects.WITHER
				|| type == MobEffects.ABSORPTION) {
			return false;
		}
		return super.canBeAffected(effect);
	}

	/**
	 * 免疫浆果丛减速效果
	 */
	@Override
	public void makeStuckInBlock(BlockState state, Vec3 multiplier) {
		if (state.getBlock() instanceof SweetBerryBushBlock) {
			return; // 浆果丛不对女巫使魔产生减速
		}
		super.makeStuckInBlock(state, multiplier);
	}

	/**
	 * 免疫浆果丛伤害 + 使魔玩家攻击时复刻SSC原版劫掠阵营交互效果
	 * （对应 form_familiar_fox_3_no_attack_witch.json + hurt_when_attack_witch.json）
	 */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		// 浆果丛免疫
		if ("sweetBerryBush".equals(source.getMsgId())) {
			return false;
		}

		// 原版使魔/堕落悦灵形态玩家攻击女巫使魔 → 和SSC原版攻击劫掠阵营效果一致
		if (source.getEntity() instanceof Player player) {
			if (FormUtils.isAnyForm(player,
					FormIdentifiers.FALLEN_ALLAY_SP,
					VANILLA_FAMILIAR_FOX_3)) {
				handleFamiliarPlayerAttack(player);
				return false; // 不受伤害
			}
		}

		return super.hurt(source, amount);
	}

	/**
	 * 处理使魔玩家攻击女巫使魔的效果（复刻SSC原版机制）
	 * - 治疗女巫使魔20HP（no_attack_witch）
	 * - 玩家自伤1HP + 击退（hurt_when_attack_witch）
	 * - 播放末影之眼消散音效
	 */
	private void handleFamiliarPlayerAttack(Player player) {
		if (!(this.level() instanceof ServerLevel serverWorld)) return;

		// 治疗女巫使魔20HP
		this.heal(20.0f);

		// 播放音效
		serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ENDER_EYE_DEATH, SoundSource.NEUTRAL, 1.0f, 1.0f);

		// 玩家自伤1HP
		player.hurt(player.damageSources().generic(), 1.0f);

		// 玩家击退（向后 z:-0.5，向上 y:0.5，local_horizontal_normalized空间）
		Vec3 lookDir = player.getLookAngle();
		double horizontalLen = Math.sqrt(lookDir.x * lookDir.x + lookDir.z * lookDir.z);
		if (horizontalLen > 0.001) {
			// 归一化水平方向后施加击退
			player.push(
					-lookDir.x / horizontalLen * 0.5,
					0.5,
					-lookDir.z / horizontalLen * 0.5
			);
		} else {
			player.push(0, 0.5, 0);
		}
		player.hurtMarked = true;
	}

	/**
	 * 水中移动不减速（消除水中阻力）
	 */
	@Override
	public void travel(Vec3 movementInput) {
		if (this.isControlledByLocalInstance() && this.isInWater()) {
			// 水中使用更高的移动系数和更低的阻力，接近陆地速度
			this.moveRelative(0.04f, movementInput); // 原版水中为0.02f
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.setDeltaMovement(this.getDeltaMovement().scale(0.9)); // 原版水中为0.8
			if (!this.isNoGravity()) {
				this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
			}
			this.calculateEntityAnimation(false);
		} else {
			super.travel(movementInput);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// 移动动画（走路/待机）
		controllers.add(new AnimationController<>(this, "movement", 3, state -> {
			if (state.isMoving()) {
				state.setAnimation(RawAnimation.begin().then("walk", Animation.LoopType.LOOP));
			} else {
				state.setAnimation(RawAnimation.begin().then("idle", Animation.LoopType.LOOP));
			}
			return PlayState.CONTINUE;
		}));
		// 攻击动画
		controllers.add(new AnimationController<>(this, "attack", 0, state -> {
			if (this.swinging) {
				return state.setAndContinue(RawAnimation.begin().thenPlay("attack"));
			}
			state.getController().forceAnimationReset();
			return PlayState.STOP;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	// ========== 自定义AI目标 ==========

	/**
	 * 跟随主人女巫目标（类似狼的跟随主人逻辑）
	 * 当距离超过 maxDistance 时传送到主人身边
	 * 在 minDistance 以内时停止移动
	 */
	static class FollowOwnerWitchGoal extends Goal {
		private final WitchFamiliarEntity familiar;
		private final double speed;
		private final float maxDistance;      // 超过此距离传送
		private final float minDistance;      // 靠近到此距离停止
		private final float startDistance;    // 距离超过此值开始跟随
		private Witch owner;
		private int updateCountdown;

		public FollowOwnerWitchGoal(WitchFamiliarEntity familiar, double speed, float maxDistance, float minDistance, float startDistance) {
			this.familiar = familiar;
			this.speed = speed;
			this.maxDistance = maxDistance;
			this.minDistance = minDistance;
			this.startDistance = startDistance;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Witch witch = this.familiar.getOwnerWitch();
			if (witch == null) return false;
			// 正在攻击时不跟随（让战斗目标优先）
			if (this.familiar.getTarget() != null) return false;
			if (this.familiar.distanceToSqr(witch) < (double) this.startDistance * this.startDistance) return false;
			this.owner = witch;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			if (this.familiar.getNavigation().isDone()) return false;
			if (this.familiar.getTarget() != null) return false;
			return this.familiar.distanceToSqr(this.owner) > (double) this.minDistance * this.minDistance;
		}

		@Override
		public void start() {
			this.updateCountdown = 0;
		}

		@Override
		public void stop() {
			this.owner = null;
			this.familiar.getNavigation().stop();
		}

		@Override
		public void tick() {
			this.familiar.getLookControl().setLookAt(this.owner, 10.0f, (float) this.familiar.getMaxHeadXRot());

			if (--this.updateCountdown <= 0) {
				this.updateCountdown = 10;  // 每10 tick更新一次路径

				double distSq = this.familiar.distanceToSqr(this.owner);

				// 距离过远：传送到主人身边
				if (distSq >= (double) this.maxDistance * this.maxDistance) {
					tryTeleportToOwner();
				} else {
					this.familiar.getNavigation().moveTo(this.owner, this.speed);
				}
			}
		}

		/**
		 * 传送到主人身边（类似狼传送逻辑）
		 */
		private void tryTeleportToOwner() {
			BlockPos ownerPos = this.owner.blockPosition();
			for (int i = 0; i < 10; i++) {
				int dx = this.familiar.getRandom().nextIntBetweenInclusive(-3, 3);
				int dz = this.familiar.getRandom().nextIntBetweenInclusive(-3, 3);
				BlockPos target = ownerPos.offset(dx, 0, dz);
				// 简单检查：非固体方块（脚下）且站立位也非固体
				if (this.familiar.level().getBlockState(target).isAir()
						|| !this.familiar.level().getBlockState(target).isRedstoneConductor(this.familiar.level(), target)) {
					this.familiar.moveTo(
							target.getX() + 0.5, ownerPos.getY(), target.getZ() + 0.5,
							this.familiar.getYRot(), this.familiar.getXRot());
					this.familiar.getNavigation().stop();
					return;
				}
			}
		}
	}

	/**
	 * 复制主人女巫的攻击目标（类似狼跟随主人攻击逻辑）
	 */
	static class CopyOwnerTargetGoal extends TargetGoal {
		private final WitchFamiliarEntity familiar;
		private LivingEntity ownerTarget;
		private int lastCheckTime;

		public CopyOwnerTargetGoal(WitchFamiliarEntity familiar) {
			super(familiar, false);
			this.familiar = familiar;
		}

		@Override
		public boolean canUse() {
			Witch owner = this.familiar.getOwnerWitch();
			if (owner == null) return false;
			this.ownerTarget = owner.getTarget();
			if (this.ownerTarget == null) return false;
			// 不能攻击友军
			return this.familiar.canAttack(this.ownerTarget);
		}

		@Override
		public void start() {
			this.familiar.setTarget(this.ownerTarget);
			super.start();
			this.lastCheckTime = this.familiar.tickCount;
		}

		@Override
		public boolean canContinueToUse() {
			// 每40 tick检查一次主人的目标是否变化
			if (this.familiar.tickCount - this.lastCheckTime > 40) {
				Witch owner = this.familiar.getOwnerWitch();
				if (owner != null) {
					LivingEntity newTarget = owner.getTarget();
					if (newTarget != null && newTarget != this.familiar.getTarget() && this.familiar.canAttack(newTarget)) {
						this.familiar.setTarget(newTarget);
					}
				}
				this.lastCheckTime = this.familiar.tickCount;
			}
			return super.canContinueToUse();
		}
	}
}