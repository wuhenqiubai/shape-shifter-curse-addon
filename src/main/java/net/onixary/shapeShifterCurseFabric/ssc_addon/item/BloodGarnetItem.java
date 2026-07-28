package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 渴血石榴石（吸血蝙蝠专属）。
 * 好处：血渴值累积速度 +50%（普攻 / 技能 / 击杀获得量 ×1.5）。
 * 坏处：脱战衰减 +50% 且每秒被动流失 1 点血渴值。
 * 数值逻辑在 BatDesmodusBloodThirst 中按是否装备本饰品调整。
 *
 * 获取途径：20% 概率出现在废弃矿井战利品箱中。
 */
public class BloodGarnetItem extends TrinketItem {

	private static final ResourceLocation MINESHAFT_LOOT = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/abandoned_mineshaft");

	public BloodGarnetItem(Properties settings) {
		super(settings);
	}

	/**
	 * 注册渴血石榴石到废弃矿井战利品表（25% 概率）。
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!MINESHAFT_LOOT.equals(key.location())) return;
			LootPool.Builder pool = LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(LootItemRandomChanceCondition.randomChance(0.20F))
					.add(LootItem.lootTableItem(SscAddon.BLOOD_GARNET));
			tableBuilder.withPool(pool);
		});
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isBatDesmodus(entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.blood_garnet.tooltip_1").withStyle(ChatFormatting.DARK_RED));
		tooltip.add(Component.translatable("item.ssc_addon.blood_garnet.tooltip_2").withStyle(ChatFormatting.RED));
		tooltip.add(Component.translatable("item.ssc_addon.blood_garnet.tooltip_exclusive").withStyle(ChatFormatting.LIGHT_PURPLE));
		super.appendHoverText(stack, context, tooltip, type);
	}
}