package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.input;

import net.minecraft.client.Minecraft;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class StunnedInputMixin {

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("resource") // client 是对游戏实例本身的引用，不应关闭
	private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
		Minecraft client = (Minecraft) (Object) this;
		if (client.player != null && client.player.hasEffect(SscAddon.STUN_ENTRY)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("resource") // client 是对游戏实例本身的引用，不应关闭
	private void onDoItemUse(CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		if (client.player != null && client.player.hasEffect(SscAddon.STUN_ENTRY)) {
			ci.cancel();
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("resource") // client 是对游戏实例本身的引用，不应关闭
	private void onHandleBlockBreaking(boolean breaking, CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		if (client.player != null && client.player.hasEffect(SscAddon.STUN_ENTRY) && breaking) {
			ci.cancel();
		}

	}
}