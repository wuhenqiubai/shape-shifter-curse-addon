package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.item.tooltip.TooltipType;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.List;

public class InvisibilityCloakItem extends AccessoryItem {
	public InvisibilityCloakItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		return net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard.canEquip(entity, FormUtils::isWildCatSP);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.ssc_addon.invisibility_cloak.tooltip").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.ssc_addon.invisibility_cloak.special").formatted(Formatting.LIGHT_PURPLE));
		super.appendTooltip(stack, context, tooltip, type);
	}
}