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
 * 双生种荚（寄生果蝠专属）。
 * 好处：一次「灵果寄生」播种会额外寄生主目标附近最近的另一个生物（敌友各自结对应果实）。
 * 坏处：种子量消耗翻倍（每次 2 点），且冷却额外 +1 秒。
 * 数值逻辑在 ParasiticFruitSeedPower.onUse 中按是否装备本饰品调整。
 *
 * 获取途径：20% 概率出现在废弃矿井战利品箱中。
 */
public class TwinPodItem extends TrinketItem {

	private static final ResourceLocation MINESHAFT_LOOT = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/abandoned_mineshaft");

	public TwinPodItem(Properties settings) {
		super(settings);
	}

	/**
	 * 注册双生种荚到废弃矿井战利品表（10% 概率）。
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!MINESHAFT_LOOT.equals(key.location())) return;
			LootPool.Builder pool = LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(LootItemRandomChanceCondition.randomChance(0.20F))
					.add(LootItem.lootTableItem(SscAddon.TWIN_POD));
			tableBuilder.withPool(pool);
		});
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isBatParasiticFruit(entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.twin_pod.tooltip_1").withStyle(ChatFormatting.DARK_GREEN));
		tooltip.add(Component.translatable("item.ssc_addon.twin_pod.tooltip_2").withStyle(ChatFormatting.YELLOW));
		tooltip.add(Component.translatable("item.ssc_addon.twin_pod.tooltip_exclusive").withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, tooltip, type);
	}
}