package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.TrueInvisibilityAbilityPower;

import java.util.List;

public class PreInvisibilityEffect extends MobEffect {
	public PreInvisibilityEffect() {
		super(MobEffectCategory.BENEFICIAL, 0x101010); // Dark color
		this.addAttributeModifier(
				Attributes.MOVEMENT_SPEED,
				ResourceLocation.parse("12db6328-9844-4e20-9118-202758169972"),
				-0.5,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE
		);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return duration == 1;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (!entity.level().isClientSide()) {
			ServerLevel serverWorld = (ServerLevel) entity.level();

			// 1. Spawn Black Particles
			net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SQUID_INK,
					entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(),
					20, 0.5, 0.5, 0.5, 0.1);

			// 2. Play Extinguish Sound
			serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 1.0f);

			// 3. Find Power to get duration
			int duration = 100; // Default 5s
			List<TrueInvisibilityAbilityPower> powers = PowerHolderComponent.getPowers(entity, TrueInvisibilityAbilityPower.class);
			if (!powers.isEmpty()) {
				duration = powers.get(0).getEffectDuration();
			}

			// 4. Apply True Invisibility
			entity.addEffect(new MobEffectInstance(SscAddon.TRUE_INVISIBILITY_ENTRY, duration, 0, false, false, true));
			// 同步叠加原版隐身，确保服务器把“不可见”状态同步给其他客户端。
			entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false));

			// 5. Notify Player
			// 通知逻辑已移除，保留注释占位
		}
		return false;
	}
}