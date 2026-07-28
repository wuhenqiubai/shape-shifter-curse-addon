package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PotionBagDeathDropMixin {

	@Shadow
	public abstract Inventory getInventory();

	/**
	 * Drop potion bag items on death if keepInventory is disabled
	 */
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void dropPotionBagItems(CallbackInfo ci) {
		Player player = (Player) (Object) this;

		// Check if keepInventory is enabled
		boolean keepInventory = player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

		// If keepInventory is enabled, don't drop anything
		if (keepInventory) {
			return;
		}

		// Find potion bag in inventory
		ItemStack potionBag = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(SscAddon.POTION_BAG)) {
				potionBag = stack;
				break;
			}
		}

		// Drop all items from the potion bag
		if (!potionBag.isEmpty() && potionBag.has(DataComponents.CUSTOM_DATA)) {
			CustomData nbt = potionBag.get(DataComponents.CUSTOM_DATA);
			if (nbt != null && nbt.getUnsafe().contains("Items", 9)) {
				ListTag list = nbt.getUnsafe().getList("Items", 10);
				for (int i = 0; i < list.size(); ++i) {
					CompoundTag itemTag = list.getCompound(i);
					ItemStack stack = ItemStack.parse(player.level().registryAccess(), itemTag).orElse(ItemStack.EMPTY);
					if (!stack.isEmpty()) {
						player.drop(stack, true, false);
					}
				}
				// Clear the potion bag's items
				CustomData.update(DataComponents.CUSTOM_DATA, potionBag, n -> n.remove("Items"));
			}
		}
	}
}
