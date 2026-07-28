package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 契灵远程削弱：
 * - 持有弓 / 弩 / 三叉戟（PersistentProjectileEntity 子类）发射的弹体在第 1 tick 缩减 20% 速度
 * - 弹体造成的伤害 -20%
 */
@Mixin(AbstractArrow.class)
public abstract class MancianimaProjectileMixin {

	@Unique
	private boolean ssc_addon$mancianimaScaled = false;

	@Inject(method = "tick", at = @At("HEAD"))
	private void ssc_addon$scaleVelocity(CallbackInfo ci) {
		if (ssc_addon$mancianimaScaled) return;
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (self.level().isClientSide()) {
			ssc_addon$mancianimaScaled = true;
			return;
		}
		Entity owner = self.getOwner();
		if (owner instanceof Player p && FormUtils.isForm(p, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
			Vec3 v = self.getDeltaMovement();
			self.setDeltaMovement(v.scale(0.8));
		}
		ssc_addon$mancianimaScaled = true;
	}

	@Inject(method = "getBaseDamage", at = @At("RETURN"), cancellable = true)
	private void ssc_addon$scaleDamage(CallbackInfoReturnable<Double> cir) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		Entity owner = self.getOwner();
		if (owner instanceof LivingEntity le && le instanceof Player p
				&& FormUtils.isForm(p, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
			cir.setReturnValue(cir.getReturnValueD() * 0.8);
		}
	}
}
