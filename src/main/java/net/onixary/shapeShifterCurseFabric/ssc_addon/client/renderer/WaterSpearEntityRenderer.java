package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.WaterSpearEntity;

@Environment(EnvType.CLIENT)
public class WaterSpearEntityRenderer extends EntityRenderer<WaterSpearEntity> {
	private final ItemRenderer itemRenderer;

	public WaterSpearEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemRenderer = context.getItemRenderer();
	}

	@Override
	public void render(WaterSpearEntity entity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
		matrices.pushPose();
		// Fixed rotation: -90.0F to match vanilla direction
		matrices.mulPose(Axis.YP.rotationDegrees(Mth.lerp(tickDelta, entity.yRotO, entity.getYRot()) - 90.0F));
		matrices.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(tickDelta, entity.xRotO, entity.getXRot()) - 90.0F));

		ItemStack stack = entity.getWeaponItem();
		if (stack != null && !stack.isEmpty()) {
			// Use a copy of the stack to avoid modifying the actual item NBT (which would persist when picked up)
			ItemStack renderStack = stack.copy();
			renderStack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(1));
			this.itemRenderer.renderStatic(renderStack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, matrices, vertexConsumers, entity.level(), entity.getId());
		}

		matrices.popPose();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(WaterSpearEntity entity) {
		return ResourceLocation.parse("textures/atlas/blocks.png");
	}
}