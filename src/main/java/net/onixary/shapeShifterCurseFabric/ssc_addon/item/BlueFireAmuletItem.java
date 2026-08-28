package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.item.tooltip.TooltipType;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;

public class BlueFireAmuletItem extends AccessoryItem {
	public BlueFireAmuletItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		return net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard.canEquip(entity, FormUtils::isFamiliarFoxForm);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.ssc_addon.blue_fire_amulet.tooltip_1").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.translatable("item.ssc_addon.blue_fire_amulet.tooltip_2").formatted(Formatting.GRAY));
		super.appendTooltip(stack, context, tooltip, type);
	}
}