package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.TidalOrbEntity;

/**
 * 荧光幼灵潮汐球渲染器。
 *
 * <p>非拴人期：完全沿用 {@link ThrownItemRenderer}（潮涌方块物品作发光核心，外观与原来一致）。
 * <p>拴人（激活）期：把核心换成「激活态潮涌核心」——开壳 cage + 旋转 wind 风纹 + 朝相机发光 open_eye，
 * 与原版激活潮涌一致。激活状态由实体 {@code DataTracker} 同步，多人一致。
 */
@Environment(EnvType.CLIENT)
public class TidalOrbRenderer extends ThrownItemRenderer<TidalOrbEntity> {

    // 直接指向原版潮涌贴图文件（不走图集，避免图集常量在版本间的不确定性）
    private static final ResourceLocation WIND_TEX = ResourceLocation.parse("textures/entity/conduit/wind.png");
    private static final ResourceLocation WIND_VERTICAL_TEX = ResourceLocation.parse("textures/entity/conduit/wind_vertical.png");
    private static final ResourceLocation OPEN_EYE_TEX = ResourceLocation.parse("textures/entity/conduit/open_eye.png");

    private final ModelPart eye;
    private final ModelPart wind;

    public TidalOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, 1.0F, false); // 与原注册一致：scale 1.0、非自发光
        this.eye = ctx.bakeLayer(ModelLayers.CONDUIT_EYE);
        this.wind = ctx.bakeLayer(ModelLayers.CONDUIT_WIND);
    }

    @Override
    public void render(TidalOrbEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MultiBufferSource vcp, int light) {
        if (!entity.isTetherActive()) {
            // 非拴人期：完全沿用原版飞行物品渲染，外观零变化
            super.render(entity, yaw, tickDelta, matrices, vcp, light);
            return;
        }
        // 拴人期：渲染「激活态潮涌核心」（旋转风纹 + 朝相机发光眼）
        long time = entity.level().getGameTime();
        int fullBright = 0xF000F0;

        matrices.pushPose();
        matrices.translate(0.0, 0.15, 0.0);   // 微抬到球体中心
        matrices.scale(0.75f, 0.75f, 0.75f);    // 整体尺寸（较初版放大 50%）

        // wind（风纹，三向循环 + 自转，营造激活漩涡）
        int frame = (int) (time / 22) % 3;
        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotationDegrees(((float) time + tickDelta) * 2.5f));
        if (frame == 1) {
            matrices.mulPose(Axis.XP.rotationDegrees(90f));
        } else if (frame == 2) {
            matrices.mulPose(Axis.ZP.rotationDegrees(90f));
        }
        ResourceLocation windTex = (frame == 0) ? WIND_TEX : WIND_VERTICAL_TEX;
        this.wind.render(matrices,
                vcp.getBuffer(RenderType.entityCutoutNoCull(windTex)),
                fullBright, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        // open_eye（朝相机、发光）
        matrices.pushPose();
        matrices.mulPose(this.entityRenderDispatcher.cameraOrientation());
        matrices.scale(0.5f, 0.5f, 0.5f);
        this.eye.render(matrices,
                vcp.getBuffer(RenderType.entityTranslucent(OPEN_EYE_TEX)),
                fullBright, OverlayTexture.NO_OVERLAY);
        matrices.popPose();

        matrices.popPose();
        // 拴人期不调用 super：中央只显示激活态潮涌核心
    }
}