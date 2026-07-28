package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class TrueInvisibilityArmorMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void hideArmorWhenTrueInvisible(PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, LivingEntity livingEntity, float f, float g, float h, float j, float k, float l, CallbackInfo ci) {
		if (livingEntity.hasEffect(SscAddon.TRUE_INVISIBILITY_ENTRY)
				|| livingEntity.hasEffect(SscAddon.MIST_FORM_ENTRY)) {
			ci.cancel();
		}
	}
}