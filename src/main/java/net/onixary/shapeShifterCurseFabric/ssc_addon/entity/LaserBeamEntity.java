package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * 荧光幼灵主要技能「法阵激光」实体（自驱状态机）。
 *
 * <p>阶段：
 * <ul>
 *   <li>CHARGE（0~7s=140t）：玩家定身（ROOTED，仅锁移动/跳跃、视角自由）。前方放法阵；
 *       0~2.5s 四条 END_ROD 白线从法阵四角延伸到 24 格；2.5~6.5s 四线随法阵旋转 + 加速向中心聚拢、
 *       末尾聚到较小间隔并慢慢停转；6.5~7s 停稳。可被净化打断（取消、无 CD）。</li>
 *   <li>RELEASE（7~10s=60t）：视角限速 5°/s（LASER_STATE=2 由 ViewRateLimitMixin 处理）。
 *       沿朝向发射直径 5 格、长 24 格的穿墙光柱，每 4t 造成 6 点魔法伤害（默认白名单）；
 *       周围白/青螺旋粒子沿光柱前进。不可被打断。</li>
 *   <li>FADE（10~11.5s=30t）：光柱半径 1.5s 内缩小后随法阵消失；结束后设 20s CD。</li>
 * </ul>
 * 渲染（法阵 + 光柱）由 {@code FluorescentLaserRenderer} 负责；四线/螺旋/爆裂粒子服务端生成（所有人可见）。
 */
public class LaserBeamEntity extends Entity {

	// ===== 时序 =====
	private static final int CHARGE_TICKS = 140;      // 7 秒蓄力
	private static final int RELEASE_TICKS = 60;      // 3 秒激光
	private static final int FADE_TICKS = 30;         // 1.5 秒消退
	private static final int CD_TICKS = 400;          // 20 秒 CD

	// ===== 几何 =====
	private static final double ARRAY_DIST = 3.0;     // 法阵在玩家前方距离
	private static final double BEAM_LENGTH = 32.0;    // 光柱/四线长度
	private static final double BEAM_RADIUS = 2.5;     // 光柱半径（直径 5 格）

	// ===== 海晶荧光坠增强（三连发单道视觉）=====
	private static final float ENH_BEAM_LENGTH = 24.0f;   // 增强激光最大射程 24 格
	private static final float ENH_BEAM_RADIUS = 0.75f;   // 增强光柱半径（原 2.5 的 30%）
	private static final float ENH_ARRAY_SCALE = 0.3f;    // 增强法阵缩到原 30%
	private static final int ENH_SHOT_TICKS = 8;          // 增强单道存活 8t（纯视觉）
	// 注：增强激光射程由 Manager.ENH_BEAM_LENGTH 管（resolveHitPoint 射线长度），实体不再单独存 ENH_LENGTH（渲染改用实时距离）

	// ===== 伤害 =====
	private static final int DAMAGE_INTERVAL = 10;      // 每 10t 结算
	private static final float DAMAGE = 20.0f;          // 每次 20 魔法伤害（释放 60t 共 6 次 = 120）

