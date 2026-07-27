package net.onixary.shapeShifterCurseFabric.ssc_addon.recipe;

import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.PortableMoisturizerItem;

public class RefillMoisturizerRecipe extends SpecialCraftingRecipe {

	public RefillMoisturizerRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		boolean hasMoisturizer = false;
		boolean hasBucket = false;

		for (int i = 0; i < input.getSize(); ++i) {
			ItemStack stack = input.getStackInSlot(i);
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
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		ItemStack moisturizer = ItemStack.EMPTY;

		// Find input moisturizer to copy NBT if needed (though we reset charge anyway)
		// We might want to preserve "Active" state? Or reset it?
		// Let's create a fresh one with max charge.

		// Actually, we should probably output a copy of the input moisturizer but with full charge.
		for (int i = 0; i < input.getSize(); ++i) {
			ItemStack stack = input.getStackInSlot(i);
			if (stack.getItem() == SscAddon.PORTABLE_MOISTURIZER) {
				moisturizer = stack.copy();
				break;
			}
		}

		if (!moisturizer.isEmpty()) {
			boolean wasActive = moisturizer.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).getNbt().getBoolean("Active");
			moisturizer.setCount(1);

			// Set Charge to Max (5400)
			NbtComponent.set(DataComponentTypes.CUSTOM_DATA, moisturizer, nbt -> nbt.putInt("Charge", PortableMoisturizerItem.MAX_CHARGE));

			// Should we keep it active? Usually refilling allows it to continue working immediately.
			NbtComponent.set(DataComponentTypes.CUSTOM_DATA, moisturizer, nbt -> nbt.putBoolean("Active", wasActive));

			return moisturizer;
		}

		return ItemStack.EMPTY;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.REFILL_MOISTURIZER_SERIALIZER;
	}
}