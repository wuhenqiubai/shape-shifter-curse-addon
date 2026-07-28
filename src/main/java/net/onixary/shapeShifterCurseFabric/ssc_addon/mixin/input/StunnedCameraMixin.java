package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to lock camera movement when player is stunned
 */
@Mixin(MouseHandler.class)
public class StunnedCameraMixin {

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void onUpdateMouse(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.hasEffect(SscAddon.STUN_ENTRY)) {
			// Cancel all mouse movement updates when stunned - this locks the camera
			ci.cancel();
		}
	}
}