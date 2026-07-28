/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.ParasiticFruitSeedPower;
import org.joml.Vector3f;

/**
 * 寄生果蝠主技能「灵果寄生」的投掷物（抛物线，速度同孢子炸弹）。
 * 命中生物 → 调用 {@link ParasiticFruitSeedPower#plantSeedFrom} 在宿主身上种下灵果种子（保留原生根结果效果）。
 * 命中方块（落地）→ 阶段2 将生成地面种子圈；阶段1 暂仅落地粒子 + 音效。
 * 全部判定在服务端执行，确保多人环境主客机一致。
 */
public class ParasiticSeedProjectile extends ThrowableItemProjectile {
    /** 飞行尾迹：灵果绿色粒子 */
    private static final DustParticleOptions SEED_TRAIL = new DustParticleOptions(new Vector3f(0.35f, 0.95f, 0.30f), 1.0f);
    /** 落地种子圈寿命（tick，匹配主技能 duration 默认 240=12s） */
    private static final int DEFAULT_FIELD_LIFE = 240;
    /** 是否装备双生种荷（命中/落地时启用扩散：额外寄生 1 人，无人则叠 2 层）。 */
    private boolean twinPod = false;

    public void setTwinPod(boolean twinPod) {
        this.twinPod = twinPod;
    }

    public ParasiticSeedProjectile(EntityType<? extends ParasiticSeedProjectile> entityType, Level world) {
        super(entityType, world);
    }

    public ParasiticSeedProjectile(Level world, LivingEntity owner) {
        super(SscAddon.PARASITIC_SEED_ENTITY, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        // 视觉模型：小麦种子
        return Items.WHEAT_SEEDS;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        // 飞行尾迹：绿色灵果粒子（轻量，每 tick 2 个）
        if (this.level() instanceof ServerLevel sw) {
            sw.sendParticles(SEED_TRAIL, this.getX(), this.getY() + 0.1, this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level().isClientSide) return;
        if (this.isRemoved()) return;

        ServerPlayer caster = (this.getOwner() instanceof ServerPlayer sp) ? sp : null;

        if (hitResult.getType() == HitResult.Type.ENTITY
                && hitResult instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity host
                && host.isAlive() && caster != null) {
            // 命中生物：在宿主身上种下灵果种子（双生种荷时扩散额外 1 人/无人叠 2 层）
            ParasiticFruitSeedPower.plantSeedSpread(caster, host, twinPod);
        } else if (hitResult.getType() == HitResult.Type.BLOCK
                && this.level() instanceof ServerLevel sw && caster != null) {
            // 落地：生成灵果种子圈（绿色治疗环 + 核心图标，进圈生物触发；双生种荷时核心为双生种荷）
            net.onixary.shapeShifterCurseFabric.ssc_addon.ability.ParasiticSeedFieldManager
                    .spawnField(caster, sw, this.position(), DEFAULT_FIELD_LIFE, twinPod);
        }
        this.discard();
    }

    /** 通用碰撞预过滤：忽略发射者本人。 */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity != this.getOwner();
    }
}
