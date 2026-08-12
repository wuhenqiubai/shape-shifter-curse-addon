package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ThrowablePotionItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrowablePotionItem.class)
public class RedFoxThrowDistanceMixin {

	@Unique
	private static final ResourceLocation RED_FOX_MANA_POWER = ResourceLocation.fromNamespaceAndPath("my_addon", "form_familiar_fox_sp_init_mana");

	@WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ThrownPotion;shootFromRotation(Lnet/minecraft/world/entity/Entity;FFFFF)V"), require = 0)
	private void modifyThrowVelocity(ThrownPotion instance, Entity entity, float pitch, float yaw, float roll, float speed, float divergence, Operation<Void> original) {
		if (entity instanceof Player player) {
			// Check if player has the specific power indicating Red Fox form
			boolean isRedFox = PowerHolderComponent.KEY.get(player).getPowers().stream()
					.anyMatch(power -> power.getType().getIdentifier().equals(RED_FOX_MANA_POWER));

			if (isRedFox) {
				speed *= 1.5F; // Increase by 50%
			}
		}
		original.call(instance, entity, pitch, yaw, roll, speed, divergence);
	}
}