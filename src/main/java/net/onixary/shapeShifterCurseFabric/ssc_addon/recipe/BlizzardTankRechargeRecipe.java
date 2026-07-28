package net.onixary.shapeShifterCurseFabric.ssc_addon.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.PortableFridgeItem;
import org.jetbrains.annotations.NotNull;

public class BlizzardTankRechargeRecipe extends CustomRecipe {

	public BlizzardTankRechargeRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level world) {
		ItemStack tankStack = ItemStack.EMPTY;
		boolean hasSnowBlock = false;

		for (int i = 0; i < input.size(); ++i) {
			ItemStack stack = input.getItem(i);
			if (!stack.isEmpty()) {
				if (stack.getItem() == SscAddon.PORTABLE_FRIDGE) {
					if (!tankStack.isEmpty()) return false; // Only 1 tank allowed
					tankStack = stack;
				} else if (stack.getItem() == Items.SNOW_BLOCK) {
					hasSnowBlock = true;
				} else {
					return false; // No other items allowed
				}
			}
		}

		if (!tankStack.isEmpty() && hasSnowBlock) {
			return PortableFridgeItem.getCharge(tankStack) < PortableFridgeItem.MAX_CHARGE;
		}

		return false;
	}

	@Override
	public @NotNull ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
		ItemStack tank = ItemStack.EMPTY;
		int chargeToAdd = 0;

		for (int i = 0; i < input.size(); ++i) {
			ItemStack stack = input.getItem(i);
			if (!stack.isEmpty()) {
				if (stack.getItem() == SscAddon.PORTABLE_FRIDGE) {
					tank = stack.copy();
				} else if (stack.getItem() == Items.SNOW_BLOCK) {
					chargeToAdd += 8;
				}
			}
		}

		if (!tank.isEmpty() && chargeToAdd > 0) {
			int currentCharge = PortableFridgeItem.getCharge(tank);
			PortableFridgeItem.setCharge(tank, currentCharge + chargeToAdd); // setCharge handles current + add, and internal min/max logic
			return tank;
		}

		return ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.BLIZZARD_TANK_RECHARGE_SERIALIZER;
	}
}