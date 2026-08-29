package net.jackcooper.shapeShifterCurseAddon.recipe;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;

/**
 * 毒液腺体合成配方（特殊配方：中心必须是剧毒药水，原版 shaped 无法按药水 NBT 匹配）。
 * 布局（3×3 满格）：
 * <pre>
 *   S S S    S = 蜘蛛眼 ×8（minecraft:spider_eye）
 *   S P S    P = 剧毒药水（普通 poison / 延长 long_poison / II strong_poison 三种均可）
 *   S S S
 * </pre>
 */
public class VenomGlandRecipe extends SpecialCraftingRecipe {

	public VenomGlandRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		if (input.getWidth() < 3 || input.getHeight() < 3) {
			return false;
		}
		// 中心（index 4）必须是剧毒药水，其余 8 格全是蜘蛛眼
		if (!isPoisonPotion(input.getStackInSlot(4))) {
			return false;
		}
		for (int i = 0; i < 9; i++) {
			if (i == 4) continue;
			if (!input.getStackInSlot(i).isOf(Items.SPIDER_EYE)) {
				return false;
			}
		}
		return true;
	}

	/** 剧毒药水 = 饮用/喷溅/滞留任一瓶且药水类型为 poison / long_poison / strong_poison（三级任一）。 */
	private boolean isPoisonPotion(ItemStack stack) {
		if (!stack.isOf(Items.POTION) && !stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) {
			return false;
		}
		PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
		if (contents == null) return false;
		RegistryEntry<Potion> potion = contents.potion().orElse(null);
		return potion == Potions.POISON || potion == Potions.LONG_POISON || potion == Potions.STRONG_POISON;
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		return new ItemStack(SscAddon.VENOM_GLAND);
	}

	@Override
	public boolean fits(int width, int height) {
		return width >= 3 && height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.VENOM_GLAND_SERIALIZER;
	}
}
