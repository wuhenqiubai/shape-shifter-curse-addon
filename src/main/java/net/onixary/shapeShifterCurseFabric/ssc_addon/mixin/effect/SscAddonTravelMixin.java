package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.effect;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.AdaptiveSwimmingPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LivingEntity.class)
public abstract class SscAddonTravelMixin {

	// aliases 兼容 intermediary(field_6362)/yarn(jumping) 运行时映射，防「jumping was not located」崩溃
	@Shadow(aliases = {"field_6362", "jumping"})
	protected boolean jumping;

	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void adaptiveSwimming(Vec3 movementInput, CallbackInfo ci) {
		LivingEntity entity = (LivingEntity) (Object) this;

		// 幽雾化形飞行（悦灵同款）：仅客户端本地玩家接管移动，服务端靠位置同步 + setNoGravity 豁免悬空踢人。
		// WASD 控制水平移动并严格锁速 4.5 格/s（0.225 格/tick）；跳跃键上浮、潜行键下降、无输入缓降。
		if (entity.level().isClientSide && entity instanceof Player mistPlayer
				&& entity.hasEffect(SscAddon.MIST_FORM_ENTRY)) {
			// 凝聚爆破蓄力期间整体减速 50%（带 MIST_CHARGING 标记时）
			double maxH = entity.hasEffect(SscAddon.MIST_CHARGING_ENTRY) ? 0.1125 : 0.225; // 0.225 格/tick = 4.5 格/s

			// 水平：按朝向(yaw)将 WASD 输入转为世界方向，归一化后严格锁定为 maxH（前后左右斜向同速）
			float yaw = entity.getYRot() * 0.017453292F;
			Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
			Vec3 right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
			Vec3 horiz = fwd.scale(movementInput.z).add(right.scale(movementInput.x));
			if (horiz.lengthSqr() > 1.0E-6) {
				horiz = horiz.normalize().scale(maxH);
			} else {
				horiz = Vec3.ZERO;
			}

			// 垂直：跳跃键上浮，潜行键下降，否则缓降（营造悦灵漂浮下沉感）
			double vy;
			if (this.jumping) {
				vy = maxH;
			} else if (mistPlayer.isShiftKeyDown()) {
				vy = -maxH;
			} else {
				vy = Math.max(entity.getDeltaMovement().y - 0.02, -0.10);
			}

			Vec3 v = new Vec3(horiz.x, vy, horiz.z);
			entity.setDeltaMovement(v);
			entity.move(MoverType.SELF, v);
			entity.fallDistance = 0f;
			ci.cancel();
			return;
		}

		if (!entity.isInWater() || !(entity instanceof Player player)) return;

		// Check for power
		PowerHolderComponent component = PowerHolderComponent.KEY.get(player);
		List<AdaptiveSwimmingPower> powers = component.getPowers(AdaptiveSwimmingPower.class);
		if (powers.isEmpty()) return;

		AdaptiveSwimmingPower power = powers.get(0); // Use first active
		if (!power.isActive()) return;

		// Implementation of 3D swimming with inertia

		// Check Inputs for Vertical Intent
		// movementInput.y is usually 0 in survival, so we check jump/sneak flags explicitly
		double verticalInput = movementInput.y;
		if (Math.abs(verticalInput) < 1.0E-5) {
			if (this.jumping) verticalInput += 1.0; // Space
			if (player.isShiftKeyDown()) verticalInput -= 1.0; // Shift
		}

		Vec3 lookVec = entity.getViewVector(1.0F);
		// Total desired vertical drive = Direct Up/Down keys + Looking Up/Down while moving Forward/Back
		double verticalDrive = verticalInput + (lookVec.y * movementInput.z);

		// 1. Friction / Inertia
		Vec3 velocity = entity.getDeltaMovement();

		float friction = power.getFriction();
		float horizontalFriction = friction;

		// If applying vertical force, reduce horizontal friction to preserve horizontal momentum
		if (Math.abs(verticalDrive) > 0.1) {
			horizontalFriction = friction + (1.0f - friction) * 0.8f;
		}

		velocity = new Vec3(
				velocity.x * horizontalFriction,
				velocity.y * friction,
				velocity.z * horizontalFriction
		);

		// 2. Acceleration based on Input
		if (movementInput.lengthSqr() > 1.0E-7D || Math.abs(verticalInput) > 1.0E-5) {

			// Separate Horizontal and Vertical logic to preserve horizontal momentum when looking up/down

			// A. Horizontal Logic (Yaw based)
			float yaw = entity.getYRot() * 0.017453292F;
			Vec3 horizontalForward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
			Vec3 horizontalRight = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)); // Right is Forward rotated -90 deg (or cross up)

			Vec3 horizontalDriveVec = new Vec3(0, 0, 0);
			horizontalDriveVec = horizontalDriveVec.add(horizontalForward.scale(movementInput.z));
			horizontalDriveVec = horizontalDriveVec.add(horizontalRight.scale(movementInput.x));

			if (horizontalDriveVec.lengthSqr() > 0) {
				// Normalize horizontal input to ensure standard acceleration speed regardless of strafe+forward combo
				// And apply full acceleration (user requested: pitch shouldn't reduce horizontal speed)
				horizontalDriveVec = horizontalDriveVec.normalize().scale(power.getAcceleration());
				velocity = velocity.add(horizontalDriveVec);
			}

			// B. Vertical Logic
			if (Math.abs(verticalDrive) > 0) {
				float verticalAccel = power.getAcceleration();
				// If not sprinting, reduce vertical acceleration significantly for better control
				if (!entity.isSprinting()) {
					verticalAccel *= 0.25f;
				}
				velocity = velocity.add(0, verticalDrive * verticalAccel, 0);
			}
		}

		// 3. Max Vertical Speed Cap (Non-sprinting only)
		if (!entity.isSprinting()) {
			double maxVerticalSpeed = 0.25; // Cap normal vertical speed
			if (Math.abs(velocity.y) > maxVerticalSpeed) {
				velocity = new Vec3(velocity.x, Math.signum(velocity.y) * maxVerticalSpeed, velocity.z);
			}
		}

		entity.setDeltaMovement(velocity);
		entity.move(MoverType.SELF, velocity);

		// Cancel vanilla travel to prevent gravity and default water physics
		ci.cancel();
	}
}