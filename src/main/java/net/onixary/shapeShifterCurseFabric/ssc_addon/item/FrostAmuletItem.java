package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.item.tooltip.TooltipType;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;

public class FrostAmuletItem extends AccessoryItem {
	public FrostAmuletItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
		return net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard.canEquip(entity, FormUtils::isSnowFoxSP);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.ssc_addon.frost_amulet.tooltip.2").formatted(Formatting.BLUE));
		tooltip.add(Text.translatable("item.ssc_addon.frost_amulet.tooltip.exclusive").formatted(Formatting.AQUA));
		super.appendTooltip(stack, context, tooltip, type);
	}
}