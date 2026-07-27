package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_SnowFox3;

public class Form_AnubisWolfSP extends AbstractFeralForm {
	public Form_AnubisWolfSP(Identifier formID) {
		super(formID);
	}

	@Override
	protected AbstractAnimStateController createRideController() {
		return Form_SnowFox3.RIDE_CONTROLLER;
	}
}