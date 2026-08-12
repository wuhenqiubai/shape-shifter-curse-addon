package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.UpgradeAxolotlSpearRenderState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进化美西螈「投掷水矛」蓄力期：第三人称<b>纯渲染</b>把主手渲染的物品替换成 3D 水矛（不动玩家背包）。
 *
 * <p>重定向 {@code HeldItemFeatureRenderer.render} 里的 {@code entity.getMainHandStack()}：
 * 蓄力中返回带 {@code CustomModelData=1} 的水矛（恒定 3D）。覆盖左/右利手（该调用在两种利手分支各出现一次，
 * 只有实际利手那支执行）与<b>空手</b>情况（返回非空水矛使渲染分支不被 isEmpty 跳过）。
 * 举矛过肩姿势由 {@code UpgradeAxolotlSpearArmPoseMixin}(getArmPose→THROW_SPEAR) 提供。多人下对被追踪的
 * 蓄力玩家同样生效（蓄力状态已广播给追踪端）。</p>
 */
@Mixin(ItemInHandLayer.class)
public class UpgradeAxolotlThirdPersonSpearMixin {

	@WrapOperation(
			method = "render",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"),require = 0
	)
	private ItemStack ssc_addon$swapTpSpear(LivingEntity entity, Operation<ItemStack> original) {
		if (entity instanceof AbstractClientPlayer
				&& UpgradeAxolotlSpearRenderState.isCharging(entity.getUUID())) {
			ItemStack spear = new ItemStack(SscAddon.WATER_SPEAR);
			spear.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(1));
			return spear;
		}
		return original.call(entity);
	}

	// 蓄力期把主手渲染的水矛「举起过肩」（第三人称纯渲染抬矛蓄力效果；数值可实机微调）
	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void ssc_addon$raiseTpPush(LivingEntity entity, ItemStack stack, ItemDisplayContext mode, HumanoidArm arm,
			PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
		if (arm == entity.getMainArm() && UpgradeAxolotlSpearRenderState.isCharging(entity.getUUID())) {
			matrices.pushPose();
			// 轻微后仰增强蓄力感（举矛过肩由 THROW_SPEAR 手臂姿势负责，避免把矛推离手心）
			matrices.mulPose(Axis.XP.rotationDegrees(-10.0F));
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void ssc_addon$raiseTpPop(LivingEntity entity, ItemStack stack, ItemDisplayContext mode, HumanoidArm arm,
			PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
		if (arm == entity.getMainArm() && UpgradeAxolotlSpearRenderState.isCharging(entity.getUUID())) {
			matrices.popPose();
		}
	}

	/**
	 * 血雾 / 真隐身期间隐藏玩家手持物品（主手 + 副手）。（原 MistFormHeldItemMixin 合并至此；同为 HeldItemFeatureRenderer 目标，行为不变。）
	 * vanilla 隐身不隐藏手持物，需在 render 头部直接 cancel。
	 */
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