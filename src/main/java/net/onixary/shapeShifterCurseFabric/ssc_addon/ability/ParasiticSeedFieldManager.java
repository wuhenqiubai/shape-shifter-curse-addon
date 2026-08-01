/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.ParasiticFruitSeedPower;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 寄生果蝠主技能「灵果寄生」落地种子圈管理器。
 * <p>
 * 投掷物未命中生物、落地时生成一个「灵果种子圈」：地面绿色混凝土核心（block_marker 粒子，不改世界方块）
 * + 绿色治疗环粒子（学 RC4 healing_crystal 并绿色化）；半径 1 内任意玩家走入即拾取，
 * 获得寄生效果（时长 = 最大时长 − 已落地时长，由施法者持有的 power 施加，友/敌果实自动判定）；
 * 寿命耗尽或被拾取后消失，消失时喷绿色粒子。
 * <p>
 * 服务端权威，所有粒子由服务端 spawnParticles 广播，保证多人主客机一致。
 */
public final class ParasiticSeedFieldManager {
    /** 拾取 / 治疗环半径（格） */
    public static final double FIELD_RADIUS = 1.0;
    /** 治疗环绿色粉尘 */
    private static final DustParticleOptions RING_DUST = new DustParticleOptions(new Vector3f(0.30f, 0.85f, 0.30f), 1.0f);

    private static final CopyOnWriteArrayList<SeedField> FIELDS = new CopyOnWriteArrayList<>();

    private ParasiticSeedFieldManager() {
    }

    private static final class SeedField {
        final UUID casterUuid;
        final ResourceKey<Level> worldKey;
        final Vec3 pos;
        final long spawnTick;
        final long endTick;
        final UUID standUuid;
        final boolean twinPod;
        float ringProgress;

        SeedField(UUID casterUuid, ResourceKey<Level> worldKey, Vec3 pos, long spawnTick, long endTick, UUID standUuid, boolean twinPod) {
            this.casterUuid = casterUuid;
            this.worldKey = worldKey;
            this.pos = pos;
            this.spawnTick = spawnTick;
            this.endTick = endTick;
            this.standUuid = standUuid;
            this.twinPod = twinPod;
        }
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(ParasiticSeedFieldManager::onWorldTick);
    }

