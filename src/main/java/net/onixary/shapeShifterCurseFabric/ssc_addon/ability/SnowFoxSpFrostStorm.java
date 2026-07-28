package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostStormEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP雪狐远程次要技能 - 冰风暴
 * 1.5秒蓄力后在准星位置释放冰风暴
 * 蓄力期间减少50%准星移动速度
 */
public class SnowFoxSpFrostStorm {

    private SnowFoxSpFrostStorm() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }
    
    private static final ConcurrentHashMap<UUID, ChargingData> CHARGING_PLAYERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWN_PLAYERS = new ConcurrentHashMap<>(); // 自定义CD跟踪
    
    private static final int CHARGE_TICKS = 30; // 1.5秒蓄力
    private static final double MAX_RANGE = 30.0; // 最大释放距离
    private static final int MANA_COST = 30; // 霜寒值消耗
    //未使用: private static final int COOLDOWN = 600;  30秒CD = 600tick
    
    private static final ResourceLocation RESOURCE_ID = ResourceLocation.fromNamespaceAndPath("my_addon", "form_snow_fox_sp_resource");
    private static final ResourceLocation REGEN_COOLDOWN_ID = ResourceLocation.fromNamespaceAndPath("my_addon", "form_snow_fox_sp_frost_regen_cooldown_resource");
    
    /**
     * 开始蓄力（点按技能键时调用）
     */
    public static boolean startCharging(ServerPlayer player) {
        // 检查是否已经在蓄力
        if (CHARGING_PLAYERS.containsKey(player.getUUID())) {
            return false;
        }
        
        // 检查自定义CD是否结束（使用服务端tick，多人环境一致）
        long currentTick = player.level().getGameTime();
        Long cdEndTick = COOLDOWN_PLAYERS.get(player.getUUID());
        if (cdEndTick != null && currentTick < cdEndTick) {
            return false;
        }
        
        // 检查霜寒值
        int currentMana = getResourceValue(player);
        if (currentMana < MANA_COST) {
            player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.0f);
            return false;
        }
        
        // 消耗霜寒值（在蓄力开始时就消耗）
        changeResourceValue(player, -MANA_COST);
        // 设置回复冷却（5秒）
        setRegenCooldown(player, 100);
        // 设置技能CD（30秒 = 600tick，使用服务端tick保证多人一致性）
        COOLDOWN_PLAYERS.put(player.getUUID(), currentTick + 600L);
        // 设置CD显示资源（30秒 = 600tick）
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RANGED_SECONDARY_CD, 600);
        
        // 开始蓄力
        CHARGING_PLAYERS.put(player.getUUID(), new ChargingData(0));
        
        // 播放蓄力开始音效
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.5f);
        
        return true;
    }
    
    /**
     * 取消蓄力（被净化时调用）
     */
    public static void cancelCharging(ServerPlayer player) {
        if (CHARGING_PLAYERS.remove(player.getUUID()) != null) {
            // 播放打断音效
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.5f);
        }
    }

    /**
     * 玩家断线时清理所有状态，防止内存泄漏
     */
    public static void clearPlayer(java.util.UUID uuid) {
        CHARGING_PLAYERS.remove(uuid);
        COOLDOWN_PLAYERS.remove(uuid);
    }

    /**
     * 清除所有玩家的蓄力 / CD 状态
     * 用于服务器启动 / 数据包重载，避免长生命周期 JVM 中残留过期数据
     */
    public static void clearAll() {
        CHARGING_PLAYERS.clear();
        COOLDOWN_PLAYERS.clear();
    }
    
    /**
     * 每tick更新蓄力状态
     */
    public static void tick(ServerPlayer player) {
        ChargingData data = CHARGING_PLAYERS.get(player.getUUID());
        if (data == null) return;
        
        // 检查是否被净化 - 如果有purified效果则取消蓄力
        if (player.hasEffect(SscAddon.PURIFIED_ENTRY)) {
            cancelCharging(player);
            return;
        }
        
        data.chargeTicks++;
        
        // 生成蓄力粒子效果
        if (player.level() instanceof ServerLevel serverWorld) {
            Vec3 pos = player.position();
            double angle = (data.chargeTicks * 0.3) % (Math.PI * 2);
            double radius = 0.8;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            // 充能态每 tick 单发：只广播 64 格（原 512 格 ParticleUtils.spawnParticles），减少远处玩家高频网络包；充能主体特效不受影响
            ParticleUtils.spawnParticlesNearby(serverWorld, ParticleTypes.SNOWFLAKE, x, pos.y + 1, z, 1, 0, 0.1, 0, 0);
        }
        
        // 蓄力完成
        if (data.chargeTicks >= CHARGE_TICKS) {
            releaseStorm(player);
            CHARGING_PLAYERS.remove(player.getUUID());
        }
    }
    
    /**
     * 释放冰风暴
     */
    private static void releaseStorm(ServerPlayer player) {
        // 霜寒值已在startCharging时消耗，CD也已设置

        // 计算准星位置（射线检测）
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = start.add(look.scale(MAX_RANGE));
        
        BlockHitResult hitResult = player.level().clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
        
        Vec3 targetPos;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            targetPos = hitResult.getLocation();
        } else {
            targetPos = end;
        }
        
        // 创建冰风暴实体
        FrostStormEntity storm = new FrostStormEntity(
            player.level(),
            targetPos.x, targetPos.y, targetPos.z,
            player
        );
        player.level().addFreshEntity(storm);
        
        // 播放释放音效
        player.level().playSound(null, targetPos.x, targetPos.y, targetPos.z,
            SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0f, 0.8f);
        
        // 生成释放粒子
        if (player.level() instanceof ServerLevel serverWorld) {
            ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CLOUD,
                targetPos.x, targetPos.y + 1, targetPos.z,
                30, 1.5, 1.0, 1.5, 0.05);
        }
    }

    /**
     * 获取霜寒值
     */
    private static int getResourceValue(ServerPlayer player) {
        return PowerUtils.getResourceValue(player, RESOURCE_ID);
    }

    /**
     * 修改霜寒值
     */
    private static void changeResourceValue(ServerPlayer player, int change) {
        PowerUtils.changeResourceValueAndSync(player, RESOURCE_ID, change);
    }

    /**
     * 设置回复冷却（使用后5秒内无法自然回复霜寒值）
     */
    private static void setRegenCooldown(ServerPlayer player, int value) {
        PowerUtils.setResourceValueAndSync(player, REGEN_COOLDOWN_ID, value);
    }

    /**
     * 蓄力数据
     */
    private static class ChargingData {
        int chargeTicks;

        ChargingData(int chargeTicks) {
            this.chargeTicks = chargeTicks;
        }
    }
}