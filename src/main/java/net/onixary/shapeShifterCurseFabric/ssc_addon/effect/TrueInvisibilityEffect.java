package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class TrueInvisibilityEffect extends MobEffect {
	public TrueInvisibilityEffect() {
		super(MobEffectCategory.BENEFICIAL, 0x00FFFF); // Cyan color
		this.addAttributeModifier(
				Attributes.MOVEMENT_SPEED,
				ResourceLocation.parse("12db6328-9844-4e20-9118-202758169971"),
				0.25,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		);
	}
}