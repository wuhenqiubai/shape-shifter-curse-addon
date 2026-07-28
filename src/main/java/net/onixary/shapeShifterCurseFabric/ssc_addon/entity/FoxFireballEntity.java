package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.List;

/**
 * red（红堕落使魔）次要技能 - 狐火火球。
 * 直线飞行（无抛物线、不穿墙）：0~12 格速度 20→2 b/s 线性衰减（约 1.5 秒，期间穿过生物造成 8 魔法穿透 + 额外爆破）；
 * 12 格后固定 2 格/s 飞行至多 3 秒（达 18 格上限）；撞墙立即爆炸，12 格后碍生物也爆炸，未命中则泡泡裂开消散。
 * 爆炸：6 格球范围 6 物理（不穿墙） + 仅直接碰到火球者腰部火环向 2 格内连锁 4 物理（不再二次连锁）。
 */
public class FoxFireballEntity extends Projectile implements ItemSupplier {

    private static final double ARM_DISTANCE = 12.0;   // 12 格后才进入杀伤（碰墙/生物爆炸）
    private static final double HIT_RADIUS = 2.0;      // 命中/穿透判定球半径
    private static final double EXPLODE_RADIUS = 6.0;  // 爆炸球半径
    private static final double CHAIN_RADIUS = 2.0;    // 连锁半径
    private static final float PIERCE_DAMAGE = 8.0f;   // 前 12 格穿透（魔法）
    private static final float EXPLODE_DAMAGE = 6.0f;  // 爆炸（物理）
    private static final float CHAIN_DAMAGE = 4.0f;    // 连锁（物理）
    private static final int RING_DURATION = 7;        // 腰部火环扩散动画帧数（半径 0→2）
    private static final double PHASE2_SPEED = 2.0;    // 12 格后固定 2 格/s
    private static final int PHASE2_DURATION = 60;     // 12 格后最多飞 3 秒（60 tick → 18 格上限）

    private Vec3 direction = new Vec3(0, 0, 1);
    private double distanceTraveled = 0;
    private int ticksAlive = 0;
    private int phase2Tick = 0;                  // 12 格后已飞 tick 数
    private boolean exploded = false;
    private boolean exploding = false;           // 爆炸后停止移动、等待所有火环播完即消失
    private final java.util.List<float[]> activeRings = new java.util.ArrayList<>();   // 活跃火环 [cx,cy,cz,已播放tick]
    private final java.util.Set<java.util.UUID> piercedEntities = new java.util.HashSet<>();

    public FoxFireballEntity(EntityType<? extends FoxFireballEntity> type, Level world) {
        super(type, world);
    }

    public FoxFireballEntity(Level world, LivingEntity owner) {
        super(SscAddon.FOX_FIREBALL_ENTITY, world);
        this.setOwner(owner);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
    }

    public void setDirection(Vec3 dir) {
        this.direction = dir.normalize();
        this.setDeltaMovement(this.direction);   // 用速度把方向编进 spawn 包，供客户端预测移动
    }

    /** 速度曲线（格/tick = b/s ÷ 20）：0~12 格按距离 20→2 线性递减（约 1.5 秒走完），12 格后固定 2 格/s。 */
    private double speedPerTick(double d) {
        double bps;
        if (d < ARM_DISTANCE) {
            bps = 20.0 - (20.0 - 2.0) * (d / ARM_DISTANCE);  // 20 → 2 线性递减（按距离）
        } else {
            bps = PHASE2_SPEED;                              // 12 格后固定 2 格/s
        }
        return bps / 20.0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        ticksAlive++;
        if (this.level().isClientSide) {
            // 客户端确定性预测移动（与服务端同一速度曲线），渲染插值平滑，避免只靠 tracker 同步的卡顿
            Vec3 v = this.getDeltaMovement();
            Vec3 dir = v.lengthSqr() > 1.0e-6 ? v.normalize() : direction;
            double speed = speedPerTick(distanceTraveled);
            this.setPos(this.getX() + dir.x * speed, this.getY() + dir.y * speed, this.getZ() + dir.z * speed);
            distanceTraveled += speed;
            return;
        }
        if (!(this.level() instanceof ServerLevel sw)) return;

        // 每帧推进并绘制所有活跃火环（穿透/爆炸触发的额外爆炸动画）
        updateActiveRings(sw);

        if (exploding) {
            // 已爆炸：原地等待所有火环播完即消失
            if (activeRings.isEmpty()) this.discard();
            return;
        }

        boolean armed = distanceTraveled >= ARM_DISTANCE;  // 12 格后才有杀伤
        double speed = speedPerTick(distanceTraveled);
        Vec3 velocity = direction.scale(speed);
        Vec3 from = this.position();
        Vec3 to = from.add(velocity);

        // 墙壁碰撞：火球不穿墙，撞墙立即触发爆破（任何距离）
        BlockHitResult blockHit = sw.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            Vec3 hp = blockHit.getLocation();
            this.setPos(hp.x, hp.y, hp.z);
            explode(sw, null);
            return;
        }

