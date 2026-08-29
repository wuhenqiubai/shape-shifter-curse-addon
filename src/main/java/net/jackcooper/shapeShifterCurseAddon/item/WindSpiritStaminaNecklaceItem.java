/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.List;

/** 风灵专属项链：加快「疾风连爪」过热后的耐力回复速度（效果在 WindSpiritClawManager 生效）。 */
public class WindSpiritStaminaNecklaceItem extends AccessoryItem {
    public WindSpiritStaminaNecklaceItem(Settings settings) {
        super(settings);
    }

    @Override
	public boolean canEquip(ItemStack stack, LivingEntity entity, AccessoryItem.SlotData slotData) {
        return net.jackcooper.shapeShifterCurseAddon.item.AddonAccessoryGuard.canEquip(entity, FormUtils::isOcelotSP);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.ssc_addon.wind_spirit_stamina_necklace.desc").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.ssc_addon.wind_spirit_stamina_necklace.tooltip.exclusive").formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, context, tooltip, type);
    }
}