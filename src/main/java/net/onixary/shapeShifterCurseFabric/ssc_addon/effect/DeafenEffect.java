package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

// 失聪：客机 SoundManagerDeafenMixin 据此静音受影响玩家自身的所有声音
public class DeafenEffect extends MobEffect {
	public DeafenEffect() {
		super(MobEffectCategory.HARMFUL, 0x6B5B95);
	}
}
