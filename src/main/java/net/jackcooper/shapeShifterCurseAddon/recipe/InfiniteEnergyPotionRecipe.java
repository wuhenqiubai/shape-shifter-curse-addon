package net.jackcooper.shapeShifterCurseAddon.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.items.RegCustomPotions;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.jetbrains.annotations.NotNull;

/**
 * 无限压缩能量药水合成配方（特殊配方，需匹配带 NBT 的 feed_potion 药水，原版 shaped 无法匹配）。
 * 布局（3x3）：
 * <pre>
 *   0 M 0      M = 月髓环 (ssc_addon:sp_upgrade_thing)
 *   A I A      A = 附魔金苹果 (minecraft:enchanted_golden_apple)
 *   0 0 0      I = 压缩能量药水 (原版药水 + feed_potion)
 * </pre>
 */
public class InfiniteEnergyPotionRecipe extends SpecialCraftingRecipe {

	public InfiniteEnergyPotionRecipe(CraftingRecipeCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world) {
		if (input.getWidth() < 3 || input.getHeight() < 3) {
			return false;
		}
		// 形状 0 M 0 / A I A / 0 0 0，允许整体上对齐或下对齐两种摆放：
		// 上：M=1 A=3 I=4 A=5，空=0,2,6,7,8
		// 下：M=4 A=6 I=7 A=8，空=0,1,2,3,5
		return matchesLayout((RecipeInputInventory) input, 1, 3, 4, 5, new int[]{0, 2, 6, 7, 8})
				|| matchesLayout((RecipeInputInventory) input, 4, 6, 7, 8, new int[]{0, 1, 2, 3, 5});
	}

	/** 校验一种摆放：moonSlot=月髓环，appleSlotL/appleSlotR=附魔金苹果，potionSlot=压缩能量药水，emptySlots 必须为空。 */
	private boolean matchesLayout(RecipeInputInventory inv, int moonSlot, int appleSlotL, int potionSlot, int appleSlotR, int[] emptySlots) {
		if (!isMoonRing(inv.getStack(moonSlot))) {
			return false;
		}
		if (!isEnchantedGoldenApple(inv.getStack(appleSlotL)) || !isEnchantedGoldenApple(inv.getStack(appleSlotR))) {
			return false;
		}
		if (!isCompressedEnergyPotion(inv.getStack(potionSlot))) {
			return false;
		}
		for (int slot : emptySlots) {
			if (!inv.getStack(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private boolean isMoonRing(ItemStack stack) {
		return stack.isOf(SscAddon.SP_UPGRADE_THING);
	}

	private boolean isEnchantedGoldenApple(ItemStack stack) {
		return stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
	}

	/** 压缩能量药水 = 原版药水物品且药水类型为 feed_potion。 */
	private boolean isCompressedEnergyPotion(ItemStack stack) {
		return stack.isOf(Items.POTION) && stack.getOrDefault(net.minecraft.component.DataComponentTypes.POTION_CONTENTS, net.minecraft.component.type.PotionContentsComponent.DEFAULT).potion().map(p -> p.value() == RegCustomPotions.FEED_POTION).orElse(false);
	}

	@Override
	public @NotNull ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		return new ItemStack(SscAddon.INFINITE_ENERGY_POTION);
	}

	@Override
	public boolean fits(int width, int height) {
		return width >= 3 && height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SscAddon.INFINITE_ENERGY_POTION_SERIALIZER;
	}
}