package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;

public class BlueFireAmuletItem extends TrinketItem {
	public BlueFireAmuletItem(Properties settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isFamiliarFoxForm(entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.blue_fire_amulet.tooltip_1").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.ssc_addon.blue_fire_amulet.tooltip_2").withStyle(ChatFormatting.GRAY));
		super.appendHoverText(stack, context, tooltip, type);
	}
}