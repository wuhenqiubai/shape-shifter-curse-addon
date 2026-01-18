package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class SscPlayerEntityRendererMixin {

    @Inject(method = "setModelPose", at = @At("RETURN"))
    public void setModelPose(AbstractClientPlayerEntity player, CallbackInfo ci) {
        PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
        if (component != null) {
            PlayerFormBase currentForm = component.getCurrentForm();
            if (currentForm != null && currentForm.FormID != null) {
                if (!currentForm.FormID.getPath().contains("original")) {
                     PlayerEntityRenderer renderer = (PlayerEntityRenderer)(Object)this;
                     PlayerEntityModel<AbstractClientPlayerEntity> model = renderer.getModel();
                     
                     // Hide all parts
                     model.head.visible = false;
                     model.hat.visible = false;
                     model.body.visible = false;
                     model.rightArm.visible = false;
                     model.leftArm.visible = false;
                     model.rightLeg.visible = false;
                     model.leftLeg.visible = false;
                     model.leftSleeve.visible = false;
                     model.rightSleeve.visible = false;
                     model.leftPants.visible = false;
                     model.rightPants.visible = false;
                     model.jacket.visible = false;
                }
            }
        }
    }
}
