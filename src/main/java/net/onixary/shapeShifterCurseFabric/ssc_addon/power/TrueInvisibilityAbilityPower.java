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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.TrinketUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.List;

public class TrueInvisibilityAbilityPower extends ActiveCooldownPower {

	private static final int COOLDOWN_TICKS = 240; // 12 seconds
	private final int effectDuration;
	// Internal cooldown tracking (separate from parent class)
	private long internalCooldownEndTime = 0;
	private int gracePeriodTicks = 0;
	private int lastAmplifier = 0;

	private boolean wasInvisible = false;
	private boolean wasUsingItem = false;
	private boolean wasHandSwinging = false;

	public TrueInvisibilityAbilityPower(PowerType<?> type, LivingEntity entity, int cooldownAfter, int effectDuration, HudRender hudRender, Active.Key key) {
		super(type, entity, cooldownAfter, hudRender, (e) -> {
		});
		this.effectDuration = effectDuration;
		this.setKey(key);
		this.setTicking(true);
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "true_invisibility"),
				new SerializableData()
						.add("cooldown", SerializableDataTypes.INT, COOLDOWN_TICKS)
						.add("duration", SerializableDataTypes.INT, 100)
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
						.add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
				data ->
						(type, player) -> new TrueInvisibilityAbilityPower(
								type,
								player,
								data.getInt("cooldown"),
								data.getInt("duration"),
								data.get("hud_render"),
								data.get("key")
						)
		).allowCondition();
	}

	private boolean hasInvisibilityCloak() {
		return TrinketUtils.isWearing(entity, SscAddon.INVISIBILITY_CLOAK);
	}

	public int getEffectDuration() {
		if (hasInvisibilityCloak()) {
			return this.effectDuration + 40; // Add 2 seconds (40 ticks)
		}
		return this.effectDuration;
	}

	@Override
	public void tick() {
		super.tick();

		if (entity == null || entity.level().isClientSide) return;

		if (entity.hasEffect(SscAddon.PURIFIED_ENTRY)) {
			if (entity.hasEffect(SscAddon.PRE_INVISIBILITY_ENTRY)) {
				entity.removeEffect(SscAddon.PRE_INVISIBILITY_ENTRY);
				entity.removeEffect(MobEffects.INVISIBILITY);
				applyUniversalCooldown();
			}
			if (entity.hasEffect(SscAddon.TRUE_INVISIBILITY_ENTRY)) {
				breakInvisibility(false);
			}
			wasInvisible = false;
			return;
		}

		boolean isInvisible = entity.hasEffect(SscAddon.TRUE_INVISIBILITY_ENTRY);
		boolean isPrecasting = entity.hasEffect(SscAddon.PRE_INVISIBILITY_ENTRY);

		// Natural End Detection (Time expired - not from action break or key cancel)
		if (wasInvisible && !isInvisible && !isPrecasting && lastAmplifier == 0) {
			applyUniversalCooldown();
			// Play glass break sound for natural expiration
			entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
		}

		if (!isInvisible) {
			wasUsingItem = false;
			wasHandSwinging = false;
			wasInvisible = false;
			gracePeriodTicks = 5; // Reset grace period when not invisible
			lastAmplifier = 0; // Reset amplifier tracking
			return;
		}

		// Track current amplifier
		MobEffectInstance currentEffect = entity.getEffect(SscAddon.TRUE_INVISIBILITY_ENTRY);
		if (currentEffect != null) {
			lastAmplifier = currentEffect.getAmplifier();
		}

		// Decrease grace period if > 0
		if (gracePeriodTicks > 0) {
			gracePeriodTicks--;
			// Update previous states to prevent immediate break after grace period
			wasUsingItem = entity.isUsingItem();
			wasHandSwinging = entity.swinging;
			wasInvisible = isInvisible;
			return;
		}

		// Particles while invisible
		if (entity.getRandom().nextFloat() < 0.07f) {
			ServerLevel serverWorld = (ServerLevel) entity.level();
			net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, net.minecraft.core.particles.ParticleTypes.SQUID_INK,
					entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
					1, 0.3, 0.5, 0.3, 0.05);
		}

		// Check for actions that break invisibility
		boolean shouldBreak = false;

		// 1. Using item
		boolean isUsingItem = entity.isUsingItem();
		if (isUsingItem && !wasUsingItem) shouldBreak = true;
		wasUsingItem = isUsingItem;

		// 2. Hand swinging
		boolean isHandSwinging = entity.swinging;
		if (isHandSwinging && !wasHandSwinging) shouldBreak = true;
		wasHandSwinging = isHandSwinging;

		if (shouldBreak) {
			breakInvisibility(false); // false = action break (glass break sound)
			return;
		}

		wasInvisible = isInvisible;
	}

	/**
	 * Check if internal cooldown is ready
	 * 使用服务端tick，保证多人一致性
	 */
	public boolean isInternalCooldownReady() {
		return entity.level().getGameTime() >= internalCooldownEndTime;
	}

	/**
	 * Apply cooldown to both this power and the dash power
	 */
	public void applyUniversalCooldown() {
		// Use real time for reliable cooldown
		int cooldownTicks = COOLDOWN_TICKS;
		if (hasInvisibilityCloak()) {
			cooldownTicks += 40; // Add 2 seconds to cooldown (from 12s to 14s)
		}
		internalCooldownEndTime = entity.level().getGameTime() + cooldownTicks; // tick-based, multiplayer-safe

		// 设置CD显示资源（主要和次要技能共享CD）
		if (entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			PowerUtils.setResourceValueAndSync(serverPlayer, FormIdentifiers.SP_PRIMARY_CD, cooldownTicks);
			PowerUtils.setResourceValueAndSync(serverPlayer, FormIdentifiers.SP_SECONDARY_CD, cooldownTicks);
		}

		// Also set dash ability cooldown
		List<TrueInvisibilityDashAbilityPower> dashPowers = PowerHolderComponent.getPowers(entity, TrueInvisibilityDashAbilityPower.class);
		for (TrueInvisibilityDashAbilityPower dashPower : dashPowers) {
			dashPower.applyInternalCooldown();
		}
	}

	/**
	 * Get remaining cooldown in seconds for display
	 */
	public int getRemainingCooldownSeconds() {
		long remaining = internalCooldownEndTime - entity.level().getGameTime();
		if (remaining <= 0) return 0;
		return (int) Math.ceil(remaining / 20.0); // 20 ticks per second
	}

	/**
	 * Breaks invisibility with appropriate sound effect
	 *
	 * @param byKey true if broken by pressing the key again (cat hiss), false if broken by action (glass break)
	 */
	public void breakInvisibility(boolean byKey) {
		if (entity == null || entity.level().isClientSide) return;

		if (!entity.hasEffect(SscAddon.TRUE_INVISIBILITY_ENTRY)) return;

		// Check amplifier before removing
		MobEffectInstance currentEffect = entity.getEffect(SscAddon.TRUE_INVISIBILITY_ENTRY);
		int currentAmp = (currentEffect != null) ? currentEffect.getAmplifier() : 0;

		// Remove invisibility effect
		entity.removeEffect(SscAddon.TRUE_INVISIBILITY_ENTRY);
		entity.removeEffect(MobEffects.INVISIBILITY);
		wasInvisible = false;

		ServerLevel serverWorld = (ServerLevel) entity.level();

		// player.sendMessage(Text.of("§c隐身被打破!"), true);
		if (byKey) {
			// Key Cancel: Cat Hiss
			serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.CAT_HISS, SoundSource.PLAYERS, 1.0f, 1.0f);

			// Add Buffs: Guaranteed Crit & Speed II for 5 seconds
			entity.addEffect(new MobEffectInstance(SscAddon.GUARANTEED_CRIT_ENTRY, 100, 0, false, false, true));
			entity.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 100, 1, false, false, true));

		} else {
			// Action Break: Glass Break
			serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
		}

		// Apply universal 12s cooldown ONLY when breaking invisibility AND it was the main ability (Amp 0)
		if (currentAmp == 0) {
			applyUniversalCooldown();
		}
	}

	@Override
	public boolean canUse() {
		// Always allow use - we handle cooldown logic in onUse
		return true;
	}

	@Override
	public void onUse() {
		if (entity == null || entity.level().isClientSide) return;

		boolean isInvisible = entity.hasEffect(SscAddon.TRUE_INVISIBILITY_ENTRY);
		boolean isPrecasting = entity.hasEffect(SscAddon.PRE_INVISIBILITY_ENTRY);

		if (isInvisible) {
			// Already invisible - pressing key again cancels with cat hiss
			breakInvisibility(true); // true = key break (cat hiss)
		} else if (isPrecasting) {
			// Currently casting - do nothing
		} else {
			// Not invisible - try to cast
			if (isInternalCooldownReady()) {
				// Apply pre-invisibility (casting phase)
				entity.addEffect(new MobEffectInstance(SscAddon.PRE_INVISIBILITY_ENTRY, 20, 0, false, false, true));
			}
		}
	}
}