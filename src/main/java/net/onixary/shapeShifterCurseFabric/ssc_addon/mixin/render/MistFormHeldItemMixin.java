package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 血雾 / 真隐身期间隐藏玩家手持物品（主手 + 副手）。
 * vanilla 隐身效果不会隐藏手持物，需要在 HeldItemFeatureRenderer.render 头部直接 cancel。
 */
@Mixin(ItemInHandLayer.class)
public class MistFormHeldItemMixin {

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true)
	private void ssc_addon$hideHeldItemDuringMist(PoseStack matrices, MultiBufferSource vertexConsumers, int light,
												 LivingEntity entity, float limbAngle, float limbDistance,
												 float tickDelta, float animationProgress, float headYaw, float headPitch,
												 CallbackInfo ci) {
		if (entity.hasEffect(SscAddon.MIST_FORM_ENTRY)
				|| entity.hasEffect(SscAddon.TRUE_INVISIBILITY_ENTRY)) {
			ci.cancel();
		}
	}
}