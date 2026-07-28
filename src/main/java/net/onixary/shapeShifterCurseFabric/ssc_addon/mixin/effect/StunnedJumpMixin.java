package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent jumping when player is stunned
 */
@Mixin(LivingEntity.class)
public class StunnedJumpMixin {

	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void onJump(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof Player player && player.hasEffect(SscAddon.STUN_ENTRY)) {
			ci.cancel();
		}

	}
}