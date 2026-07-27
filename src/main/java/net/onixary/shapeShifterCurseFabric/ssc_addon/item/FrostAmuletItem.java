package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;

public class FrostAmuletItem extends TrinketItem {
	public FrostAmuletItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isSnowFoxSP(entity);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.ssc_addon.frost_amulet.tooltip.2").formatted(Formatting.BLUE));
		tooltip.add(Text.translatable("item.ssc_addon.frost_amulet.tooltip.exclusive").formatted(Formatting.AQUA));
		super.appendTooltip(stack, context, tooltip, type);
	}
}