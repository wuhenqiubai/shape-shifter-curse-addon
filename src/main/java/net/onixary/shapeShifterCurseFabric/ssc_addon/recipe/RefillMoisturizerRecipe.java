package net.onixary.shapeShifterCurseFabric.ssc_addon.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.PortableMoisturizerItem;
import org.jetbrains.annotations.NotNull;

public class RefillMoisturizerRecipe extends CustomRecipe {

	public RefillMoisturizerRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level world) {
		boolean hasMoisturizer = false;
		boolean hasBucket = false;

		for (int i = 0; i < input.size(); ++i) {
			ItemStack stack = input.getItem(i);
			if (!stack.isEmpty()) {
				if (stack.getItem() == SscAddon.PORTABLE_MOISTURIZER && !hasMoisturizer) {
					hasMoisturizer = true;
				} else if (stack.getItem() == Items.AXOLOTL_BUCKET && !hasBucket) {
					hasBucket = true;
				} else {
					return false;
				}
			}
		}
		return hasMoisturizer && hasBucket;
	}

	@Override
	public @NotNull ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
		ItemStack moisturizer = ItemStack.EMPTY;

		// Find input moisturizer to copy NBT if needed (though we reset charge anyway)
		// We might want to preserve "Active" state? Or reset it?
		// Let's create a fresh one with max charge.

		// Actually, we should probably output a copy of the input moisturizer but with full charge.
		for (int i = 0; i < input.size(); ++i) {
			ItemStack stack = input.getItem(i);
			if (stack.getItem() == SscAddon.PORTABLE_MOISTURIZER) {
				moisturizer = stack.copy();
				break;
			}
		}

		if (!moisturizer.isEmpty()) {
			boolean wasActive = moisturizer.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getBoolean("Active");
			moisturizer.setCount(1);

			// Set Charge to Max (5400)
			CustomData.update(DataComponents.CUSTOM_DATA, moisturizer, nbt -> nbt.putInt("Charge", PortableMoisturizerItem.MAX_CHARGE));

			// Should we keep it active? Usually refilling allows it to continue working immediately.
			CustomData.update(DataComponents.CUSTOM_DATA, moisturizer, nbt -> nbt.putBoolean("Active", wasActive));

			return moisturizer;
		}

		return ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.REFILL_MOISTURIZER_SERIALIZER;
	}
}