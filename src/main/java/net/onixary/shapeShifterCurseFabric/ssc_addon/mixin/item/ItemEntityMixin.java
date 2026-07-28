package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.WaterSpearItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

	protected ItemEntityMixin(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Shadow
	public abstract ItemStack getItem();

	@Shadow
	public abstract int getAge();

	@Inject(method = "tick", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		if (!this.level().isClientSide) {
			ItemStack stack = this.getItem();
			if (stack.getItem() instanceof WaterSpearItem && this.getAge() >= 20) {
				this.discard();
			}
			if (stack.is(SscAddon.ALLAY_HEAL_WAND) || stack.is(SscAddon.ALLAY_JUKEBOX) || stack.is(SscAddon.POTION_BAG)) {
				this.discard();
			}
		}
	}
}
