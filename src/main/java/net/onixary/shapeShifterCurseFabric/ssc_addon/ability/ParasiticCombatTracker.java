/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 寄生果蝠主技能「交战状态」追踪器。
 * <p>
 * 判定一个玩家是否处于交战：
 * - 造成伤害后 10s（200t）内；
 * - 受伤后 10s 内；
 * - 10s 后若「对其造成伤害的来源」仍存活且在其 10 格内，仍视为交战。
 * <p>
 * 服务端权威，仅追踪玩家；非玩家用简单判定（受击中或有攻击目标）。
 */
public final class ParasiticCombatTracker {
    /** 交战判定时长（tick）：10s */
    private static final int COMBAT_TICKS = 200;
    /** 来源在场判定半径平方（10 格） */
    private static final double SOURCE_RANGE_SQ = 10.0 * 10.0;

    private static final Map<UUID, CombatData> DATA = new ConcurrentHashMap<>();

    private ParasiticCombatTracker() {
    }

    private static final class CombatData {
        long lastDamageDealt = Long.MIN_VALUE;
        long lastHurt = Long.MIN_VALUE;
        UUID lastHurtSource;
    }

    public static void init() {
        // 用 ALLOW_DAMAGE（伤害结算前）记录交战时间戳，return true 不拦截伤害
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.level() instanceof ServerLevel) {
                long now = entity.level().getGameTime();
                // 受伤方是玩家 → 记录受伤时间与来源
                if (entity instanceof ServerPlayer victim) {
                    CombatData d = DATA.computeIfAbsent(victim.getUUID(), k -> new CombatData());
                    d.lastHurt = now;
                    Entity attacker = source.getEntity();
                    d.lastHurtSource = attacker != null ? attacker.getUUID() : null;
                }
                // 攻击方是玩家 → 记录造成伤害时间
                if (source.getEntity() instanceof ServerPlayer dealer) {
                    CombatData d = DATA.computeIfAbsent(dealer.getUUID(), k -> new CombatData());
                    d.lastDamageDealt = now;
                }
            }
            return true;
        });
    }

    /** 判定目标是否处于交战。非玩家用简单判定（受击中或有攻击目标）。 */
    public static boolean isInCombat(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return entity.hurtTime > 0 || (entity instanceof Mob mob && mob.getTarget() != null);
        }
        CombatData d = DATA.get(player.getUUID());
        if (d == null) return false;
        long now = player.level().getGameTime();
        if (now - d.lastDamageDealt < COMBAT_TICKS) return true;
        if (now - d.lastHurt < COMBAT_TICKS) return true;
        // 10s 后：对其造成伤害的来源仍存活且在 10 格内 → 仍交战
        if (d.lastHurtSource != null && player.level() instanceof ServerLevel sw) {
            Entity src = sw.getEntity(d.lastHurtSource);
            if (src != null && src.isAlive() && src.distanceToSqr(player) <= SOURCE_RANGE_SQ) {
                return true;
            }
        }
        return false;
    }
}
