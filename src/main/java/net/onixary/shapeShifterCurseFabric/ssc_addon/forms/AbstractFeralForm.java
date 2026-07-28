package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AbstractAnimStateController;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimStateEnum;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.MOD_ID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 四足野性形态的抽象基类
 * 仅限雪狐等共用四足形态动画的形态使用
 * SSC 新变形系统：继承原版 Form_FeralBase（NormalForm 子类，构造已设 FERAL 体型并提供共用四足动画控制器）
 */
public abstract class AbstractFeralForm extends Form_FeralBase {
	protected AnimationHolder anim_idle = AnimationHolder.EMPTY;
	protected AnimationHolder anim_sneak_idle = AnimationHolder.EMPTY;
	protected AnimationHolder anim_ride = AnimationHolder.EMPTY;
	protected AnimationHolder anim_walk = AnimationHolder.EMPTY;
	protected AnimationHolder anim_sneak_walk = AnimationHolder.EMPTY;
	protected AnimationHolder anim_sneak_rush = AnimationHolder.EMPTY;
	protected AnimationHolder anim_run = AnimationHolder.EMPTY;
	protected AnimationHolder anim_float = AnimationHolder.EMPTY;
	protected AnimationHolder anim_swim = AnimationHolder.EMPTY;
	protected AnimationHolder anim_dig = AnimationHolder.EMPTY;
	protected AnimationHolder anim_jump = AnimationHolder.EMPTY;
	protected AnimationHolder anim_climb = AnimationHolder.EMPTY;
	protected AnimationHolder anim_fall = AnimationHolder.EMPTY;
	protected AnimationHolder anim_attack = AnimationHolder.EMPTY;
	protected AnimationHolder anim_sleep = AnimationHolder.EMPTY;
	protected AnimationHolder anim_elytra_fly = AnimationHolder.EMPTY;

	protected AbstractFeralForm(ResourceLocation formID) {
		super(formID);
		// 父类 Form_FeralBase 构造已 bodyType(FERAL)
	}

	// SSC 1.9.0 起 PlayerFormBase 已移除该 v2 API；子类仍通过 super 调用初始化字段，故保留
	public void Anim_registerAnims() {
		anim_idle = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("idle")), true);
		anim_sneak_idle = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("sneak_idle")), true);
		anim_ride = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("ride")), true);
		anim_walk = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("walk")), true, 1.2f, 2);
		anim_sneak_walk = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("sneak_walk")), true);
		anim_run = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("run")), true, 2.3f);
		anim_float = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("float")), true);
		anim_swim = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("swim")), true);
		anim_dig = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("dig")), true);
		anim_jump = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("jump")), true);
		anim_climb = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("climb")), true);
		anim_fall = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("fall")), true);
		anim_attack = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("attack")), true);
		anim_sleep = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("sleep")), true);
		anim_elytra_fly = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("elytra_fly")), true);
		anim_sneak_rush = new AnimationHolder(ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId("run")), true, 2.3f);
	}

	protected String getAnimId(String animName) {
		return "form_feral_common_" + animName;
	}

	protected ResourceLocation getAnimIdentifier(String animName) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, getAnimId(animName));
	}

	@Override
	public @Nullable AbstractAnimStateController getAnimStateController(Player player, AnimSystem.AnimSystemData animSystemData, @NotNull ResourceLocation animStateID) {
		AnimStateEnum animStateEnum = AnimStateEnum.getStateEnum(animStateID);
		if (animStateEnum != null) {
			return getControllerMapping(animStateEnum);
		}
		return super.getAnimStateController(player, animSystemData, animStateID);
	}

	protected AbstractAnimStateController getControllerMapping(AnimStateEnum animStateEnum) {
		return switch (animStateEnum) {
			case ANIM_STATE_SLEEP -> createSleepController();
			case ANIM_STATE_CLIMB -> createClimbController();
			case ANIM_STATE_FALL -> createFallController();
			case ANIM_STATE_JUMP -> createJumpController();
			case ANIM_STATE_RIDE -> createRideController();
			case ANIM_STATE_SWIM -> createSwimController();
			case ANIM_STATE_USE_ITEM -> createUseItemController();
			case ANIM_STATE_WALK -> createWalkController();
			case ANIM_STATE_SPRINT -> createSprintController();
			case ANIM_STATE_IDLE -> createIdleController();
			case ANIM_STATE_MINING -> createMiningController();
			case ANIM_STATE_ATTACK -> createAttackController();
			case ANIM_STATE_FLYING, ANIM_STATE_FALL_FLYING -> createFallFlyingController();
			// 原版 AnimStateEnum 新增 ANIM_STATE_CRAWL 等枚举时，回落到 idle（与原版 Form_FeralBase default 一致）
			default -> createIdleController();
		};
	}

	protected AbstractAnimStateController createSleepController() {
		return Form_FeralBase.SLEEP_CONTROLLER;
	}

	protected AbstractAnimStateController createClimbController() {
		return Form_FeralBase.CLIMB_CONTROLLER;
	}

	protected AbstractAnimStateController createFallController() {
		return Form_FeralBase.FALL_CONTROLLER;
	}

	protected AbstractAnimStateController createJumpController() {
		return Form_FeralBase.JUMP_CONTROLLER;
	}

	protected AbstractAnimStateController createRideController() {
		return Form_FeralBase.RIDE_CONTROLLER;
	}

	protected AbstractAnimStateController createSwimController() {
		return Form_FeralBase.SWIM_CONTROLLER;
	}

	protected AbstractAnimStateController createUseItemController() {
		return Form_FeralBase.USE_ITEM_CONTROLLER;
	}

	protected AbstractAnimStateController createWalkController() {
		return Form_FeralBase.WALK_CONTROLLER;
	}

	protected AbstractAnimStateController createSprintController() {
		return Form_FeralBase.SPRINT_CONTROLLER;
	}

	protected AbstractAnimStateController createIdleController() {
		return Form_FeralBase.IDLE_CONTROLLER;
	}

	protected AbstractAnimStateController createMiningController() {
		return Form_FeralBase.MINING_CONTROLLER;
	}

	protected AbstractAnimStateController createAttackController() {
		return Form_FeralBase.ATTACK_CONTROLLER;
	}

	protected AbstractAnimStateController createFallFlyingController() {
		return Form_FeralBase.FALL_FLYING_CONTROLLER;
	}
}