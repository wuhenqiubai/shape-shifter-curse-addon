package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.OneAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateControllerDP.WithSneakAnimController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimUtils;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Form_Allay extends NormalForm {
	private static final String ANIM_NS = "my_addon";
	public static final AbstractAnimStateController WALK_CONTROLLER = new WithSneakAnimController(new AnimUtils.AnimationHolderData(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_moving")), new AnimUtils.AnimationHolderData(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_sneaking_walk")));
	public static final AbstractAnimStateController SPRINT_CONTROLLER = new WithSneakAnimController(new AnimUtils.AnimationHolderData(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_run")), new AnimUtils.AnimationHolderData(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_sneaking_walk")));
	public static final AbstractAnimStateController IDLE_CONTROLLER = new WithSneakAnimController(new AnimUtils.AnimationHolderData(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_idle")), new AnimUtils.AnimationHolderData(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_sneaking")));
	public static final AbstractAnimStateController MINING_CONTROLLER = new OneAnimController(new AnimUtils.AnimationHolderData(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_digging")));
	public static final AbstractAnimStateController ATTACK_CONTROLLER = new OneAnimController(new AnimUtils.AnimationHolderData(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_attack")));
	public static final AbstractAnimStateController FLYING_CONTROLLER = new OneAnimController(new AnimUtils.AnimationHolderData(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_fly")));
	private static AnimationHolder anim_walk = AnimationHolder.EMPTY;
	private static AnimationHolder anim_run = AnimationHolder.EMPTY;
	private static AnimationHolder anim_sneak_idle = AnimationHolder.EMPTY;
	private static AnimationHolder anim_sneak_walk = AnimationHolder.EMPTY;
	private static AnimationHolder anim_digging = AnimationHolder.EMPTY;
	private static AnimationHolder anim_flying = AnimationHolder.EMPTY;
	private static AnimationHolder anim_idle = AnimationHolder.EMPTY;
	private static AnimationHolder anim_attack = AnimationHolder.EMPTY;

	public Form_Allay(ResourceLocation formID) {
		super(formID);
	}

	// SSC 1.9.0 起 PlayerFormBase 已移除该 v2 API
	public void Anim_registerAnims() {
		anim_walk = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_moving"), true);
		anim_run = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_run"), true);
		anim_sneak_idle = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_sneaking"), true);
		anim_sneak_walk = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_sneaking_walk"), true);
		anim_digging = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_digging"), true);
		anim_flying = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_fly"), true);
		anim_idle = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_idle"), true);
		anim_attack = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(ANIM_NS, "allay_sp_attack"), true);
	}

	@Override
	public @Nullable AbstractAnimStateController getAnimStateController(Player player, AnimSystem.AnimSystemData animSystemData, @NotNull ResourceLocation animStateID) {
		@Nullable AnimStateEnum animStateEnum = AnimStateEnum.getStateEnum(animStateID);
		if (animStateEnum != null) {
			return switch (animStateEnum) {
				case ANIM_STATE_SPRINT -> SPRINT_CONTROLLER;
				case ANIM_STATE_IDLE -> IDLE_CONTROLLER;
				case ANIM_STATE_MINING -> MINING_CONTROLLER;
				case ANIM_STATE_ATTACK -> ATTACK_CONTROLLER;
				case ANIM_STATE_JUMP, ANIM_STATE_FALL, ANIM_STATE_FALL_FLYING, ANIM_STATE_FLYING -> FLYING_CONTROLLER;
				default -> WALK_CONTROLLER;
			};
		}
		return super.getAnimStateController(player, animSystemData, animStateID);
	}
}