package net.onixary.shapeShifterCurseFabric.ssc_addon.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SpUpgradeRecipe extends CustomRecipe {

	public SpUpgradeRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level world) {
		// Grid must be at least 3x3 for safety, though inventory usually is 3x3
		if (input.width() < 3 || input.height() < 3) return false;

		// Check Fixed Positions
		// 0 1 2
		// 3 4 5
		// 6 7 8

		// Corners: Gold Ingot
		if (!stackMatches(input.getItem(0), Items.GOLD_INGOT) ||
				!stackMatches(input.getItem(2), Items.GOLD_INGOT) ||
				!stackMatches(input.getItem(6), Items.GOLD_INGOT) ||
				!stackMatches(input.getItem(8), Items.GOLD_INGOT)) {
			return false;
		}

		// Center: Morphscale Core
		if (!stackMatches(input.getItem(4), RegCustomItem.MORPHSCALE_CORE)) {
			return false;
		}

		// Remaining Slots: 1, 3, 5, 7. Must contain exactly {Emerald, Redstone, MoonShard, Netherite}
		List<Item> finding = new ArrayList<>();
		finding.add(Items.EMERALD);
		finding.add(Items.REDSTONE);
		finding.add(RegCustomItem.MOONDUST_CRYSTAL_SHARD);
		finding.add(Items.NETHERITE_INGOT);

		int[] checkSlots = {1, 3, 5, 7};
		for (int i : checkSlots) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) return false;
			if (finding.contains(stack.getItem())) {
				finding.remove(stack.getItem());
			} else {
				return false; // Valid item but duplicate or unexpected
			}
		}

		return finding.isEmpty();
	}

	private boolean stackMatches(ItemStack stack, Item item) {
		return !stack.isEmpty() && stack.getItem() == item;
	}

	@Override
	public @NotNull ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
		return new ItemStack(SscAddon.SP_UPGRADE_THING);
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width >= 3 && height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.SP_UPGRADE_SERIALIZER;
	}
}