        this.setPos(to.x, to.y, to.z);
        distanceTraveled += speed;

        if (armed) {
            // 12 格后：2 格球命中非白名单生物即爆炸
            LivingEntity target = findTarget(sw);
            if (target != null) {
                explode(sw, target);
                return;
            }
        } else {
            // 前 12 格：穿过生物造成穿透伤害 + 额外爆炸（火球不灭继续飞）
            pierceTargets(sw);
        }
        spawnTrail(sw);

        // 12 格后最多再飞 3 秒（达 18 格），未命中则原地触发一次爆炸后消失
        if (distanceTraveled >= ARM_DISTANCE) {
            phase2Tick++;
            if (phase2Tick >= PHASE2_DURATION) {
                explode(sw, null);   // 未命中飞到射程上限：原地触发一次爆炸（无连锁）后消失
                return;
            }
        }
        if (ticksAlive > 200) this.discard();
    }

    private LivingEntity findTarget(ServerLevel world) {
        AABB box = this.getBoundingBox().inflate(HIT_RADIUS);
        Vec3 c = this.position();
        List<LivingEntity> list = world.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this.getOwner() && e.isAlive() && !e.isSpectator()
                        && e.distanceToSqr(c.x, c.y, c.z) <= HIT_RADIUS * HIT_RADIUS);
        for (LivingEntity e : list) {
            if (this.getOwner() instanceof ServerPlayer op && WhitelistUtils.isProtected(op, e)) continue;
            return e;
        }
        return null;
    }

    /** 前 12 格穿透：对 2 格球内每个非白名单生物造成一次 8 魔法穿透伤害（去重，火球不灭）。 */
    private void pierceTargets(ServerLevel world) {
        AABB box = this.getBoundingBox().inflate(HIT_RADIUS);
        Vec3 c = this.position();
        LivingEntity owner = this.getOwner() instanceof LivingEntity le ? le : null;
        List<LivingEntity> list = world.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this.getOwner() && e.isAlive() && !e.isSpectator()
                        && e.distanceToSqr(c.x, c.y, c.z) <= HIT_RADIUS * HIT_RADIUS
                        && !piercedEntities.contains(e.getUUID()));
        for (LivingEntity e : list) {
            if (this.getOwner() instanceof ServerPlayer op && WhitelistUtils.isProtected(op, e)) continue;
            e.hurt(magicSource(e, owner), PIERCE_DAMAGE);
            piercedEntities.add(e.getUUID());
            applyFoxFireBurn(e);
            double ex = e.getX(), ey = e.getY() + e.getBbHeight() * 0.5, ez = e.getZ();
            world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ex, ey, ez, 8, 0.3, 0.3, 0.3, 0.02);
            world.sendParticles(ParticleTypes.FLAME, ex, ey, ez, 6, 0.25, 0.25, 0.25, 0.02);
            world.playSound(null, ex, ey, ez, SoundEvents.BLAZE_HURT, SoundSource.PLAYERS, 0.4f, 1.6f);
            triggerExtraExplosion(world, owner, e);   // 穿透段也触发额外爆炸（腰部火环 + 连锁）
        }
    }

    /** 火球本体：2 格直径（半径 1）火焰球 + 短拖尾（双火焰 + 稀疏烟雾）+ 岩浆火星 + 熔岩滴落。 */
    private void spawnTrail(ServerLevel w) {
        double x = this.getX(), y = this.getY(), z = this.getZ();
        RandomSource rnd = this.random;
        for (int i = 0; i < 10; i++) {
            Vec3 p = randomInSphere(1.0, rnd);
            w.sendParticles(ParticleTypes.FLAME, x + p.x, y + p.y, z + p.z, 1, 0, 0, 0, 0.01);
        }
        for (int i = 0; i < 7; i++) {
            Vec3 p = randomInSphere(1.0, rnd);
            w.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x + p.x, y + p.y, z + p.z, 1, 0, 0, 0, 0.01);
        }
        // 岩浆火星：从火球表面随机迸出的小火花。
        if (rnd.nextFloat() < 0.3f) {
            Vec3 p = randomInSphere(0.4, rnd);
            w.sendParticles(ParticleTypes.LAVA, x + p.x, y + p.y, z + p.z, 1, 0.003, 0.008, 0.003, 0.0);
        }
        // 熔岩往下滴落：火球下方零星滴落的熔岩粒子。
        if (rnd.nextFloat() < 0.07f) {
            w.sendParticles(ParticleTypes.FALLING_LAVA,
                    x + (rnd.nextDouble() - 0.5) * 1.0,
                    y - 0.6 + (rnd.nextDouble() - 0.5) * 0.8,
                    z + (rnd.nextDouble() - 0.5) * 1.0,
                    1, 0, -0.1, 0, 0.01);
        }
        Vec3 back = direction.scale(-0.5);
        w.sendParticles(ParticleTypes.FLAME, x + back.x, y + back.y, z + back.z, 2, 0.15, 0.15, 0.15, 0.0);
        w.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x + back.x, y + back.y, z + back.z, 1, 0.12, 0.12, 0.12, 0.0);
        w.sendParticles(ParticleTypes.SMOKE, x + back.x * 1.5, y + back.y * 1.5, z + back.z * 1.5, 1, 0.1, 0.1, 0.1, 0.0);
    }

    private void explode(ServerLevel w, LivingEntity directTarget) {
        if (exploded) return;
        exploded = true;
        double x = this.getX(), y = this.getY(), z = this.getZ();
        LivingEntity owner = this.getOwner() instanceof LivingEntity le ? le : null;

        // 爆炸粒子 + 音效
        spawnExplosionParticles(w, x, y, z);
        w.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.4f);

        // 6 格球范围物理伤害；视线被方块阻挡的目标不受伤（爆炸不穿墙）
        AABB box = new AABB(x - EXPLODE_RADIUS, y - EXPLODE_RADIUS, z - EXPLODE_RADIUS,
                x + EXPLODE_RADIUS, y + EXPLODE_RADIUS, z + EXPLODE_RADIUS);
        List<LivingEntity> affected = w.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !e.isSpectator()
                        && e.distanceToSqr(x, y, z) <= EXPLODE_RADIUS * EXPLODE_RADIUS
                        && !(owner instanceof ServerPlayer op && WhitelistUtils.isProtected(op, e))
                        && hasLineOfSight(w, x, y, z, e));
        for (LivingEntity e : affected) {
            // 爆破伤害按距离衰减：≤2 格全额，2~6 格线性衰减至最低 1
            double dist = Math.sqrt(e.distanceToSqr(x, y, z));
            float dmg;
            if (dist <= 2.0) {
                dmg = EXPLODE_DAMAGE;
            } else {
                dmg = (float) (EXPLODE_DAMAGE - (EXPLODE_DAMAGE - 1.0) * (dist - 2.0) / (EXPLODE_RADIUS - 2.0));
                if (dmg < 1.0f) dmg = 1.0f;
            }
            e.hurt(physicalSource(e, owner), dmg);
            applyFoxFireBurn(e);
        }
        // 仅“直接碰到火球”的生物触发额外爆破（腰部火环 + 2 格连锁）；主爆炸范围内其他生物不触发；碰墙无直接目标则不触发
        if (directTarget != null && directTarget.isAlive()) {
            triggerExtraExplosion(w, owner, directTarget);
        }
        // 爆炸后停止移动，由 tick 等所有火环播完即消失
        exploding = true;
    }

    /** 爆炸视线检测：爆炸中心（略沿来向回退避免贴墙误判）到目标若被方块碰撞箱阻挡则不可达（爆炸不穿墙）。 */
    private boolean hasLineOfSight(ServerLevel w, double x, double y, double z, LivingEntity e) {
        Vec3 from = new Vec3(x, y, z).subtract(direction.scale(0.3));
        Vec3 to = new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
        BlockHitResult hit = w.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** 额外爆炸：被影响者腰部火环扩散 + 2 格内连锁伤害（不再二次连锁）；穿透段与爆炸段共用。 */
    private void triggerExtraExplosion(ServerLevel w, LivingEntity owner, LivingEntity center) {
        double cx = center.getX(), cy = center.getY() + center.getBbHeight() * 0.5, cz = center.getZ();
        activeRings.add(new float[]{(float) cx, (float) cy, (float) cz, 0f});   // 腰部火环，由 tick 逐帧播放
        AABB box = new AABB(cx - CHAIN_RADIUS, cy - CHAIN_RADIUS, cz - CHAIN_RADIUS,
                cx + CHAIN_RADIUS, cy + CHAIN_RADIUS, cz + CHAIN_RADIUS);
        List<LivingEntity> chained = w.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e != center && e.isAlive() && !e.isSpectator()
                        && e.distanceToSqr(cx, cy, cz) <= CHAIN_RADIUS * CHAIN_RADIUS
                        && !(owner instanceof ServerPlayer op && WhitelistUtils.isProtected(op, e)));
        for (LivingEntity e : chained) {
            e.hurt(physicalSource(e, owner), CHAIN_DAMAGE);
            applyFoxFireBurn(e);
        }
    }

    /** 火球命中附加狐火灼烧 5 秒（每秒掉血），并打上施法者归属 tag。 */
    private void applyFoxFireBurn(LivingEntity target) {
        target.addEffect(new MobEffectInstance(SscAddon.FOX_FIRE_BURN_ENTRY, 100, 0, false, true, true));
        if (this.getOwner() != null) {
            target.addTag("ssc_owner:" + this.getOwner().getUUID());
        }
    }

    /** 推进并绘制所有活跃火环（穿透/爆炸触发），播完移除。 */
    private void updateActiveRings(ServerLevel w) {
        java.util.Iterator<float[]> it = activeRings.iterator();
        while (it.hasNext()) {
            float[] r = it.next();
            r[3] += 1f;
            float progress = r[3] / (float) RING_DURATION;
            spawnWaistRing(w, r[0], r[1], r[2], progress);
            if (r[3] >= RING_DURATION) it.remove();
        }
    }

    /** 6 格球爆炸粒子：80% 红 dust + 20% 狐火，附加少量 lava 黑渣。 */
    private void spawnExplosionParticles(ServerLevel w, double x, double y, double z) {
        RandomSource rnd = this.random;
        DustParticleOptions red = new DustParticleOptions(new Vector3f(0.85f, 0.1f, 0.05f), 1.3f);
        for (int i = 0; i < 130; i++) {
            Vec3 p = randomInSphere(EXPLODE_RADIUS, rnd);
            double px = x + p.x, py = y + p.y, pz = z + p.z;
            if (rnd.nextDouble() < 0.8) {
                w.sendParticles(red, px, py, pz, 1, 0, 0, 0, 0);
            } else {
                w.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, 0, 0, 0.01);
            }
        }
        for (int i = 0; i < 16; i++) {
            Vec3 p = randomInSphere(EXPLODE_RADIUS * 0.6, rnd);
            w.sendParticles(ParticleTypes.LAVA, x + p.x, y + p.y + 1.0, z + p.z, 1, 0, 0, 0, 0);
        }
        // === RC4 奥术手雷爆炸特效（放大 1.5 倍版：扩散范围与 dust 尺寸 ×1.5，数量与速度不变） ===
        w.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.3, z, 3, 0.3, 0.3, 0.3, 1.0);
        w.sendParticles(ParticleTypes.LAVA, x, y + 0.8, z, 20, 0.45, 0.45, 0.45, 0.2);
        // 紫红→暗红渐变粉尘（dust 尺寸 1→1.5）
        DustColorTransitionOptions arcaneDust = new DustColorTransitionOptions(
                new Vector3f(0.322f, 0.0f, 0.149f), new Vector3f(0.149f, 0.012f, 0.039f), 1.5f);
        w.sendParticles(arcaneDust, x, y + 0.3, z, 300, 1.05, 1.8, 1.05, 0.01);
        w.sendParticles(ParticleTypes.FLAME, x, y + 0.3, z, 80, 0.75, 1.2, 0.75, 0.1);
        w.sendParticles(ParticleTypes.SQUID_INK, x, y + 0.3, z, 5, 0.45, 0.45, 0.45, 0.1);
        w.sendParticles(ParticleTypes.FALLING_LAVA, x, y + 0.1, z, 125, 1.5, 0.75, 1.5, 0.2);
    }

    /** 腰部火环扩散动画：每帧画半径递增的环（0→CHAIN_RADIUS），仅火焰 + 灵魂火粒子（无红色粉尘）。 */
    private void spawnWaistRing(ServerLevel w, double x, double y, double z, float progress) {
        double radius = Math.max(0.1, CHAIN_RADIUS * progress);
        int pts = Math.max(2, (int) ((12 + 26 * progress) * 0.2));  // 粒子量减至原 20%
        double rot = progress * 0.6;                                // 轻微旋转更灵动
        double upward = 0.02 + progress * 0.04;                     // 火苗向上飘
        for (int i = 0; i < pts; i++) {
            double a = 2 * Math.PI * i / pts + rot;
            double px = x + radius * Math.cos(a);
            double pz = z + radius * Math.sin(a);
            w.sendParticles(ParticleTypes.FLAME, px, y, pz, 1, 0, upward, 0, 0.0);
            if (i % 2 == 0) {
                w.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, y + 0.05, pz, 1, 0, upward, 0, 0.01);
            }
        }
    }

    private Vec3 randomInSphere(double r, RandomSource rnd) {
        double rr = r * Math.cbrt(rnd.nextDouble());
        double theta = rnd.nextDouble() * 2 * Math.PI;
        double phi = Math.acos(2 * rnd.nextDouble() - 1);
        double sinPhi = Math.sin(phi);
        return new Vec3(rr * sinPhi * Math.cos(theta), rr * Math.cos(phi), rr * sinPhi * Math.sin(theta));
    }

    private DamageSource magicSource(LivingEntity target, LivingEntity owner) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "magic"));
        return target.damageSources().source(key, owner, owner);
    }

    private DamageSource physicalSource(LivingEntity target, LivingEntity owner) {
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "mob_attack"));
        return target.damageSources().source(key, owner, owner);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity != this.getOwner() && entity instanceof LivingEntity;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("DirX")) {
            this.direction = new Vec3(nbt.getDouble("DirX"), nbt.getDouble("DirY"), nbt.getDouble("DirZ"));
        }
        this.distanceTraveled = nbt.getDouble("Dist");
        this.exploded = nbt.getBoolean("Exploded");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putDouble("DirX", direction.x);
        nbt.putDouble("DirY", direction.y);
        nbt.putDouble("DirZ", direction.z);
        nbt.putDouble("Dist", distanceTraveled);
        nbt.putBoolean("Exploded", exploded);
    }

    @Override
    public net.minecraft.world.item.ItemStack getItem() {
        return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIRE_CHARGE);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTrackerEntry) {
        return new ClientboundAddEntityPacket(this, entityTrackerEntry);
    }
}