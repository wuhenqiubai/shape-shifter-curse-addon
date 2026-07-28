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
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.ManaBarPos;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

/**
 * 契灵 - 抗伤值条 HUD。
 * 设计：
 *  - 与 mana 条中心对齐（mana 条宽度 80）。
 *  - 长度 = FULL_WIDTH * current/max（线性），current=0 时不显示。
 *  - 位置紧贴 mana 条上方 2 像素。
 *  - 仅在契灵形态显示。
 */
@Environment(EnvType.CLIENT)
public class MancianimaResistanceBar implements HudRenderCallback {
	private static final Minecraft mc = Minecraft.getInstance();
	private static final ResourceLocation BAR_FULL = ResourceLocation.fromNamespaceAndPath("my_addon", "textures/gui/mancianima_resistance_full.png");
	private static final ResourceLocation BAR_EMPTY = ResourceLocation.fromNamespaceAndPath("my_addon", "textures/gui/mancianima_resistance_empty.png");
	private static final int MANA_WIDTH = 80;
	private static final int FULL_WIDTH = 92;
	private static final int HEIGHT = 5;
	private static final int CENTER_OFFSET_X = -(FULL_WIDTH - MANA_WIDTH) / 2; // -6，在 mana 条上居中

	@Override
	public void onHudRender(GuiGraphics context, DeltaTracker tickCounter) {
		if (mc.options.hideGui || mc.player == null) return;
		Player player = mc.player;

		int[] data = PowerUtils.getClientResourceValueAndMax(player, FormIdentifiers.MANCIANIMA_RESISTANCE);
		int current = data[0];
		int max = data[1];
		// 仅在玩家持有该 power 时显示（max>1 即代表契灵形态）
		if (max <= 1) return;
		if (current < 0) current = 0;

		int[] mp = ManaBarPos.get(8, 100, 0);
		int posType = mp[0];
		int offsetX = mp[1];
		int offsetY = mp[2];

		Tuple<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(posType, offsetX, offsetY);
		int x = pos.getA() + CENTER_OFFSET_X;
		// 与 mana 条同 y 坐标（居中、不上移）
		int y = pos.getB();
		// 按比例填充：先画空槽，再叠 FULL（宽 = full * current/max，从左到右填充 = 从右至左减少）
		double percent = (double) current / (double) max;
		int barWidth = (int) Math.ceil(FULL_WIDTH * percent);
		context.blit(BAR_EMPTY, x, y, 0, 0, FULL_WIDTH, HEIGHT, FULL_WIDTH, HEIGHT);
		if (barWidth > 0) {
			context.blit(BAR_FULL, x, y, 0, 0, barWidth, HEIGHT, FULL_WIDTH, HEIGHT);
		}
	}

	@SuppressWarnings("unused")
	private static boolean isMancianima(Player player) {
		try {
			IForm form = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).nowForm;
			return form != null && FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.getFormID());
		} catch (Exception e) {
			return false;
		}
	}
}