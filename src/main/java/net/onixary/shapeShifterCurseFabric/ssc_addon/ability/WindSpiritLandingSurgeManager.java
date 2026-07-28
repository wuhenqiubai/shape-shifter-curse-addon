package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风灵被动「落地风涌」。
 *
 * <p>玩家显著滞空后（fallDistance ≥ 1.5）落地时，若本次滞空期间<b>未攻击命中任何敌人</b>，
 * 触发一次落地冲击：3 格半径内造成 6 点物理伤害 + 击退。5 秒内置 CD。
 * 若滞空期间命中过敌人（徒手近战 / 疾风连爪 / 弹射物命中均算），则落地不触发（鼓励空中战斗）。
 *
 * <p>机制：
 * <ul>
 *   <li>{@code ServerLivingEntityEvents.ALLOW_DAMAGE}：风灵在空中造成伤害 → 标记 hitDuringAir</li>
 *   <li>服务端 tick：onGround 下降沿 + 上一 tick fallDistance ≥ 1.5 + CD 就绪 → 触发风涌</li>
 * </ul>
 * 判定全服务端，粒子广播，多人一致。
 */
public final class WindSpiritLandingSurgeManager {

    private static final double RADIUS = 3.0;
    private static final float DAMAGE = 6.0f;
    private static final int COOLDOWN_TICKS = 100;          // 5 秒
    private static final float MIN_FALL_DISTANCE = 1.5f;     // 显著滞空阈值

    /** 每玩家上一 tick 的 onGround 状态（用于检测下降沿）。 */
    private static final Map<UUID, Boolean> PREV_ON_GROUND = new ConcurrentHashMap<>();
    /** 每玩家上一 tick 的 fallDistance（落地瞬间 MC 会清零，故需记 prev）。 */
    private static final Map<UUID, Float> PREV_FALL_DISTANCE = new ConcurrentHashMap<>();
    /** 每玩家下次可触发风涌的服务端 tick（CD 时间戳）。 */
    private static final Map<UUID, Long> NEXT_AVAILABLE_TICK = new ConcurrentHashMap<>();
    /** 本次滞空期间是否攻击命中过敌人（true 则落地不触发）。 */
    private static final Map<UUID, Boolean> HIT_DURING_AIR = new ConcurrentHashMap<>();

    private static boolean registered = false;

    private WindSpiritLandingSurgeManager() {
    }

    /** 在 mod 初始化时调用一次：注册伤害事件监听（追踪空中命中）。 */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            handleDamageDealt(entity, source);
            return true; // 不拦截伤害
        });
    }

    /** 伤害结算时：若攻击方是风灵且在空中，标记本次滞空命中过。 */
    private static void handleDamageDealt(LivingEntity target, DamageSource source) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer player)) return;
        if (!FormUtils.isOcelotSP(player)) return;
        if (player.onGround()) return; // 不在空中则忽略
        // 在空中造成伤害 → 标记
        HIT_DURING_AIR.put(player.getUUID(), Boolean.TRUE);
    }

    /** 每服务端 tick 对每个在线玩家调用（由 SscAddon tick 循环驱动）。 */
    public static void tick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean curOnGround = player.onGround();
        Boolean prevOnGroundObj = PREV_ON_GROUND.get(uuid);
        float curFall = player.fallDistance;
        Float prevFallObj = PREV_FALL_DISTANCE.get(uuid);

        // 起跳瞬间（从地面到空中）：清除命中标记，开始新一轮滞空追踪
        if (prevOnGroundObj != null && prevOnGroundObj && !curOnGround) {
            HIT_DURING_AIR.remove(uuid);
        }

        // 落地下降沿：prev 在空中 → cur 在地面
        if (prevOnGroundObj != null && !prevOnGroundObj && curOnGround) {
            float prevFall = prevFallObj != null ? prevFallObj : 0f;
            if (prevFall >= MIN_FALL_DISTANCE) {
                tryTriggerSurge(player, uuid);
            }
            // 落地后清理本次滞空状态
            HIT_DURING_AIR.remove(uuid);
        }

        // 记录本 tick 状态供下一 tick 用
        PREV_ON_GROUND.put(uuid, curOnGround);
        PREV_FALL_DISTANCE.put(uuid, curFall);
    }

    /** 尝试触发落地风涌（含 CD / 空中命中检查）。 */
    private static void tryTriggerSurge(ServerPlayer player, UUID uuid) {
        // 非风灵不触发
        if (!FormUtils.isOcelotSP(player)) return;

        ServerLevel world = (ServerLevel) player.level();
        long now = world.getGameTime();

        // CD 检查
        Long next = NEXT_AVAILABLE_TICK.get(uuid);
        if (next != null && now < next) return;

        // 本次滞空期间命中过敌人 → 不触发（鼓励空中战斗）
        if (Boolean.TRUE.equals(HIT_DURING_AIR.get(uuid))) return;

        // 触发风涌：3 格半径 AOE
        Vec3 center = player.position();
        AABB box = new AABB(center.subtract(RADIUS, RADIUS, RADIUS), center.add(RADIUS, RADIUS, RADIUS));
        for (Entity e : world.getEntities(player, box)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (living == player) continue;
            if (WhitelistUtils.isProtected(player, living)) continue; // 默认白名单
            living.hurt(player.damageSources().playerAttack(player), DAMAGE);
            Vec3 push = living.position().subtract(center);
            if (push.lengthSqr() < 1.0e-4) push = new Vec3(0, 1, 0);
            push = push.normalize();
            living.knockback(0.5, -push.x, -push.z);
        }

        // 粒子：环形冲击波 + 向上扬尘
        world.sendParticles(ParticleTypes.POOF, center.x, center.y + 0.1, center.z,
                20, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.08);
        world.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.2, center.z,
                16, RADIUS * 0.6, 0.15, RADIUS * 0.6, 0.06);
        world.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y + 0.5, center.z,
                4, RADIUS * 0.3, 0.2, RADIUS * 0.3, 0.0);

        // 音效（全员可听）
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.6f, 1.3f);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4f, 1.5f);

        // 设置 CD
        NEXT_AVAILABLE_TICK.put(uuid, now + COOLDOWN_TICKS);
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PREV_ON_GROUND.remove(uuid);
        PREV_FALL_DISTANCE.remove(uuid);
        NEXT_AVAILABLE_TICK.remove(uuid);
        HIT_DURING_AIR.remove(uuid);
    }
}
