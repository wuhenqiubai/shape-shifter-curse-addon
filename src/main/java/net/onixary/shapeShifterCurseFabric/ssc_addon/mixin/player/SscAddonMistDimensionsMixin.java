package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
@Mixin(Player.class)
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
	private void sscAddon$markConstructed(Level level, BlockPos blockPos, float f, GameProfile gameProfile, CallbackInfo ci) {
		this.sscAddon$constructed = true;
	}

	@Unique
	private boolean sscAddon$isMistFormActive() {
		return this.sscAddon$constructed && ((Player) (Object) this).hasEffect(SscAddon.MIST_FORM_ENTRY);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void sscAddon$refreshMistDimensions(CallbackInfo ci) {
		boolean mistFormActive = this.sscAddon$isMistFormActive();
		if (mistFormActive != this.sscAddon$mistDimensionsApplied) {
			this.sscAddon$mistDimensionsApplied = mistFormActive;
			((Player) (Object) this).refreshDimensions();
		}
	}

	@ModifyExpressionValue(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;"))
	private EntityDimensions sscAddon$mistDimensions(EntityDimensions original, Pose pose) {
		if (this.sscAddon$isMistFormActive()) {
			EntityDimensions mistSize = EntityDimensions.scalable(MIST_WIDTH, MIST_HEIGHT);
			return mistSize.withEyeHeight(MIST_EYE_HEIGHT);
		}
		return original;
	}
}