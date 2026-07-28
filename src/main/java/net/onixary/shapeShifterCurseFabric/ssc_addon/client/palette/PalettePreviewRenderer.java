package net.onixary.shapeShifterCurseFabric.ssc_addon.client.palette;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.player_form.skin.PlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.palette.PaletteCodec;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;
import org.joml.Quaternionf;

/**
 * 在附属自定义 Screen 内渲染玩家当前形态的小预览（附属内部副本）。
 *
 * 实现：渲染前临时把玩家 PlayerSkinComponent.formColor 换成预览配色，
 * InventoryScreen.drawEntity 后立刻还原，避免污染真实组件状态。
 *
 * 备注：原版同名工具位于 net.onixary.shapeShifterCurseFabric.client.palette，
 * 服务于原版「玩家颜色自定义」入口处的配色预设界面；本副本仅供附属包内的
 * AdvancedColorScreen 等界面使用，保持附属包可独立编译。
 */
public final class PalettePreviewRenderer {
    private PalettePreviewRenderer() {}

    /**
     * @param previewCode 槽位代码；null 或空表示预览当前真实配色
     */
    public static void render(GuiGraphics context, int x, int y, int size,
                              int mouseX, int mouseY,
                              LocalPlayer player, String previewCode) {
        if (player == null) return;

        FormTextureUtils.ColorSetting backupColor = null;
        boolean backupEnabled = false;
        boolean swapped = false;
        PlayerSkinComponent skin = null;

        if (previewCode != null && !previewCode.isEmpty()) {
            try {
                PaletteCodec.PaletteData d = PaletteCodec.decode(previewCode);
                skin = RegPlayerSkinComponent.SKIN_SETTINGS.get(player);
                // 防御：未注册皮肤组件的玩家 get 会返回 null，此时跳过配色替换，直接渲染原样
                if (skin == null) {
                    drawEntityFollowMouse(context, x, y, size, mouseX, mouseY, player);
                    return;
                }
                backupColor = skin.getFormColor();
                backupEnabled = skin.isEnableFormColor();
                FormTextureUtils.ColorSetting preview = new FormTextureUtils.ColorSetting(
                        FormTextureUtils.RGBA2ABGR(d.primaryRGBA()),
                        FormTextureUtils.RGBA2ABGR(d.accent1RGBA()),
                        FormTextureUtils.RGBA2ABGR(d.accent2RGBA()),
                        FormTextureUtils.RGBA2ABGR(d.eyeARGBA()),
                        FormTextureUtils.RGBA2ABGR(d.eyeBRGBA()),
                        d.primaryGreyReverse(), d.accent1GreyReverse(), d.accent2GreyReverse());
                skin.setFormColor(preview);
                skin.setEnableFormColor(true);
                swapped = true;
            } catch (PaletteCodec.DecodeException ignored) {
                // 解码失败：回退到当前真实配色
            }
        }

        try {
            drawEntityFollowMouse(context, x, y, size, mouseX, mouseY, player);
        } finally {
            if (swapped) {
                skin.setFormColor(backupColor);
                skin.setEnableFormColor(backupEnabled);
            }
        }
    }

    /** 复刻 BookOfShapeShifterScreenV2_P1.RenderEntity 的鼠标跟随旋转。 */
    private static void drawEntityFollowMouse(GuiGraphics context, int x, int y, int size,
                                              int mouseX, int mouseY, LivingEntity entity) {
        float f = (float) Math.atan(mouseX / 40.0F);
        float g = (float) Math.atan(mouseY / 40.0F);
        Quaternionf body = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf head = new Quaternionf().rotateX(g * 20.0F * 0.017453292F);
        body.mul(head);

        float bh = entity.yBodyRot;
        float yaw = entity.getYRot();
        float pitch = entity.getXRot();
        float phy = entity.yHeadRotO;
        float hy = entity.yHeadRot;
        float pby = entity.yBodyRotO;
        entity.yBodyRot = 180.0F + f * 20.0F;
        entity.yBodyRotO = entity.yBodyRot;
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-g * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        try {
            InventoryScreen.renderEntityInInventory(context, (float)x, (float)y, size, new org.joml.Vector3f(0.0f, entity.getBbHeight() / 2.0f, 0.0f), body, head, entity);
        } finally {
            entity.yBodyRot = bh;
            entity.yBodyRotO = pby;
            entity.setYRot(yaw);
            entity.setXRot(pitch);
            entity.yHeadRotO = phy;
            entity.yHeadRot = hy;
        }
    }
}
