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
 * 霜降效果 - SP雪狐远程技能造成的debuff
 * 效果：
 * - 移动速度 -30%
 * - 持续时间：4秒
 */
public class FrostFallEffect extends MobEffect {

	private static final ResourceLocation SPEED_MODIFIER_UUID = ResourceLocation.parse("f2a3b4c5-d6e7-4890-bcde-f01234567891");
	private static final String SPEED_MODIFIER_NAME = "Frost Fall Speed Debuff";

	public FrostFallEffect() {
		super(MobEffectCategory.HARMFUL, 0xADD8E6); // Light blue color
	}

	@Override
	public void onEffectStarted(LivingEntity entity, int amplifier) {
		super.onEffectStarted(entity, amplifier);

		// Apply movement speed reduction (-30%)
		AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speedAttr != null) {
			speedAttr.removeModifier(SPEED_MODIFIER_UUID);
			speedAttr.addTransientModifier(new AttributeModifier(
					SPEED_MODIFIER_UUID,
					-0.3,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
			));
		}
	}

	@Override
	public void removeAttributeModifiers(AttributeMap attributes) {
		super.removeAttributeModifiers(attributes);

		// Remove movement speed modifier
		AttributeInstance speedAttr = attributes.getInstance(Attributes.MOVEMENT_SPEED);
		if (speedAttr != null) {
			speedAttr.removeModifier(SPEED_MODIFIER_UUID);
		}
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		// Apply update effect every 5 ticks for particles
		return duration % 5 == 0;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		// Spawn snowflake particles
		if (entity.level() instanceof ServerLevel serverWorld) {
			net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld,
					ParticleTypes.SNOWFLAKE,
					entity.getX(),
					entity.getY() + entity.getBbHeight() / 2.0,
					entity.getZ(),
					2,
					entity.getBbWidth() / 2.0,
					entity.getBbHeight() / 4.0,
					entity.getBbWidth() / 2.0,
					0.02
			);
		}
		return false;
	}
}