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
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.SnowballLauncherItem;
import org.jetbrains.annotations.NotNull;

public class ReloadSnowballLauncherRecipe extends CustomRecipe {

	public ReloadSnowballLauncherRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level world) {
		boolean hasLauncher = false;
		boolean hasAmmo = false;

		for (int i = 0; i < input.size(); ++i) {
			ItemStack stack = input.getItem(i);
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
	public @NotNull ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
		ItemStack launcher = ItemStack.EMPTY;
		int ammoToAdd = 0;

		for (int i = 0; i < input.size(); ++i) {
			ItemStack stack = input.getItem(i);
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
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.RELOAD_SNOWBALL_LAUNCHER_SERIALIZER;
	}
}