package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.util.Identifier;

public class StunEffect extends StatusEffect {
	// 固定 UUID：暴露为常量，供 SscAddon 的「孤儿修正兜底清理」按 UUID 精确移除残留。
	public static final Identifier SPEED_MODIFIER_UUID = Identifier.of("7107de5e-7ce8-4030-940e-514c1f160890");
	public static final Identifier ATTACK_MODIFIER_UUID = Identifier.of("22653b89-116e-49dc-9b6b-9971489b5c0a");

	public StunEffect() {
		super(StatusEffectCategory.HARMFUL, 0x888888);
		this.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
				Identifier.of(SPEED_MODIFIER_UUID.toString()), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		this.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE,
				Identifier.of(ATTACK_MODIFIER_UUID.toString()), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		// Removed explicit velocity reset to allow gravity/knockback to work
		// entity.setVelocity(0, entity.getVelocity().y, 0);
		// entity.velocityModified = true;
		return false;
	}
}