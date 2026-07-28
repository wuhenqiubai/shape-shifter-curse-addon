package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class SscAddonEntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	public void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if ((Object) this instanceof LivingEntity entity && entity.hasEffect(SscAddon.PLAYING_DEAD_ENTRY)) {
			ci.cancel();
		}

	}
}