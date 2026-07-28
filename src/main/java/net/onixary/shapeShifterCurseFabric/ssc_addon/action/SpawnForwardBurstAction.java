package net.onixary.shapeShifterCurseFabric.ssc_addon.action;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 向前喷射粒子 action（附属专用）。
 * 沿实体视线方向，从眼前向前一段距离内喷射粒子，每个粒子带"向前飞"的初速度，
 * 实现吐火/吐息那种向前喷涌的动力感（普通 particle 命令的 delta 是固定世界向量、
 * 无法跟随朝向，所以用 Java 取 lookVec 计算速度）。
 * 仅 red 吐火使用，不动共享的 my_addon:fire_breath，因此不影响 sp。
 */
public class SpawnForwardBurstAction {

    public static void action(SerializableData.Instance data, Entity entity) {
        if (!(entity.level() instanceof ServerLevel serverWorld)) return;
        if (!(entity instanceof LivingEntity living)) return;

        ParticleOptions particleEffect = data.get("particle");
        boolean force = data.getBoolean("force");
        int count = Math.max(1, data.getInt("count"));
        double distance = data.getDouble("distance");
        double forwardSpeed = data.getDouble("forward_speed");
        double sideSpread = data.getDouble("side_spread");
        double verticalOffset = data.getDouble("vertical_offset");

        Vec3 look = living.getViewVector(1.0F);
        Vec3 start = living.getEyePosition().add(0, verticalOffset, 0);
        RandomSource r = serverWorld.getRandom();

        for (int i = 0; i < count; i++) {
            // 沿前方随机距离取生成位置 + 少量侧向散布
            double dist = r.nextDouble() * distance;
            Vec3 pos = start.add(look.scale(dist))
                    .add((r.nextDouble() - 0.5) * sideSpread,
                         (r.nextDouble() - 0.5) * sideSpread,
                         (r.nextDouble() - 0.5) * sideSpread);
            // 速度 = 朝向 * 向前速度 + 少量随机扰动，形成喷涌锥
            double vx = look.x * forwardSpeed + (r.nextDouble() - 0.5) * 0.15;
            double vy = look.y * forwardSpeed + (r.nextDouble() - 0.5) * 0.15;
            double vz = look.z * forwardSpeed + (r.nextDouble() - 0.5) * 0.15;
            for (ServerPlayer player : serverWorld.players()) {
                // count=0 时 (vx,vy,vz) 作为粒子速度方向，最后的 1.0 为速度倍率
                serverWorld.sendParticles(player, particleEffect, force,
                        pos.x, pos.y, pos.z, 0, vx, vy, vz, 1.0);
            }
        }
    }

    public static ActionFactory<Entity> getFactory() {
        return new ActionFactory<>(
                ResourceLocation.fromNamespaceAndPath("my_addon", "spawn_forward_burst"),
                new SerializableData()
                        .add("particle", SerializableDataTypes.PARTICLE_EFFECT_OR_TYPE)
                        .add("count", SerializableDataTypes.INT, 20)
                        .add("force", SerializableDataTypes.BOOLEAN, false)
                        .add("distance", SerializableDataTypes.DOUBLE, 4.0D)
                        .add("forward_speed", SerializableDataTypes.DOUBLE, 0.5D)
                        .add("side_spread", SerializableDataTypes.DOUBLE, 0.4D)
                        .add("vertical_offset", SerializableDataTypes.DOUBLE, -0.2D),
                SpawnForwardBurstAction::action
        );
    }
}