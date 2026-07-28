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

import java.util.List;

/**
 * 阿努比斯权杖上的水晶 - SP阿努比斯之狼专属饰品
 * 效果：增加冥狼召唤数量和上限
 * 获取途径：沙漠神殿战利品箱，15%概率
 */
public class AnubisCrystalItem extends TrinketItem {
	public AnubisCrystalItem(Properties settings) {
		super(settings);
	}

	/**
	 * 注册到沙漠神殿战利品表（15%概率，1个）
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (key.location().equals(ResourceLocation.fromNamespaceAndPath("minecraft", "chests/desert_pyramid"))) {
				LootPool.Builder poolBuilder = LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0F))
						.when(LootItemRandomChanceCondition.randomChance(0.15F))
						.add(LootItem.lootTableItem(SscAddon.ANUBIS_CRYSTAL));
				tableBuilder.withPool(poolBuilder);
			}
		});
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isAnubisWolfSP(entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.anubis_crystal.tooltip_1").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.ssc_addon.anubis_crystal.tooltip_2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ssc_addon.anubis_crystal.tooltip_3").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ssc_addon.anubis_crystal.tooltip_4").withStyle(ChatFormatting.RED));
		super.appendHoverText(stack, context, tooltip, type);
	}
}