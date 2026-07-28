package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SP雪狐近战主要技能 - 雪刺冲刺
 * 向准星方向冲刺8格，碰撞敌人造成6点魔法伤害并施加霜凝效果3秒
 */
public class SnowFoxSpMeleeAbility {

	private static final ConcurrentHashMap<UUID, DashingPlayerData> DASHING_PLAYERS = new ConcurrentHashMap<>();
	private static final double DASH_DISTANCE = 8.0;
	private static final double DASH_SPEED = 1.5;
	private static final float DAMAGE = 8.0f;
	private static final int FROST_FREEZE_DURATION = 60;
	private static final int MANA_COST = 15;
	// ==== NEW CODE: 使用FormIdentifiers ====
	private static final ResourceLocation RESOURCE_ID = FormIdentifiers.SNOW_FOX_RESOURCE;
	private static final ResourceLocation REGEN_COOLDOWN_ID = FormIdentifiers.SNOW_FOX_REGEN_COOLDOWN;

	private SnowFoxSpMeleeAbility() {
	}

	/**
	 * 执行雪刺冲刺
	 * 注意：冷却由Apoli apoli:active_self power的cooldown字段管理
	 */
	public static boolean execute(ServerPlayer player) {
		int currentMana = PowerUtils.getResourceValue(player, RESOURCE_ID);

		if (currentMana < MANA_COST) {
			// 法力不足提示音仅施法者自己听（player.playSound 在服务端会排除自己，故改为定向发包）
			net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager.playSoundToPlayer(
					player, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.0f);
			return false;
		}

		if (DASHING_PLAYERS.containsKey(player.getUUID())) {
			return false;
		}

		PowerUtils.changeResourceValueAndSync(player, RESOURCE_ID, -MANA_COST);
		PowerUtils.setResourceValueAndSync(player, REGEN_COOLDOWN_ID, 100);
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_MELEE_PRIMARY_CD, 120);

		Vec3 lookDir = player.getLookAngle().normalize();

		DashingPlayerData data = new DashingPlayerData(lookDir, 0);
		DASHING_PLAYERS.put(player.getUUID(), data);

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1.0f, 1.2f);

		return true;
	}

	/**
	 * 每tick更新冲刺状态
	 */
	public static void tick(ServerPlayer player) {
		DashingPlayerData data = DASHING_PLAYERS.get(player.getUUID());
		if (data == null) return;

		double distanceMoved = data.ticksElapsed * DASH_SPEED;

		// 不要检查 verticalCollision：踩在地面时 vanilla Entity.move 会基于"重力让 movement.y=-0.08
		// 但被地面阻挡到 0，movement.y != vec3d.y"恒置 verticalCollision=true，导致站立触发的 dash
		// 在第一 tick 就被这里 return 掉，setVelocity 一次都没执行，玩家原地不动也碰不到敌人。
		// 水平撞墙才需要终止 dash，所以只看 horizontalCollision。
		if (distanceMoved >= DASH_DISTANCE || player.horizontalCollision) {
			DASHING_PLAYERS.remove(player.getUUID());
			return;
		}

		Vec3 velocity = data.direction.scale(DASH_SPEED);
		player.setDeltaMovement(velocity);
		player.hurtMarked = true;
		// 关键（多人/客机修复）：玩家移动是客户端权威，单靠 velocityModified 不保证把"自身速度"
		// 下发给控制端，远端客机会原地不动、冲刺无位移、也碰不到沿途实体导致无伤害。
		// 这里每 tick 显式给该玩家连接补发速度包，强制客机应用冲刺速度（主机本地玩家不受影响）。
		if (player.connection != null) {
			player.connection.send(
					new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
		}

		AABB hitbox = player.getBoundingBox().inflate(0.5);
		List<Entity> nearbyEntities = player.level().getEntities(player, hitbox,
				entity -> entity instanceof LivingEntity && !data.hitEntities.contains(entity.getUUID()));

		for (Entity entity : nearbyEntities) {
			if (entity instanceof LivingEntity target) {
				if (WhitelistUtils.isProtected(player, target)) {
					data.hitEntities.add(entity.getUUID());
					continue;
				}
				data.hitEntities.add(entity.getUUID());

				DamageSource source = player.damageSources().playerAttack(player);
				target.hurt(source, DAMAGE);

				target.addEffect(new MobEffectInstance(
						SscAddon.FROST_FREEZE_ENTRY,
						FROST_FREEZE_DURATION,
						0,
						false,
						true,
						true
				));

				// 使用ParticleUtils
				if (player.level() instanceof ServerLevel serverWorld) {
					ParticleUtils.spawnHitParticles(serverWorld, new Vec3(target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ()));
				}

				player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
						SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.5f);
			}
		}

		// 使用ParticleUtils
		if (player.level() instanceof ServerLevel serverWorld) {
			ParticleUtils.spawnSnowflakeParticles(serverWorld, new Vec3(player.getX(), player.getY() + 0.5, player.getZ()));
		}

		data.ticksElapsed++;
	}

	/**
	 * 检查玩家是否正在冲刺
	 */
	public static boolean isDashing(Player player) {
		return DASHING_PLAYERS.containsKey(player.getUUID());
	}

	/**
	 * 玩家断线/死亡时清理所有状态，防止内存泄漏和重连后状态错乱
	 */
	public static void clearPlayer(java.util.UUID uuid) {
		DASHING_PLAYERS.remove(uuid);
	}

	/**
	 * 清理所有冲刺状态，用于服务器重置或全局清理
	 */
	public static void clearAll() {
		DASHING_PLAYERS.clear();
	}
    
    /*
    // 旧代码 (保留参考) 已移至PowerUtils
    
    private static final Identifier RESOURCE_ID_OLD = Identifier.of("my_addon", "form_snow_fox_sp_resource");
    private static final Identifier REGEN_COOLDOWN_ID_OLD = Identifier.of("my_addon", "form_snow_fox_sp_frost_regen_cooldown_resource");
    private static final Identifier POWER_ID_OLD = Identifier.of("my_addon", "form_snow_fox_sp_melee_primary");
    
    private static int getResourceValue(ServerPlayerEntity player) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID_OLD);
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
            PowerType<?> powerType = PowerTypeRegistry.get(RESOURCE_ID_OLD);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof VariableIntPower variablePower) {
                int newValue = Math.max(0, Math.min(100, variablePower.getValue() + change));
                variablePower.setValue(newValue);
                PowerHolderComponent.sync(player);
            }
        } catch (Exception e) {
        }
    }
    
    private static void setRegenCooldown(ServerPlayerEntity player, int value) {
        try {
            PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
            PowerType<?> powerType = PowerTypeRegistry.get(REGEN_COOLDOWN_ID_OLD);
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
            PowerType<?> powerType = PowerTypeRegistry.get(POWER_ID_OLD);
            Power power = powerHolder.getPower(powerType);
            if (power instanceof CooldownPower cooldownPower) {
                cooldownPower.setCooldown(ticks);
            }
        } catch (Exception e) {
        }
    }
    */

	/**
	 * 冲刺中玩家数据
	 */
	private static class DashingPlayerData {
		final Vec3 direction;
		final Set<UUID> hitEntities;
		int ticksElapsed;

		DashingPlayerData(Vec3 direction, int ticksElapsed) {
			this.direction = direction;
			this.hitEntities = new HashSet<>();
			this.ticksElapsed = ticksElapsed;
		}
	}
}