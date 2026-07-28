package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.resources.ResourceLocation;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_SnowFox3;

/**
 * 金沙岚 - 胡狼SP形态（与冥裁者平行的独立SP变体）
 * 围绕凋零效果展开的攻击型形态
 */
public class Form_GoldenSandstormSP extends AbstractFeralForm {
	public Form_GoldenSandstormSP(ResourceLocation formID) {
		super(formID);
	}

	@Override
	protected AbstractAnimStateController createRideController() {
		return Form_SnowFox3.RIDE_CONTROLLER;
	}
}