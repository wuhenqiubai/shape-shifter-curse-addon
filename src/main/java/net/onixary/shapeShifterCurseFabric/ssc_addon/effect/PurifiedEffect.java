package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.InfectionSporeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 净化标记效果 - SP悦灵净化技能施加的短暂标记
 * 用于通知Java端技能（如冰霜风暴蓄力、闪现攻击）被打断
 * 效果本身无任何属性修改，仅作为信号使用
 * 持续时间极短（1秒），仅用于在下一个tick被Java代码检测到
 */
public class PurifiedEffect extends MobEffect {

	public PurifiedEffect() {
		super(MobEffectCategory.NEUTRAL, 0x99DDFF); // Light blue color
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (entity.level().isClientSide) return false;

		// Clear all harmful effects (or all effects except this one)
		// Since we are iterating while modifying, we need a copy
		List<MobEffectInstance> effects = new ArrayList<>(entity.getActiveEffects());

		for (MobEffectInstance instance : effects) {
			// Don't remove self
			if (instance.getEffect().value() == this) continue;

			// Remove the effect
			// Note: removeStatusEffect returns boolean, doesn't throw concurrent modification if we iterate over a copy
			entity.removeEffect(instance.getEffect());
		}

		// 净化同样清除“感染孢子”（由自定义管理器维护，非原版状态效果），并驱散身边的滞留毒雾云
		if (entity.level() instanceof ServerLevel sw) {
			InfectionSporeManager.cureInfection(entity.getUUID());
			InfectionSporeManager.dissipateCloudsNear(sw, entity.position(), InfectionSporeManager.CLOUD_PURIFY_REACH);
		}
		return false;
	}
}