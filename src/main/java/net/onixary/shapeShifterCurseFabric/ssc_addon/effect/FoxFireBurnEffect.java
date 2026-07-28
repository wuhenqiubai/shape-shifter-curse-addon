package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SscIgnitedEntityAccessor;

import java.util.UUID;

public class FoxFireBurnEffect extends MobEffect {
	public FoxFireBurnEffect() {
		super(MobEffectCategory.HARMFUL, 0x3366FF); // Blue color
	}

	private Player getOwnerFromTags(LivingEntity entity) {
		for (String tag : entity.getTags()) {
			if (tag.startsWith("ssc_owner:")) {
				try {
					String uuidStr = tag.substring("ssc_owner:".length());
					UUID uuid = UUID.fromString(uuidStr);
					return entity.level().getPlayerByUUID(uuid);
				} catch (Exception e) {
					// Ignore invalid tags
				}
			}
		}
		return null;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (entity.hasEffect(SscAddon.PURIFIED_ENTRY)) {
			entity.removeEffect(SscAddon.FOX_FIRE_BURN_ENTRY);
			return false;
		}

		if (entity.level().isClientSide) {
			// Client side particles can be handled here or via separate client tick handler
			// But applyUpdateEffect runs on both usually if registered correctly.
			// For simple visuals, we might rely on server spawning particles or client side implementation.
			// StatusEffect particles are usually handled by the game automatically if color is set,
			// but we want Soul Fire particles.
			entity.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, true,
					entity.getX() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth(),
					entity.getY() + entity.getRandom().nextDouble() * entity.getBbHeight(),
					entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * entity.getBbWidth(),
					0, 0, 0);
		} else {
			// Server side logic
			// Damage every second (20 ticks) like vanilla fire
			if (entity.tickCount % 20 == 0) {
				DamageSource source = entity.damageSources().inFire();

				Player owner = null;
				if (entity instanceof SscIgnitedEntityAccessor accessor) {
					UUID igniterUuid = accessor.sscAddon$getIgniterUuid();
					if (igniterUuid != null) {
						owner = entity.level().getPlayerByUUID(igniterUuid);
					}
				}

				// Priority 1: Explicit owner from NBT tag or Accessor
				if (owner == null) {
					owner = getOwnerFromTags(entity);
				}

				if (owner != null) {
					// We use onFire damage source logic but with player attribution
					// Vanilla doesn't have a direct "fire from player" source helper that's standard for DoT
					// So we construct a generic override or simulate player attack but keeping it fire related if possible
					// Or just use playerAttack for simplicity to ensure drops.
					// A better way is creating a source that is "onFire" but has an attacker.
					// But getDamageSources().inFire() doesn't take attacker.
					// We can use create(DamageTypes.IN_FIRE, null, owner) if we want.
					// But typically 'playerAttack' is safest for credit.
					source = entity.damageSources().playerAttack(owner);
				}
				// Priority 2: Standard vanilla attribution fallback
				else if (entity.getLastAttacker() instanceof Player player) {
					source = entity.damageSources().playerAttack(player);
				} else if (entity.getLastHurtByMob() instanceof Player player) {
					source = entity.damageSources().playerAttack(player);
				}

				net.minecraft.world.phys.Vec3 oldVelocity = entity.getDeltaMovement();
				if (entity.hurt(source, 1.0f)) {
					entity.setDeltaMovement(oldVelocity);
				}

				// Spawn explicit particles on server for everyone to see
				if (entity.level() instanceof ServerLevel serverWorld) {
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
							entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(),
							2, 0.3, 0.3, 0.3, 0.05);
				}
			}
		}
		return false;
	}
}