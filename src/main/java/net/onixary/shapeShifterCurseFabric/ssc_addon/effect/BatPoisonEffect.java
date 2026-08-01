/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 寄生果蝠次要技能专属「中毒」buff。
 * 效果与原版中毒一致：周期性扣血（amplifier 越高越频繁，最低保留 1 血不致死），
 * 图标/名字复用原版中毒。
 */
public class BatPoisonEffect extends MobEffect {
    public BatPoisonEffect() {
        super(MobEffectCategory.HARMFUL, 0x4E9331);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getHealth() > 1.0f) {
            entity.hurt(entity.damageSources().magic(), 1.0f);
        }
        return false;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int i = 25 >> amplifier;
        if (i > 0) {
            return duration % i == 0;
        }
        return true;
    }
}