package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaAggroTracker;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.UndeadNeutralState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobEntityMixin {

	/**
	 * 每个生物实例独立记录：最后一次看到挑衅玩家的世界时间（-1=未追踪）
	 */
	@Unique
	private long ssc_addon$lastSawProvokedTarget = -1;

	/**
	 * 判断生物是否与玩家形态匹配的中立生物。
	 * 裁决者: 所有亡灵中立
	 * 金沙岚: 仅尸壳和咒文胡狼中立
	 */
	@Unique
	private boolean ssc_addon$isNeutralMobPair(Mob mob, Player player) {
		if (FormUtils.isForm(player, FormIdentifiers.ANUBIS_WOLF_SP)) {
			return mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD);
		}
		if (FormUtils.isForm(player, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
			return mob instanceof Husk || FormUtils.isTransformativeWolf(mob);
		}
		return false;
	}

	/**
	 * 检查生物是否应该忽略该玩家（保持中立）。
	 * 返回true=忽略（不攻击），返回false=不忽略（可攻击）
	 */
	@Unique
	private boolean ssc_addon$shouldUndeadIgnore(Mob mob, Player player) {
		if (!ssc_addon$isNeutralMobPair(mob, player)) return false;
		// 玩家处于挑衅状态 → 生物可攻击
		return !UndeadNeutralState.isPlayerProvoked(player.getUUID(), mob.level().getGameTime());
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$onSetTarget(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		// 契灵：mob 自然丢失目标（vanilla setTarget(null)），清除激怒记录 → "逃离重置"
		if (target == null) {
			LivingEntity prev = self.getTarget();
			if (prev instanceof Player pp && FormUtils.isForm(pp, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				MancianimaAggroTracker.forget(self.getUUID());
			}
		}
		if (target != null) {
			if (self instanceof IronGolem golem
					&& target instanceof Player player
					&& FormUtils.isAllaySP(player)) {
				if (golem.getTarget() == player) {
					golem.setTarget(null);
				}
				golem.setPersistentAngerTarget(null);
				golem.setRemainingPersistentAngerTime(0);
				ci.cancel();
				return;
			}
			if (target.hasEffect(SscAddon.PLAYING_DEAD_ENTRY)) {
				ci.cancel();
				return;
			}
			if (target.hasEffect(SscAddon.TRUE_INVISIBILITY_ENTRY)) {
				ci.cancel();
				return;
			}
			// 劫掠阵营不攻击女巫使魔
			if ((self instanceof Raider || self instanceof Vex || self instanceof Witch)
					&& target instanceof WitchFamiliarEntity) {
				ci.cancel();
				return;
			}
			// 女巫使魔不攻击劫掠阵营
			if (self instanceof WitchFamiliarEntity
					&& (target instanceof Raider || target instanceof Vex || target instanceof Witch || target instanceof WitchFamiliarEntity)) {
				ci.cancel();
				return;
			}
			// 灾厄中立
			if ((self instanceof Raider || self instanceof Vex)
					&& target instanceof Player player
					&& player.getTags().contains("ssc_raid_friend")) {
				ci.cancel();
				return;
			}
			// 亡灵中立：阻止主动索敌，但允许受击后反击
			if (target instanceof Player player
					&& ssc_addon$shouldUndeadIgnore(self, player)) {
				ci.cancel();
				return;
			}
			// 契灵：mob 默认不主动攻击契灵，除非被激怒；坚守者/铁傀儡 不受此限制
			if (target instanceof Player player
					&& FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)
					&& !(self instanceof Warden)
					&& !(self instanceof IronGolem)) {
				if (!MancianimaAggroTracker.isAngered(self.getUUID(), player.getUUID())) {
					ci.cancel();
				}
			}
		}
	}

	@Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$onMobTick(CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;

		// 1. 眩晕逻辑
		if (mob.hasEffect(SscAddon.STUN_ENTRY)) {
			ci.cancel();
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target == null) {
			ssc_addon$lastSawProvokedTarget = -1;
			return;
		}

		// 2. 真隐身脱战
		if (target.hasEffect(SscAddon.TRUE_INVISIBILITY_ENTRY)) {
			mob.setTarget(null);
			return;
		}

		// 3. 灾厄联盟脱战
		if ((mob instanceof Raider || mob instanceof Vex)
				&& target instanceof Player player
				&& player.getTags().contains("ssc_raid_friend")) {
			mob.setTarget(null);
			return;
		}

		// 4. 中立生物脱战机制：基于视野的脱战机制（类似僵尸猪灵）
		if (target instanceof Player player
				&& ssc_addon$isNeutralMobPair(mob, player)) {
			// 挑衅已过期 → 立即脱战
			if (!UndeadNeutralState.isPlayerProvoked(player.getUUID(), mob.level().getGameTime())) {
				mob.setTarget(null);
				ssc_addon$lastSawProvokedTarget = -1;
				return;
			}
			long worldTime = mob.level().getGameTime();
			if (mob.hasLineOfSight(target)) {
				// 能看到目标 → 重置视野计时，同时刷新全局挑衅
				ssc_addon$lastSawProvokedTarget = worldTime;
				UndeadNeutralState.PROVOKE_TIMESTAMPS.put(player.getUUID(), worldTime);
			} else {
				// 看不到目标 → 脱战倒计时
				if (ssc_addon$lastSawProvokedTarget < 0) {
					ssc_addon$lastSawProvokedTarget = worldTime;
				}
				if (worldTime - ssc_addon$lastSawProvokedTarget > UndeadNeutralState.SIGHT_TIMEOUT) {
					mob.setTarget(null);
					ssc_addon$lastSawProvokedTarget = -1;
				}
			}
		}
	}
}