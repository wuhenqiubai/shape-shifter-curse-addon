package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class GuaranteedCritEffect extends MobEffect {
	public GuaranteedCritEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xFF0000); // Red color
		// +25% speed for 3 seconds as well
		this.addAttributeModifier(
				Attributes.MOVEMENT_SPEED,
				ResourceLocation.parse("71077713-3984-4786-8800-478950587747"),
				0.25,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE
		);
	}
}