	// ===== 同步数据（供渲染器）=====
	private static final TrackedData<Integer> PHASE = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);       // 0 CHARGE / 1 RELEASE / 2 FADE
	private static final TrackedData<Integer> PHASE_TICK = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);  // 当前阶段已用 tick
	private static final TrackedData<Integer> OWNER_ID = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	// 海晶荧光坠增强：单道视觉标记 + 发射方向（Vec3d 拆 3 个 float 同步给渲染器）
	private static final TrackedData<Boolean> ENHANCED = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Float> DIR_X = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> DIR_Y = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> DIR_Z = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> FIRING = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> ARRAYS_LEFT = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> FIRING_IDX = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.INTEGER);
	// 海晶荧光坠增强：实时锁定点（准星落点，服务端每 tick 同步）——供渲染器让剩余待发射法阵预瞑转向
	private static final TrackedData<Float> LOCK_X = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> LOCK_Y = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> LOCK_Z = DataTracker.registerData(LaserBeamEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private enum Phase { CHARGE, RELEASE, FADE }

	private Phase phase = Phase.CHARGE;
	private int phaseTicks = 0;
	private UUID ownerUuid;
	private float damage = DAMAGE;
	private int releaseTicks = RELEASE_TICKS;
	private int enhFiringTicks = 0;   // 增强发射态剩余 tick

	private static final DustParticleOptions CYAN =
			new DustParticleOptions(new Vector3f(0.35f, 0.90f, 1.0f), 1.3f);
	private static final DustParticleOptions WHITE =
			new DustParticleOptions(new Vector3f(0.92f, 0.98f, 1.0f), 1.2f);

	public LaserBeamEntity(EntityType<?> type, Level world) {
		super(type, world);
		this.noPhysics = true;
	}

	public LaserBeamEntity(Level world, ServerPlayer owner) {
		super(SscAddon.LASER_BEAM_ENTITY, world);
		this.ownerUuid = owner.getUuid();
		this.setPosition(owner.getX(), owner.getEyeY(), owner.getZ());
		this.dataTracker.set(OWNER_ID, owner.getId());
		this.noClip = true;
	}

	/** 海晶荧光坠增强：持久「待机法阵」实体——combo 期间跟随玩家、在其前方显示旋转法阵（像原激光）；
	 *  发射时 startFiring 进 8t 发射态额外画光柱。不定身/不伤害/不设 CD（由 Manager 管，combo 结束 discard）。 */
	public LaserBeamEntity(World world, ServerPlayerEntity owner, boolean enhancedStandby) {
		super(SscAddon.LASER_BEAM_ENTITY, world);
		this.ownerUuid = owner.getUuid();
		this.setPosition(owner.getX(), owner.getEyeY(), owner.getZ());
		this.dataTracker.set(OWNER_ID, owner.getId());
		this.dataTracker.set(ENHANCED, true);
		this.dataTracker.set(PHASE, 1);
		this.noClip = true;
	}

	/** 发射一道：进入 8t 发射态，记录发射法阵索引 + 发射瞬间定格的世界锁定点。
	 *  激光落点固定为此世界点，法阵随玩家移动时激光始终指向它（追踪锁定，方向由渲染器/伤害按当前法阵位置实时算）。 */
	public void startFiring(int arrayIdx, Vec3d fireLock) {
		this.dataTracker.set(FIRING, true);
		this.dataTracker.set(FIRING_IDX, arrayIdx);
		this.dataTracker.set(DIR_X, (float) fireLock.x);
		this.dataTracker.set(DIR_Y, (float) fireLock.y);
		this.dataTracker.set(DIR_Z, (float) fireLock.z);
		this.enhFiringTicks = ENH_SHOT_TICKS;
	}

	public boolean isFiring() {
		return this.dataTracker.get(FIRING);
	}

	/** combo 当前剩余法阵数（渲染器据此画斜后方剩余法阵）。 */
	public void setArraysLeft(int n) {
		this.dataTracker.set(ARRAYS_LEFT, n);
	}

	public int getArraysLeft() {
		return this.dataTracker.get(ARRAYS_LEFT);
	}

	public int getFiringIdx() {
		return this.dataTracker.get(FIRING_IDX);
	}

	/** 服务端每 tick 同步的实时锁定点（准星落点），供渲染器让剩余待发射法阵预瞑转向。 */
	public void setLockPoint(Vec3d p) {
		this.dataTracker.set(LOCK_X, (float) p.x);
		this.dataTracker.set(LOCK_Y, (float) p.y);
		this.dataTracker.set(LOCK_Z, (float) p.z);
	}

	/** 锁定点（未同步时返回 null，渲染器回退朝准星）。 */
	public Vec3d getLockPoint() {
		float x = this.dataTracker.get(LOCK_X);
		float y = this.dataTracker.get(LOCK_Y);
		float z = this.dataTracker.get(LOCK_Z);
		if (x == 0f && y == 0f && z == 0f) return null;
		return new Vec3d(x, y, z);
	}

	@Override
	protected void initDataTracker() {
		this.dataTracker.startTracking(PHASE, 0);
		this.dataTracker.startTracking(PHASE_TICK, 0);
		this.dataTracker.startTracking(OWNER_ID, 0);
		this.dataTracker.startTracking(ENHANCED, false);
		this.dataTracker.startTracking(DIR_X, 0f);
		this.dataTracker.startTracking(DIR_Y, 0f);
		this.dataTracker.startTracking(DIR_Z, 0f);
		this.dataTracker.startTracking(FIRING, false);
		this.dataTracker.startTracking(ARRAYS_LEFT, 3);
		this.dataTracker.startTracking(FIRING_IDX, 0);
		this.dataTracker.startTracking(LOCK_X, 0f);
		this.dataTracker.startTracking(LOCK_Y, 0f);
		this.dataTracker.startTracking(LOCK_Z, 0f);
	}

	// ===== 渲染器读取 =====
	public int getPhaseId() {
		return this.entityData.get(PHASE);
	}

	public int getPhaseTick() {
		return this.entityData.get(PHASE_TICK);
	}

	public int getTrackedOwnerId() {
		return this.entityData.get(OWNER_ID);
	}

	public boolean isEnhanced() {
		return this.dataTracker.get(ENHANCED);
	}

	/** 增强单道发射瞬间定格的世界锁定点（渲染器/伤害据当前法阵位置算方向与长度，实现落点固定的追踪锁定）。 */
	public Vec3d getFireLock() {
		return new Vec3d(this.dataTracker.get(DIR_X), this.dataTracker.get(DIR_Y), this.dataTracker.get(DIR_Z));
	}

	public float enhArrayScale() {
		return ENH_ARRAY_SCALE;
	}

	public float enhBeamRadius() {
		return ENH_BEAM_RADIUS;
	}

	public static double arrayDist() {
		return ARRAY_DIST;
	}

	public double beamLength() {
		return BEAM_LENGTH;
	}

	public static double beamRadius() {
		return BEAM_RADIUS;
	}

	/** 增强单道光柱达 24 格、碰撞盒仅 0.5，放宽视锥剔除避免离屏被 cull。 */
	@Override
	public boolean shouldRender(double distance) {
		return true;
	}

	@Override
	public Box getVisibilityBoundingBox() {
		// 增强单道光柱最长 24 格、起点在玩家斜后方，扩大可见盒避免视锥剔除导致法阵/光柱整体不渲染
		return this.dataTracker.get(ENHANCED) ? this.getBoundingBox().expand(48.0) : super.getVisibilityBoundingBox();
	}

	@Override
	public void tick() {
		super.tick();
		// 海晶荧光坠增强：持久「待机法阵」——combo 期间跟随玩家、前方显示旋转法阵；发射态额外画光柱 8t；由 Manager combo 结束 discard
		if (this.dataTracker.get(ENHANCED)) {
			if (this.getWorld().isClient) return;
			if (!(this.getWorld() instanceof ServerWorld sw2)) return;
			ServerPlayerEntity ownerE = getOwner(sw2);
			if (ownerE == null || ownerE.isRemoved() || ownerE.isDead() || !FormUtils.isAxolotlFluorescent(ownerE)) {
				this.discard();
				return;
			}
			this.setPosition(ownerE.getX(), ownerE.getEyeY(), ownerE.getZ());   // 跟随玩家
			phaseTicks++;
			this.dataTracker.set(PHASE_TICK, phaseTicks);
			if (this.dataTracker.get(FIRING)) {
				enhFiringTicks--;
				if (enhFiringTicks <= 0) this.dataTracker.set(FIRING, false);
			}
			return;
		}
		if (this.getWorld().isClient) {
			return;   // 渲染由 FluorescentLaserRenderer 负责；粒子由服务端生成
		}
		if (!(this.level() instanceof ServerLevel sw)) return;

		ServerPlayer owner = getOwner(sw);
		if (owner == null || owner.isRemoved() || owner.isDeadOrDying()
				|| !FormUtils.isAxolotlFluorescent(owner)) {
			cancelNoCd(owner);
			this.discard();
			return;
		}

		// 跟随玩家眼部（法阵在前方由 aim 计算）
		this.setPos(owner.getX(), owner.getEyeY(), owner.getZ());

		// 定身：每 tick 刷新 ROOTED（仅锁移动，视角自由；释放期视角限速由 mixin 处理）
		owner.addEffect(new MobEffectInstance(SscAddon.ROOTED_ENTRY, 8, 0, false, false, false));

		Vec3 aim = owner.getViewVector(1.0f).normalize();
		Vec3 arrayPos = new Vec3(owner.getX(), owner.getEyeY(), owner.getZ()).add(aim.scale(ARRAY_DIST));

		phaseTicks++;
		switch (phase) {
			case CHARGE -> tickCharge(sw, owner, aim, arrayPos);
			case RELEASE -> tickRelease(sw, owner, aim, arrayPos);
			case FADE -> tickFade(sw, owner, aim, arrayPos);
		}
		this.entityData.set(PHASE_TICK, phaseTicks);
	}

	// ==================== CHARGE ====================
	private void tickCharge(ServerLevel sw, ServerPlayer owner, Vec3 aim, Vec3 arrayPos) {
		// 净化打断：取消，返还 40% CD（进 60% CD）
		if (owner.hasEffect(SscAddon.PURIFIED_ENTRY)) {
			cancelWithInterruptCd(owner);
			sw.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
					SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.2f);
			this.discard();
			return;
		}
		PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 1);

		// 蓄力音效
		if (phaseTicks == 1) {
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.8f);
		}
		if (phaseTicks % 12 == 0) {
			float p = phaseTicks / (float) CHARGE_TICKS;
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.CONDUIT_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.8f + p * 0.8f);
		}

		// 四条白线由渲染器绘制（客户端，无粒子残留）；法阵核心发光粒子改为客户端渲染器按视角生成
		// （第一人称不生成、第三人称生成），避免服务端粒子无法区分视角导致第一人称被遮挡
		if (phaseTicks >= CHARGE_TICKS) {
			phase = Phase.RELEASE;
			phaseTicks = 0;
			this.entityData.set(PHASE, 1);
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 2);
			// 发射音
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.0f);
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.7f);
		}
	}

	// ==================== RELEASE ====================
	private void tickRelease(ServerLevel sw, ServerPlayer owner, Vec3 aim, Vec3 arrayPos) {
		PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 2);
		// 螺旋粒子沿光柱前进
		spawnBeamSpiral(sw, arrayPos, aim, BEAM_RADIUS);
		// 伤害：每 4t 一次，5 格直径穿墙圆柱
		if (phaseTicks % DAMAGE_INTERVAL == 0) {
			beamDamage(sw, owner, arrayPos, aim, BEAM_RADIUS);
		}
		if (phaseTicks % 8 == 0) {
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.7f, 1.4f);
		}
		if (phaseTicks >= releaseTicks) {
			phase = Phase.FADE;
			phaseTicks = 0;
			this.entityData.set(PHASE, 2);
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 3);
		}
	}

	// ==================== FADE ====================
	private void tickFade(ServerLevel sw, ServerPlayer owner, Vec3 aim, Vec3 arrayPos) {
		double shrink = 1.0 - phaseTicks / (double) FADE_TICKS;
		double r = BEAM_RADIUS * Math.max(0.0, shrink);
		spawnBeamSpiral(sw, arrayPos, aim, r);
		if (phaseTicks >= FADE_TICKS) {
			// 完全消失 → 进 CD、解除定身、清状态
			owner.removeEffect(SscAddon.ROOTED_ENTRY);
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 0);
			PowerUtils.setResourceValueAndSync(owner, FormIdentifiers.SP_PRIMARY_CD, CD_TICKS);
			sw.playSound(null, arrayPos.x, arrayPos.y, arrayPos.z,
					SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.0f);
			this.discard();
		}
	}

	// ==================== 四线粒子 ====================
	// ==================== 光柱螺旋粒子 ====================
	private void spawnBeamSpiral(ServerLevel sw, Vec3 arrayPos, Vec3 aim, double radius) {
		if (radius <= 0.01) return;
		Vec3 right = aim.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0e-6) right = new Vec3(1, 0, 0);
		right = right.normalize();
		Vec3 up = right.cross(aim).normalize();
		double beamLen = beamLength();
		int steps = (int) (beamLen / 0.8);
		double baseAng = phaseTicks * 0.6;
		for (int s = 0; s <= steps; s++) {
			double d = beamLen * s / steps;
			// 双螺旋（白 + 青，相位差 π）
			double ang1 = baseAng + d * 0.9;
			double ang2 = ang1 + Math.PI;
			Vec3 axis = arrayPos.add(aim.scale(d));
			Vec3 o1 = right.scale(Math.cos(ang1) * radius).add(up.scale(Math.sin(ang1) * radius));
			Vec3 o2 = right.scale(Math.cos(ang2) * radius).add(up.scale(Math.sin(ang2) * radius));
			if (s % 2 == 0) {
				Vec3 p1 = axis.add(o1);
				sw.sendParticles(WHITE, p1.x, p1.y, p1.z, 1, 0, 0, 0, 0.0);
				Vec3 p2 = axis.add(o2);
				sw.sendParticles(CYAN, p2.x, p2.y, p2.z, 1, 0, 0, 0, 0.0);
			}
			// 芯部发光
			if (s % 3 == 0) {
				sw.sendParticles(ParticleTypes.END_ROD, axis.x, axis.y, axis.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
	}

	// ==================== 伤害 ====================
	private void beamDamage(ServerLevel sw, ServerPlayer owner, Vec3 arrayPos, Vec3 aim, double radius) {
		double beamLen = beamLength();
		Vec3 end = arrayPos.add(aim.scale(beamLen));
		AABB box = new AABB(arrayPos, end).inflate(radius);
		List<LivingEntity> targets = sw.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(ownerUuid));
		ResourceKey<net.minecraft.world.damagesource.DamageType> key =
				ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "magic"));
		for (LivingEntity t : targets) {
			// 默认白名单豁免
			if (WhitelistUtils.isProtected(owner, t)) continue;
			// 到光柱轴线的距离（圆柱判定，穿墙 → 不检查方块遮挡）
			Vec3 toT = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(arrayPos);
			double proj = toT.dot(aim);
			if (proj < 0 || proj > beamLen) continue;
			Vec3 closest = arrayPos.add(aim.scale(proj));
			double distSq = t.position().add(0, t.getBbHeight() * 0.5, 0).distanceToSqr(closest);
			if (distSq > radius * radius) continue;
			t.hurt(t.damageSources().source(key, owner, owner), damage);
		}
	}

	// ==================== 取消/清理 ====================
	private void cancelNoCd(ServerPlayer owner) {
		if (owner != null) {
			owner.removeEffect(SscAddon.ROOTED_ENTRY);
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 0);
		}
	}

	/** 被净化打断时取消：返还 40% CD（进 60% CD = 400 × 0.6 = 240t = 12 秒）。 */
	private void cancelWithInterruptCd(ServerPlayer owner) {
		if (owner != null) {
			owner.removeEffect(SscAddon.ROOTED_ENTRY);
			PowerUtils.setResourceValueAndSync(owner, LASER_STATE, 0);
			PowerUtils.setResourceValueAndSync(owner, FormIdentifiers.SP_PRIMARY_CD, (int)(CD_TICKS * 0.6));
		}
	}

	private ServerPlayer getOwner(ServerLevel sw) {
		if (ownerUuid == null) return null;
		var server = sw.getServer();
		return server == null ? null : server.getPlayerList().getPlayer(ownerUuid);
	}

	/** laser_state 资源 id（0 空闲 /1 蓄力 /2 释放 /3 消退）。 */
	public static final ResourceLocation LASER_STATE =
			ResourceLocation.fromNamespaceAndPath("my_addon", "form_axolotl_fluorescent_laser_state");

	/** 客户端查询某玩家的活跃激光实体（供视角限速读取 phase/phaseTick）。 */
	public static LaserBeamEntity getActiveForClient(net.minecraft.client.player.LocalPlayer player) {
		net.minecraft.client.multiplayer.ClientLevel w = net.minecraft.client.Minecraft.getInstance().level;
		if (w == null) return null;
		for (Entity e : w.entitiesForRendering()) {
			if (e instanceof LaserBeamEntity laser && !laser.isEnhanced()) {
				if (player != null && player.getId() == laser.getTrackedOwnerId()) return laser;
			}
		}
		return null;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		nbt.putInt("Phase", phase.ordinal());
		nbt.putInt("PhaseTicks", phaseTicks);
		if (ownerUuid != null) nbt.putUUID("Owner", ownerUuid);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		int p = nbt.contains("Phase") ? nbt.getInt("Phase") : 0;
		phase = Phase.values()[Mth.clamp(p, 0, Phase.values().length - 1)];
		phaseTicks = nbt.contains("PhaseTicks") ? nbt.getInt("PhaseTicks") : 0;
		if (nbt.hasUUID("Owner")) ownerUuid = nbt.getUUID("Owner");
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
		return new ClientboundAddEntityPacket(this, entityTrackerEntry);
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}