package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer.layer;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.WitchFamiliarEntity;

/**
 * 女巫使魔发光眼睛渲染层 — 使用与MC蜘蛛完全一致的 RenderLayer.getEyes()
 * 特性：无光照计算 + 加法透明 + 黑暗中可见
 */
public class WitchFamiliarEyesLayer extends GeoRenderLayer<WitchFamiliarEntity> {

	// 眼睛发光纹理（只包含眼睛像素，其余透明）
	private static final ResourceLocation EYES_TEXTURE =
			ResourceLocation.fromNamespaceAndPath("ssc_addon", "textures/entity/witch_familiar_eyes.png");

	public WitchFamiliarEyesLayer(GeoEntityRenderer<WitchFamiliarEntity> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, WitchFamiliarEntity animatable,
	                   BakedGeoModel bakedModel, RenderType renderType,
	                   MultiBufferSource bufferSource, VertexConsumer buffer,
	                   float partialTick, int packedLight, int packedOverlay) {
		// 使用蜘蛛眼睛的渲染类型（无光照 + 加法透明）
		RenderType eyesRenderType = RenderType.eyes(EYES_TEXTURE);
		VertexConsumer eyesBuffer = bufferSource.getBuffer(eyesRenderType);

		getRenderer().reRender(
				bakedModel,
				poseStack,
				bufferSource,
				animatable,
				eyesRenderType,
				eyesBuffer,
				partialTick,
				15728640, // 全亮度（LightmapTextureManager.MAX_LIGHT_COORDINATE）
				LivingEntityRenderer.getOverlayCoords(animatable, 0),
				-1
		);
	}
}