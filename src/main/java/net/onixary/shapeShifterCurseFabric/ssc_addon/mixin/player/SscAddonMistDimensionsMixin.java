package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幽雾化形碰撞箱缩小：雾化期间将玩家碰撞箱缩为 0.2×0.2 格（宽×高），
 * 使其可穿过约 1/4 格（0.25）大小的缝隙——无论竖缝还是横向矮洞均可钻入。
 * 两端均需生效（服务端用于碰撞、客户端用于渲染/相机），故置于通用 mixins 数组。
 */
@Mixin(LivingEntity.class)
public abstract class SscAddonMistDimensionsMixin {

	@Unique
	private static final float MIST_WIDTH = 0.2f;

	@Unique
	private static final float MIST_HEIGHT = 0.1f;

	@Unique
	private static final float MIST_EYE_HEIGHT = 0.08f;

	@Unique
	private boolean sscAddon$constructed = false;

	@Unique
	private boolean sscAddon$mistDimensionsApplied = false;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void sscAddon$markConstructed(EntityType entityType, World world, CallbackInfo ci) {
		this.sscAddon$constructed = true;
	}

	@Unique
	private boolean sscAddon$isMistFormActive() {
		return this.sscAddon$constructed && ((PlayerEntity) (Object) this).hasStatusEffect(SscAddon.MIST_FORM_ENTRY);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void sscAddon$refreshMistDimensions(CallbackInfo ci) {
		boolean mistFormActive = this.sscAddon$isMistFormActive();
		if (mistFormActive != this.sscAddon$mistDimensionsApplied) {
			this.sscAddon$mistDimensionsApplied = mistFormActive;
			((PlayerEntity) (Object) this).calculateDimensions();
		}
	}

	@ModifyExpressionValue(method = "getDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityDimensions;scaled(F)Lnet/minecraft/entity/EntityDimensions;"))
	private EntityDimensions sscAddon$modifyMistDimensions(EntityDimensions original) {
		if (this.sscAddon$isMistFormActive()) {
			// 高度压到 0.1，确保可以穿过 0.25 格高的缝隙
			return EntityDimensions.changing(MIST_WIDTH, MIST_HEIGHT);
		}
		return original;
	}

// 	TODO: 没了，没找到替代所以先不找了
//	@ModifyReturnValue(method = "getEyeHeight", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getEyeHeight(Lnet/minecraft/entity/EntityPose;)F"))
//	private float sscAddon$mistEyeHeight(float original) {
//		if (this.sscAddon$isMistFormActive()) {
//			return MIST_EYE_HEIGHT;
//		}
//		return original;
//	}
}