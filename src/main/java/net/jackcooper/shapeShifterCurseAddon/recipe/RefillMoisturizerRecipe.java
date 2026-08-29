package net.jackcooper.shapeShifterCurseAddon.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.item.PortableMoisturizerItem;
import org.jetbrains.annotations.NotNull;

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
	public @NotNull ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
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
			moisturizer.setCount(1);
			// 按当前等级上限充满使用时间
			PortableMoisturizerItem.setFullCharge(moisturizer);
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