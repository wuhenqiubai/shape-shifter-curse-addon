package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class StunEffect extends MobEffect {
	// 固定 UUID：暴露为常量，供 SscAddon 的「孤儿修正兜底清理」按 UUID 精确移除残留。
	public static final ResourceLocation SPEED_MODIFIER_UUID = ResourceLocation.parse("7107DE5E-7CE8-4030-940E-514C1F160890");
	public static final ResourceLocation ATTACK_MODIFIER_UUID = ResourceLocation.parse("22653B89-116E-49DC-9B6B-9971489B5C0A");

	public StunEffect() {
		super(MobEffectCategory.HARMFUL, 0x888888);
		this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
				ResourceLocation.parse(SPEED_MODIFIER_UUID.toString()), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		this.addAttributeModifier(Attributes.ATTACK_DAMAGE,
				ResourceLocation.parse(ATTACK_MODIFIER_UUID.toString()), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		// Removed explicit velocity reset to allow gravity/knockback to work
		// entity.setVelocity(0, entity.getVelocity().y, 0);
		// entity.velocityModified = true;
		return false;
	}
}