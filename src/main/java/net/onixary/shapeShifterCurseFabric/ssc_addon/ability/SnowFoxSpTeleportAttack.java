package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP雪狐近战次要技能 - 瞬移攻击
 * 瞬移到10格范围内最多3个敌人身后攻击
 */
public class SnowFoxSpTeleportAttack {

	private static final ConcurrentHashMap<UUID, TeleportAttackData> ATTACKING_PLAYERS = new ConcurrentHashMap<>();
	private static final double RANGE = 10.0;
	private static final int MAX_TARGETS = 3;
	private static final float BASE_DAMAGE = 6.0f;
	private static final float BONUS_DAMAGE = 3.0f;
	private static final int MANA_COST_SUCCESS = 30;
	private static final int MANA_COST_FAIL = 20;
	private static final int TELEPORT_INTERVAL = 10;
	private static final float DAMAGE_REDUCTION = 0.65f;
	// ==== NEW CODE: 使用FormIdentifiers ====
	private static final ResourceLocation RESOURCE_ID = FormIdentifiers.SNOW_FOX_RESOURCE;
	private static final ResourceLocation REGEN_COOLDOWN_ID = FormIdentifiers.SNOW_FOX_REGEN_COOLDOWN;

	private SnowFoxSpTeleportAttack() {
	}

	/**
	 * 执行瞬移攻击
	 * 注意：冷却由Apoli apoli:active_self power的cooldown字段管理(400tick成功/100tick失败)
	 */
	public static boolean execute(ServerPlayer player) {
		if (ATTACKING_PLAYERS.containsKey(player.getUUID())) {
			return false;
		}

		int currentMana = PowerUtils.getResourceValue(player, RESOURCE_ID);
		if (currentMana < MANA_COST_FAIL) {
			player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.0f);
			return false;
		}

		List<LivingEntity> targets = findTargets(player);

		if (targets.isEmpty()) {
			player.playSound(SoundEvents.FIRE_EXTINGUISH, 1.0f, 1.0f);
			PowerUtils.changeResourceValueAndSync(player, RESOURCE_ID, -MANA_COST_FAIL);
			setRegenCooldown(player, 100);
			return false;
		}

