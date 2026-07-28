package net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ManaBarPos;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

/**
 * 吸血蝙蝠形态 - 雾血资源条 HUD。
 * 仅在玩家拥有雾血资源（即处于吸血蝙蝠形态）时渲染，复用暗色调灵魂条贴图契合吸血主题。
 * 位置复用魔力条配置，吸血蝙蝠形态无魔力条，两者不冲突。
 */
@Environment(EnvType.CLIENT)
public class BatDesmodusBloodBar implements HudRenderCallback {
	private static final Minecraft mc = Minecraft.getInstance();
	private static final ResourceLocation BarTexFullID = ResourceLocation.fromNamespaceAndPath("my_addon", "textures/gui/bat_desmodus_blood_bar_full.png");
	private static final ResourceLocation BarTexEmptyID = ResourceLocation.fromNamespaceAndPath("my_addon", "textures/gui/bat_desmodus_blood_bar_empty.png");
	private static final ResourceLocation RESOURCE_ID = ResourceLocation.fromNamespaceAndPath("my_addon", "form_bat_desmodus_blood_resource");

	@Override
	public void onHudRender(GuiGraphics context, DeltaTracker tickCounter) {
		if (mc.options.hideGui || mc.player == null) return;

		Player player = mc.player;
		int[] resourceData = PowerUtils.getClientResourceValueAndMax(player, RESOURCE_ID);
		int current = resourceData[0];
		int max = resourceData[1];
		// 非吸血蝙蝠形态无此资源 -> 不渲染
		if (current <= 0 && max <= 1) return;
		double percent = (double) current / (double) max;

		int[] mp = ManaBarPos.get(8, 100, -17);
		int posType = mp[0];
		int offsetX = mp[1];
		int offsetY = mp[2];

		Tuple<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(
				posType,
				offsetX,
				offsetY
		);

		renderBar(context, tickCounter, pos.getA(), pos.getB(), percent);
	}

	private void renderBar(GuiGraphics context, DeltaTracker tickCounter, int x, int y, double percent) {
		int barWidth = (int) Math.ceil(80 * percent);
		// 绘制空槽
		context.blit(BarTexEmptyID, x, y, 0, 0, 80, 5, 80, 5);
		// 绘制已填充部分（按百分比裁剪）
		context.blit(BarTexFullID, x, y, 0, 0, barWidth, 5, 80, 5);
	}
}