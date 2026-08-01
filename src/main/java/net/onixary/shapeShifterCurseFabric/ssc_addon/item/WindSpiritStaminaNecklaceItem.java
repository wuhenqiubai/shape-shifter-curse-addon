/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
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

/** 风灵专属项链：加快「疾风连爪」过热后的耐力回复速度（效果在 WindSpiritClawManager 生效）。 */
public class WindSpiritStaminaNecklaceItem extends TrinketItem {
    public WindSpiritStaminaNecklaceItem(Properties settings) {
        super(settings);
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        return FormUtils.isOcelotSP(entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.ssc_addon.wind_spirit_stamina_necklace.desc").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.ssc_addon.wind_spirit_stamina_necklace.tooltip.exclusive").withStyle(ChatFormatting.LIGHT_PURPLE));
        super.appendHoverText(stack, context, tooltip, type);
    }
}