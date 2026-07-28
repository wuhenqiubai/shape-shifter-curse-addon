package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin {

	@Shadow
	public Player player;

	@Shadow
	public abstract ItemStack getItem(int slot);

	@Shadow
	public abstract void setItem(int slot, ItemStack stack);

	@Shadow
	public abstract boolean add(ItemStack stack);

	/**
	 * Helper to check if an item is a locked form-exclusive item in a specific slot
	 */
	@Unique
	private boolean isLockedAllayItem(int slot, ItemStack stack) {
		IForm currentForm = FormUtils.getCurrentForm(player);
		boolean isAllaySp = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "allay_sp"));
		if (!isAllaySp) return false;

		if (slot == 0 && stack.is(SscAddon.ALLAY_HEAL_WAND)) return true;
		return slot == 1 && stack.is(SscAddon.ALLAY_JUKEBOX);
	}

	/**
	 * Prevents removing potion bag from slot 8 if player is Red form
	 * Prevents removing allay items from slots 0/1 if player is Allay SP form
	 */
	@Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
	private void preventLockedItemRemoval(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack stack = this.getItem(slot);

		// Red form: lock potion bag in slot 8
		if (slot == 8 && stack.is(SscAddon.POTION_BAG)) {
			IForm currentForm = FormUtils.getCurrentForm(player);
			boolean isRedForm = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_red"));
			if (isRedForm) {
				cir.setReturnValue(ItemStack.EMPTY);
				return;
			}
		}

		// Allay SP form: lock heal wand in slot 0, jukebox in slot 1
		if (isLockedAllayItem(slot, stack)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}

	/**
	 * Prevents setting locked items to wrong slots
	 */
	@Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
	private void preventLockedItemMisplacement(int slot, ItemStack stack, CallbackInfo ci) {
		// Potion Bag logic (existing)
		if (stack.is(SscAddon.POTION_BAG) && slot != 8) {
			IForm currentForm = FormUtils.getCurrentForm(player);
			boolean isRedForm = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_red"));
			if (isRedForm) {
				ci.cancel();
				ItemStack slot8Stack = this.getItem(8);
				if (!slot8Stack.is(SscAddon.POTION_BAG)) {
					if (!slot8Stack.isEmpty()) {
						this.setItem(slot, slot8Stack);
					}
					this.setItem(8, stack);
				}
				return;
			}
		}

		// Allay Heal Wand: must stay in slot 0
		if (stack.is(SscAddon.ALLAY_HEAL_WAND) && slot != 0) {
			IForm currentForm = FormUtils.getCurrentForm(player);
			boolean isAllaySp = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "allay_sp"));
			if (isAllaySp) {
				ci.cancel();
				return;
			}
		}

		// Allay Jukebox: must stay in slot 1
		if (stack.is(SscAddon.ALLAY_JUKEBOX) && slot != 1) {
			IForm currentForm = FormUtils.getCurrentForm(player);
			boolean isAllaySp = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "allay_sp"));
			if (isAllaySp) {
				ci.cancel();
			}
		}
	}

	/**
	 * Prevents inserting locked items outside their designated slots
	 */
	@Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void preventLockedItemInsert(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.is(SscAddon.POTION_BAG)) {
			IForm currentForm = FormUtils.getCurrentForm(player);
			boolean isRedForm = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_red"));
			if (!isRedForm) {
				cir.setReturnValue(false);
			} else if (slot != 8 && slot != -1) {
				cir.setReturnValue(false);
			}
		}

		if (stack.is(SscAddon.ALLAY_HEAL_WAND) || stack.is(SscAddon.ALLAY_JUKEBOX)) {
			IForm currentForm = FormUtils.getCurrentForm(player);
			boolean isAllaySp = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "allay_sp"));
			if (!isAllaySp) {
				cir.setReturnValue(false);
			}
		}
	}
}