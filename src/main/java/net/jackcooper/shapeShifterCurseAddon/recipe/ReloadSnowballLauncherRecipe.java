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
import net.jackcooper.shapeShifterCurseAddon.item.SnowballLauncherItem;
import org.jetbrains.annotations.NotNull;

public class ReloadSnowballLauncherRecipe extends SpecialCraftingRecipe {

	public ReloadSnowballLauncherRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		boolean hasLauncher = false;
		boolean hasAmmo = false;

		for (int i = 0; i < input.getSize(); ++i) {
			ItemStack stack = input.getStackInSlot(i);
			if (!stack.isEmpty()) {
				if (stack.getItem() == SscAddon.SNOWBALL_LAUNCHER) {
					if (hasLauncher) return false; // Only 1 launcher
					hasLauncher = true;
				} else if (stack.getItem() == Items.SNOWBALL || stack.getItem() == Items.SNOW_BLOCK || stack.getItem() == Items.SNOW) {
					hasAmmo = true;
				} else {
					return false; // Invalid item
				}
			}
		}
		return hasLauncher && hasAmmo;
	}

	@Override
	public @NotNull ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		ItemStack launcher = ItemStack.EMPTY;
		int ammoToAdd = 0;

		for (int i = 0; i < input.getSize(); ++i) {
			ItemStack stack = input.getStackInSlot(i);
			if (!stack.isEmpty()) {
				if (stack.getItem() == SscAddon.SNOWBALL_LAUNCHER) {
					launcher = stack.copy();
				} else if (stack.getItem() == Items.SNOWBALL) {
					ammoToAdd += 1;
				} else if (stack.getItem() == Items.SNOW_BLOCK) {
					ammoToAdd += 8; // Increased from 4 to 8 for faster reload
				} else if (stack.getItem() == Items.SNOW) {
					ammoToAdd += 1; // Snow Layer
				}
			}
		}

		if (!launcher.isEmpty()) {
			int currentAmmo = SnowballLauncherItem.getAmmo(launcher);
			SnowballLauncherItem.setAmmo(launcher, currentAmmo + ammoToAdd);
			return launcher;
		}

		return ItemStack.EMPTY;
	}

	@Override
	public boolean fits(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.RELOAD_SNOWBALL_LAUNCHER_SERIALIZER;
	}
}