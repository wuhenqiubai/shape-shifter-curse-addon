package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 潮汐波动吸附减速效果 - 移动速度 -15%（代码减速，非药水缓慢）。
 * 每次被潮汐粒子球吸附施加 1.5 秒（30 tick），重复施加刷新时间。
 * 仅作用于移动速度，不显示药水图标（ambient=false, particles=false 由调用方控制）。
 */
public class TidalSlowEffect extends MobEffect {

    private static final ResourceLocation SPEED_MODIFIER_UUID = ResourceLocation.parse("7c1a2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final String SPEED_MODIFIER_NAME = "Tidal Slow";

    public TidalSlowEffect() {
        super(MobEffectCategory.HARMFUL, 0x33CCFF); // 青蓝色
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        super.onEffectStarted(entity, amplifier);
        AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
            // 15% 减速：MULTIPLY_TOTAL -0.15
            speedAttr.addTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_UUID,
		            -0.15,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
    }

    @Override
    public void removeAttributeModifiers(AttributeMap attributes) {
        super.removeAttributeModifiers(attributes);
        AttributeInstance speedAttr = attributes.getInstance(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // 偶发水滴粒子提示（与脚底青蓝粒子互补）
        if (entity.level() instanceof ServerLevel sw) {
            net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(sw,
                    ParticleTypes.DRIPPING_WATER,
                    entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(),
                    1, entity.getBbWidth() / 2.0, entity.getBbHeight() / 4.0, entity.getBbWidth() / 2.0, 0.0);
        }
        return false;
    }
}