package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.UpgradeAxolotlSpearRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进化美西螈「投掷水矛」蓄力期：把主手臂姿势设为原版「投掷长矛」姿势（THROW_SPEAR），
 * 即与手持三叉戟/水矛按住右键蓄力时完全一致的举矛过肩姿态（第一/三人称通用）。
 */
@Mixin(PlayerRenderer.class)
public class UpgradeAxolotlSpearArmPoseMixin {

	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private static void ssc_addon$spearThrowPose(AbstractClientPlayer player, InteractionHand hand,
			CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		if (hand == InteractionHand.MAIN_HAND && UpgradeAxolotlSpearRenderState.isCharging(player.getUUID())) {
			cir.setReturnValue(HumanoidModel.ArmPose.THROW_SPEAR);
		}
	}
}
