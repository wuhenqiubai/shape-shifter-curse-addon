package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.UpgradeAxolotlSpearRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进化美西螈「投掷水矛」蓄力期：第一人称<b>纯渲染</b>把主手物品替换成 3D 水矛（不动玩家背包），
 * 并举成「过肩、准备投掷」的姿势（矩阵变换包裹渲染）。
 * 靠 {@code CustomModelData=1} 走 custom_model_data override 恒定 3D。
 */
@Mixin(ItemInHandRenderer.class)
public class UpgradeAxolotlFirstPersonSpearMixin {

	// 蓄力期把主手渲染的物品替换成 3D 水矛（仅渲染层，不改背包）
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
	private ItemStack ssc_addon$swapFpSpear(ItemStack item, AbstractClientPlayer player,
			float tickDelta, float pitch, InteractionHand hand) {
		if (player != null && hand == InteractionHand.MAIN_HAND
				&& UpgradeAxolotlSpearRenderState.isCharging(player.getUUID())) {
			ItemStack spear = new ItemStack(SscAddon.WATER_SPEAR);
			spear.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(1));
			return spear;
		}
		return item;
	}

	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void ssc_addon$raiseFpPush(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand,
			float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
			MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
		if (hand == InteractionHand.MAIN_HAND && player != null
				&& UpgradeAxolotlSpearRenderState.isCharging(player.getUUID())) {
			matrices.pushPose();
			// 轻微后仰增强蓄力感（位置交给 display + THROW_SPEAR 手臂姿势，避免把矛推离手心）
			matrices.mulPose(Axis.XP.rotationDegrees(-10.0F));
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void ssc_addon$raiseFpPop(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand,
			float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
			MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
		if (hand == InteractionHand.MAIN_HAND && player != null
				&& UpgradeAxolotlSpearRenderState.isCharging(player.getUUID())) {
			matrices.popPose();
		}
	}
}