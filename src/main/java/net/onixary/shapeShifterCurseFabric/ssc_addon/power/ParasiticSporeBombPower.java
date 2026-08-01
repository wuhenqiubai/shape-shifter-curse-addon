/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.InfectionSporeBombEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

/**
 * 寄生果蝠次要技能：感染孢子炸弹。
 * 投出一颗西瓜种子样式的孢子炸弹，落地或撞击生物时无伤害爆炸，4 格内
 * 非白名单生物被施加感染孢子状态（参见 InfectionSporeManager）。
 */
public class ParasiticSporeBombPower extends ActiveCooldownPower {

    /** 投掷物初速度（与原版雪球速度相近） */
    private static final float PROJECTILE_SPEED = 1.4f;
    /** 散布抖动 */
    private static final float PROJECTILE_DIVERGENCE = 0.5f;
    private static final int ENERGY_COST = 1;

    private final int cooldownTicks;
    /** 内部冷却结束 tick：作为父类 use() 的双重保险，确保连按完全无效 */
    private long internalCooldownEndTime = 0L;

    public ParasiticSporeBombPower(PowerType<?> type, LivingEntity entity, int cooldownTicks,
                                   HudRender hudRender, Active.Key key) {
        super(type, entity, cooldownTicks, hudRender, e -> {
        });
        this.cooldownTicks = cooldownTicks;
        this.setKey(key);
    }

    public static PowerFactory<Power> createFactory() {
        return new PowerFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "parasitic_spore_bomb"),
                new SerializableData()
                        .add("cooldown", SerializableDataTypes.INT, 400)
                        .add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
                        .add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
                data ->
                        (type, player) -> new ParasiticSporeBombPower(
                                type,
                                player,
                                data.getInt("cooldown"),
                                data.get("hud_render"),
                                data.get("key")
                        )
        ).allowCondition();
    }

    @Override
    public boolean canUse() {
        return super.canUse() && entity.level().getGameTime() >= internalCooldownEndTime;
    }

    @Override
    public void onUse() {
        if (!(entity instanceof ServerPlayer caster)) return;
        if (caster.level().isClientSide) return;
        if (caster.hasEffect(SscAddon.PURIFIED_ENTRY)) return;
        // 双重保险：避免 Apoli 内部 use 状态异常时连按穿透
        if (entity.level().getGameTime() < internalCooldownEndTime) return;

        // 能量检查：不足则播放失败音效
        if (!PowerUtils.hasResource(caster, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, ENERGY_COST)) {
            caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.7f);
            return;
        }
        PowerUtils.changeResourceValueAndSync(caster, FormIdentifiers.BAT_PARASITIC_FRUIT_SEED_ENERGY, -ENERGY_COST);

        // 生成投掷物（贴图改为史莱姆球）
        InfectionSporeBombEntity bomb = new InfectionSporeBombEntity(caster.level(), caster);
        bomb.setItem(net.minecraft.world.item.Items.SLIME_BALL.getDefaultInstance());
        bomb.setOwner(caster);
        // 从眼部位置发射；速度向量与玩家视线一致
        bomb.setPosRaw(caster.getX(), caster.getEyeY() - 0.1, caster.getZ());
        bomb.shootFromRotation(caster, caster.getXRot(), caster.getYRot(), 0.0f, PROJECTILE_SPEED, PROJECTILE_DIVERGENCE);
        // 抵消投掷者的水平移动以保持初速一致
        Vec3 ownerVel = caster.getDeltaMovement();
        bomb.setDeltaMovement(bomb.getDeltaMovement().add(ownerVel.x, caster.onGround() ? 0.0 : ownerVel.y, ownerVel.z));
        caster.level().addFreshEntity(bomb);

        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 1.6f);

        // 启动内部冷却 + 父类 ActiveCooldownPower 计时（双重保险）
        internalCooldownEndTime = entity.level().getGameTime() + cooldownTicks;
        this.use();
        // 同步 CD 资源，供 cd_tick power 倒计与 HUD 显示
        PowerUtils.setResourceValueAndSync(caster, FormIdentifiers.SP_SECONDARY_CD, cooldownTicks);
    }
}