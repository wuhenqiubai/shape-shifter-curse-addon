package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.IronGolemOfferFlowerToAllayGoal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronGolem.class)
public abstract class IronGolemAllayFlowerMixin extends Mob {
	protected IronGolemAllayFlowerMixin(EntityType<? extends Mob> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void ssc_addon$addAllayFlowerGoal(CallbackInfo ci) {
		this.goalSelector.addGoal(6, new IronGolemOfferFlowerToAllayGoal((IronGolem) (Object) this));
		// 契灵：铁傀儡主动攻击契灵玩家
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
				(IronGolem) (Object) this,
				Player.class,
				10,
				true,
				false,
				p -> p instanceof Player pe && FormUtils.isForm(pe, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)
		));
	}

	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$convertAllayAttackToHealing(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!(source.getEntity() instanceof Player player) || !FormUtils.isAllaySP(player)) return;

		IronGolem golem = (IronGolem) (Object) this;
		if (amount > 0.0F && golem.getHealth() < golem.getMaxHealth()) {
			golem.heal(amount);
		}
		if (golem.getTarget() == player) {
			golem.setTarget(null);
		}
		if (player.getUUID().equals(golem.getPersistentAngerTarget())) {
			golem.setPersistentAngerTarget(null);
			golem.setRemainingPersistentAngerTime(0);
		}
		cir.setReturnValue(false);
	}
}
