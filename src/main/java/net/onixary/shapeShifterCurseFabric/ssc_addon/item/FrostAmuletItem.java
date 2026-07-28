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

public class FrostAmuletItem extends TrinketItem {
	public FrostAmuletItem(Properties settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isSnowFoxSP(entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.frost_amulet.tooltip.2").withStyle(ChatFormatting.BLUE));
		tooltip.add(Component.translatable("item.ssc_addon.frost_amulet.tooltip.exclusive").withStyle(ChatFormatting.AQUA));
		super.appendHoverText(stack, context, tooltip, type);
	}
}