		if (currentMana < MANA_COST_SUCCESS) {
			player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.0f);
			return false;
		}

		PowerUtils.changeResourceValueAndSync(player, RESOURCE_ID, -MANA_COST_SUCCESS);
		setRegenCooldown(player, 100);
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_MELEE_SECONDARY_CD, 400);

		Vec3 originalPos = player.position();
		float originalYaw = player.getYRot();
		float originalPitch = player.getXRot();

		TeleportAttackData data = new TeleportAttackData(
				originalPos, originalYaw, originalPitch,
				targets, 0, 0
		);
		ATTACKING_PLAYERS.put(player.getUUID(), data);

		teleportToTarget(player, data);

		return true;
	}

	/**
	 * 每tick更新状态
	 */
	public static void tick(ServerPlayer player) {
		TeleportAttackData data = ATTACKING_PLAYERS.get(player.getUUID());
		if (data == null) return;

		if (player.hasEffect(SscAddon.PURIFIED_ENTRY)) {
			returnToOrigin(player, data);
			ATTACKING_PLAYERS.remove(player.getUUID());
			return;
		}

		data.ticksSinceLastTeleport++;

		player.setDeltaMovement(0, 0, 0);
		player.hurtMarked = true;

		if (data.ticksSinceLastTeleport >= TELEPORT_INTERVAL) {
			data.currentTargetIndex++;
			data.ticksSinceLastTeleport = 0;

			if (data.currentTargetIndex < data.targets.size()) {
				teleportToTarget(player, data);
			} else {
				returnToOrigin(player, data);
				ATTACKING_PLAYERS.remove(player.getUUID());
			}
		}
	}

	/**
	 * 瞬移到目标身后并攻击
	 */
	private static void teleportToTarget(ServerPlayer player, TeleportAttackData data) {
		if (data.currentTargetIndex >= data.targets.size()) return;

		LivingEntity target = data.targets.get(data.currentTargetIndex);

		if (target.isDeadOrDying() || target.isRemoved()) {
			return;
		}

		Vec3 targetPos = target.position();
		Vec3 targetLookDir = target.getLookAngle().normalize();
		Vec3 behindPos = targetPos.subtract(targetLookDir.scale(1.5));

		player.teleportTo((ServerLevel) player.level(), behindPos.x, behindPos.y, behindPos.z, player.getYRot(), player.getXRot());

		Vec3 toTarget = targetPos.subtract(behindPos).normalize();
		float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
		player.setYRot(yaw);
		player.setXRot(0);

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);

		if (player.level() instanceof ServerLevel serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.REVERSE_PORTAL,
					player.getX(), player.getY() + player.getBbHeight() / 2, player.getZ(),
					20, 0.3, 0.5, 0.3, 0.05);
		}

		player.swing(player.getUsedItemHand());

		float damage = BASE_DAMAGE;

		MobEffectInstance frostEffect = target.getEffect(SscAddon.FROST_FREEZE_ENTRY);
		if (frostEffect != null) {
			damage += BONUS_DAMAGE;
		}

		DamageSource source = player.damageSources().playerAttack(player);
		Vec3 oldVelocity = target.getDeltaMovement();
		if (target.hurt(source, damage)) {
			target.setDeltaMovement(oldVelocity);
		}

		if (player.level() instanceof ServerLevel serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SNOWFLAKE,
					target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
					15, 0.3, 0.3, 0.3, 0.1);
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SWEEP_ATTACK,
					target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
					1, 0, 0, 0, 0);
		}

		player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f);
	}

	/**
	 * 返回原位
	 */
	private static void returnToOrigin(ServerPlayer player, TeleportAttackData data) {
		player.teleportTo((ServerLevel) player.level(), data.originalPos.x, data.originalPos.y, data.originalPos.z, player.getYRot(), player.getXRot());
		player.setYRot(data.originalYaw);
		player.setXRot(data.originalPitch);
		player.setDeltaMovement(0, 0, 0);
		player.hurtMarked = true;

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.8f);

		if (player.level() instanceof ServerLevel serverWorld) {
			ParticleUtils.spawnParticles(serverWorld, ParticleTypes.REVERSE_PORTAL,
					player.getX(), player.getY() + player.getBbHeight() / 2, player.getZ(),
					30, 0.3, 0.5, 0.3, 0.05);
		}
	}

	/**
	 * 查找范围内的目标
	 */
	private static List<LivingEntity> findTargets(ServerPlayer player) {
		List<LivingEntity> result = new ArrayList<>();
		AABB searchBox = player.getBoundingBox().inflate(RANGE);

		List<LivingEntity> nearbyEntities = player.level().getEntitiesOfClass(
				LivingEntity.class, searchBox,
				entity -> entity != player &&
						!entity.isSpectator() &&
						entity.isAlive() &&
						player.distanceToSqr(entity) <= RANGE * RANGE &&
						!WhitelistUtils.isProtected(player, entity)
		);

		nearbyEntities.sort(Comparator.comparingDouble(player::distanceToSqr));

		for (int i = 0; i < Math.min(MAX_TARGETS, nearbyEntities.size()); i++) {
			result.add(nearbyEntities.get(i));
		}

		return result;
	}

	/**
	 * 检查玩家是否正在瞬移攻击
	 */
	public static boolean isAttacking(ServerPlayer player) {
		return ATTACKING_PLAYERS.containsKey(player.getUUID());
	}

	/**
	 * 玩家断线/死亡时清理所有状态，防止内存泄漏和重连后传送到错误位置
	 */
	public static void clearPlayer(java.util.UUID uuid) {
		ATTACKING_PLAYERS.remove(uuid);
	}

	/**
	 * 清除所有正在进行的传送攻击状态
	 */
	public static void clearAll() {
		ATTACKING_PLAYERS.clear();
	}

	/**
	 * 获取伤害减免系数（用于Mixin）
	 */
	public static float getDamageReduction(ServerPlayer player) {
		if (isAttacking(player)) {
			return DAMAGE_REDUCTION;
		}
		return 0.0f;
	}

	/**
	 * 设置回复冷却（使用后5秒内无法自然回复霜寒值）
	 * ==== NEW CODE: 使用PowerUtils ====
	 */
	public static void setRegenCooldown(ServerPlayer player, int value) {
		PowerUtils.setResourceValueAndSync(player, REGEN_COOLDOWN_ID, value);
	}
    
    /*
    // 旧代码 (保留参考) 已移至PowerUtils
    
    private static int getResourceValue(ServerPlayerEntity player) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                return variablePower.getValue();
            }
        } catch (Exception e) {
        }
        return 0;
    }
    
    private static void changeResourceValue(ServerPlayerEntity player, int change) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                int newValue = Math.max(0, Math.min(100, variablePower.getValue() + change));
                variablePower.setValue(newValue);
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
        }
    }
    
    public static void setRegenCooldownOld(ServerPlayerEntity player, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(REGEN_COOLDOWN_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                variablePower.setValue(value);
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
        }
    }
    
    private static void setPowerCooldown(ServerPlayerEntity player, int ticks) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(POWER_ID);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof CooldownPower cooldownPower) {
                cooldownPower.setCooldown(ticks);
            }
        } catch (Exception e) {
        }
    }
    */

	/**
	 * 瞬移攻击数据
	 */
	private static class TeleportAttackData {
		final Vec3 originalPos;
		final float originalYaw;
		final float originalPitch;
		final List<LivingEntity> targets;
		int currentTargetIndex;
		int ticksSinceLastTeleport;

		TeleportAttackData(Vec3 originalPos,
		                   float originalYaw, float originalPitch,
		                   List<LivingEntity> targets,
		                   int currentTargetIndex, int ticksSinceLastTeleport) {
			this.originalPos = originalPos;
			this.originalYaw = originalYaw;
			this.originalPitch = originalPitch;
			this.targets = targets;
			this.currentTargetIndex = currentTargetIndex;
			this.ticksSinceLastTeleport = ticksSinceLastTeleport;
		}
	}
}