    /** 投掷物落地时调用：在落点生成一个灵果种子圈，寿命 = lifeTicks。双生种荷时核心图标为双生种荷。 */
    public static void spawnField(ServerPlayer caster, ServerLevel world, Vec3 pos, int lifeTicks, boolean twinPod) {
        long now = world.getGameTime();
        // 悬浮的核心图标（small armor_stand 头戴，仿 RC4 healing_crystal）：双生种荷时为双生种荷，否则火把花种子
        ArmorStand stand = new ArmorStand(world, pos.x, pos.y, pos.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        CompoundTag nbt = new CompoundTag();
        stand.addAdditionalSaveData(nbt);
        nbt.putBoolean("Small", true);
        nbt.putBoolean("Marker", true);
        nbt.putBoolean("NoBasePlate", true);
        stand.readAdditionalSaveData(nbt);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(twinPod
                ? net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.TWIN_POD
                : Items.TORCHFLOWER_SEEDS));
        world.addFreshEntity(stand);

        FIELDS.add(new SeedField(caster.getUUID(), world.dimension(), pos, now, now + Math.max(20, lifeTicks), stand.getUUID(), twinPod));
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GRASS_PLACE, SoundSource.PLAYERS, 0.8f, 1.2f);
        world.sendParticles(ParticleTypes.WARPED_SPORE, pos.x, pos.y + 0.2, pos.z, 30, 0.5, 0.3, 0.5, 0.02);
    }

    private static void onWorldTick(ServerLevel world) {
        if (FIELDS.isEmpty()) return;
        long now = world.getGameTime();
        MinecraftServer server = world.getServer();
        for (SeedField f : FIELDS) {
            if (!f.worldKey.equals(world.dimension())) continue;

            // 到期消失 + 绿色粒子
            if (now >= f.endTick) {
                killStand(world, f);
                spawnDisappearParticles(world, f.pos);
                FIELDS.remove(f);
                continue;
            }

            // 视觉：绿色混凝土核心 + 旋转绿色治疗环
            drawField(world, f, now);

            // 半径 1 内任意存活生物触发（玩家+怪物+动物，友/敌果由白名单判定）；排除种子圈 armor_stand。
            // 允许施法者本人触发（客机可吃自己种子），但需种子落地 ≥10t 后，避免刚扔出被自己立即踩发
            ServerPlayer caster = server.getPlayerList().getPlayer(f.casterUuid);
            if (caster == null) continue;   // 施法者离线则保留种子圈，等其上线或自然到期
            boolean casterArmed = now - f.spawnTick >= 10;
            AABB box = new AABB(f.pos.subtract(FIELD_RADIUS, FIELD_RADIUS, FIELD_RADIUS),
                    f.pos.add(FIELD_RADIUS, FIELD_RADIUS, FIELD_RADIUS));
            double sqRadius = FIELD_RADIUS * FIELD_RADIUS;
            List<LivingEntity> nearby = world.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && !e.getUUID().equals(f.standUuid) && !e.isSpectator()
                            && (casterArmed || e != caster)
                            && e.distanceToSqr(f.pos) <= sqRadius);
            if (!nearby.isEmpty()) {
                for (LivingEntity picker : nearby) {
                    // 触发后按生物交战状态定寿命（非交战 15s / 交战 5s，同命中）；双生种荷时扩散额外 1 人/无人叠 2 层
                    ParasiticFruitSeedPower.plantSeedSpread(caster, picker, f.twinPod);
                }
                spawnPickupParticles(world, f.pos);
                killStand(world, f);
                FIELDS.remove(f);
            }
        }
    }

    /** 绿色混凝土核心 + 旋转绿色治疗环（学 RC4 healing_crystal 并绿色化，半径 1）。 */
    private static void drawField(ServerLevel world, SeedField f, long now) {
        double x = f.pos.x, y = f.pos.y, z = f.pos.z;
        // 悬浮方块缓慢自转（仿 RC4 healing_crystal）
        Entity stand = world.getEntity(f.standUuid);
        if (stand != null) {
            stand.setYRot(stand.getYRot() + 5.0f);
        }
        // 旋转治疗环（半径 1 的 8 点圈）
        f.ringProgress += 0.12f;
        double rot = f.ringProgress;
        for (int i = 0; i < 8; i++) {
            double a = 2 * Math.PI * i / 8 + rot;
            double px = x + FIELD_RADIUS * Math.cos(a);
            double pz = z + FIELD_RADIUS * Math.sin(a);
            world.sendParticles(RING_DUST, px, y + 0.15, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // 周期绿星 + 青绿孢子上飘
        if (now % 6 == 0) {
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.8, z, 1, 0.25, 0.4, 0.25, 0.0);
        }
        world.sendParticles(ParticleTypes.WARPED_SPORE, x, y + 0.6, z, 2, 0.35, 0.3, 0.35, 0.01);
    }

    private static void spawnPickupParticles(ServerLevel world, Vec3 pos) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 0.9f, 1.3f);
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.3, pos.z, 18, 0.4, 0.5, 0.4, 0.0);
        world.sendParticles(ParticleTypes.WARPED_SPORE, pos.x, pos.y + 0.3, pos.z, 20, 0.5, 0.4, 0.5, 0.02);
    }

    private static void spawnDisappearParticles(ServerLevel world, Vec3 pos) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.6f, 0.9f);
        world.sendParticles(ParticleTypes.WARPED_SPORE, pos.x, pos.y + 0.2, pos.z, 24, 0.5, 0.3, 0.5, 0.03);
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.2, pos.z, 10, 0.4, 0.3, 0.4, 0.0);
    }

    /** 移除种子圈的悬浮方块 armor_stand。 */
    private static void killStand(ServerLevel world, SeedField f) {
        Entity stand = world.getEntity(f.standUuid);
        if (stand != null) stand.discard();
    }
}
