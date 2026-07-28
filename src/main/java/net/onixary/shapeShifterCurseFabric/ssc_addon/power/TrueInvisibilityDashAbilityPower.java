package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.List;

public class TrueInvisibilityDashAbilityPower extends ActiveCooldownPower {

	private static final int COOLDOWN_TICKS = 240; // 12 seconds
	private static final int STUN_DELAY_TICKS = 20; // 1 second
	private int ticksSinceDash = 0;
	private boolean isWaitingForStun = false;
	// Internal cooldown tracking (same as TrueInvisibilityAbilityPower)
	private long internalCooldownEndTime = 0;

	public TrueInvisibilityDashAbilityPower(PowerType<?> type, LivingEntity entity, int cooldownAfter, HudRender hudRender, Active.Key key) {
		super(type, entity, cooldownAfter, hudRender, (e) -> {
		});
		this.setKey(key);
		this.setTicking(true);
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "true_invisibility_dash"),
				new SerializableData()
						.add("cooldown", SerializableDataTypes.INT, COOLDOWN_TICKS)
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
						.add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
				data ->
						(type, player) -> new TrueInvisibilityDashAbilityPower(
								type,
								player,
								data.getInt("cooldown"),
								data.get("hud_render"),
								data.get("key")
						)
		).allowCondition();
	}

	/**
	 * Check if internal cooldown is ready
	 * 使用服务端tick，保证多人一致性
	 */
	public boolean isInternalCooldownReady() {
		return entity.level().getGameTime() >= internalCooldownEndTime;
	}

	/**
	 * Apply internal cooldown (called from TrueInvisibilityAbilityPower)
	 */
	public void applyInternalCooldown() {
		internalCooldownEndTime = entity.level().getGameTime() + COOLDOWN_TICKS;
	}

	/**
	 * Get remaining cooldown in seconds for display
	 */
	public int getRemainingCooldownSeconds() {
		long remaining = internalCooldownEndTime - entity.level().getGameTime();
		if (remaining <= 0) return 0;
		return (int) Math.ceil(remaining / 20.0);
	}

	@Override
	public boolean canUse() {
		// Can only use when invisible
		return entity.hasEffect(SscAddon.TRUE_INVISIBILITY_ENTRY);
	}

	@Override
	public void onUse() {
		if (entity == null || entity.level().isClientSide) return;

		// Double check: MUST be invisible to use this
		// Note: Apoli might call onUse without checking canUse() in some network contexts
		if (!this.canUse()) {
			return;
		}

		// Remove invisibility immediately
		entity.removeEffect(SscAddon.TRUE_INVISIBILITY_ENTRY);
		entity.removeEffect(MobEffects.INVISIBILITY);

		// Apply 50% slow (Slowness III = -45%, close enough) for 1 second
		entity.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false, false));

		// Play Cat Hiss Sound (周围人能听见)
		entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.CAT_HISS, SoundSource.PLAYERS, 1.0f, 1.0f);

		// Dash forward 5 blocks
		float yaw = entity.getYRot();
		float f = -net.minecraft.util.Mth.sin(yaw * 0.017453292F);
		float g = net.minecraft.util.Mth.cos(yaw * 0.017453292F);
		entity.push(f * 1.5, 0.5, g * 1.5);
		entity.hurtMarked = true;

		// Start waiting for 1 second to apply stun
		isWaitingForStun = true;
		ticksSinceDash = 0;

		// Set both cooldowns to 12 seconds
		applyInternalCooldown();

		// Also set the main ability cooldown
		List<TrueInvisibilityAbilityPower> mainPowers = PowerHolderComponent.getPowers(entity, TrueInvisibilityAbilityPower.class);
		for (TrueInvisibilityAbilityPower mainPower : mainPowers) {
			mainPower.applyUniversalCooldown();
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (entity == null || entity.level().isClientSide) return;

		if (isWaitingForStun) {
			ticksSinceDash++;

			// Apply stun exactly 1 second (20 ticks) after dash
			if (ticksSinceDash >= STUN_DELAY_TICKS) {
				performStunEffect();
			}
		}
	}

	private void performStunEffect() {
		ServerLevel world = (ServerLevel) entity.level();

		// Apply stun to nearby entities (5 blocks radius for 10 blocks diameter sphere)
		net.minecraft.world.phys.AABB box = entity.getBoundingBox().inflate(5.0, 5.0, 5.0);
		world.getEntitiesOfClass(LivingEntity.class, box, (e) -> {
					// Filter Logic:
					// 1. Not self
					if (e == entity) return false;

					// 2. Not other "Wild Cats" - check if they have the TrueInvisibility power
					// This includes all wild cats, whether visible or not
					if (!PowerHolderComponent.getPowers(e, TrueInvisibilityAbilityPower.class).isEmpty()) return false;
					if (!PowerHolderComponent.getPowers(e, TrueInvisibilityDashAbilityPower.class).isEmpty()) return false;

					return e.distanceTo(entity) <= 5.0;
				})
				.forEach(target -> {
					// Whitelist check
					if (entity instanceof ServerPlayer sPlayer && WhitelistUtils.isProtected(sPlayer, target))
						return;
					// Apply Stun: 1.5s = 30 ticks
					target.addEffect(new MobEffectInstance(SscAddon.STUN_ENTRY, 30, 0, false, false, true));
				});

		// Particle effect
		net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(world, net.minecraft.core.particles.ParticleTypes.POOF,
				entity.getX(), entity.getY(), entity.getZ(), 15, 0.8, 0.2, 0.8, 0.1);
		net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(world, net.minecraft.core.particles.ParticleTypes.CLOUD,
				entity.getX(), entity.getY(), entity.getZ(), 10, 0.5, 0.1, 0.5, 0.05);

		// Play additional sound when stun triggers
		world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5f, 1.5f);


		isWaitingForStun = false;
		ticksSinceDash = 0;
	}
}