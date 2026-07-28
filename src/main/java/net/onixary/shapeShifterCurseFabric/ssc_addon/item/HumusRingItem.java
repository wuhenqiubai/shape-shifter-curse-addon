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
 * 腐殖之戒（寄生果蝠专属）。
 * 好处：敌方削弱果效果时长 +50%。
 * 坏处：友方增益果效果时长 -30%（更偏攻击 / 控制向）。
 * 数值逻辑在 ParasiticFruitSeedPower 的果实时长计算中按是否装备本饰品调整。
 *
 * 获取途径：20% 概率出现在废弃矿井战利品箱中。
 */
public class HumusRingItem extends TrinketItem {

	private static final ResourceLocation MINESHAFT_LOOT = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/abandoned_mineshaft");

	public HumusRingItem(Properties settings) {
		super(settings);
	}

	/**
	 * 注册腐殖之戒到废弃矿井战利品表（25% 概率）。
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!MINESHAFT_LOOT.equals(key.location())) return;
			LootPool.Builder pool = LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(LootItemRandomChanceCondition.randomChance(0.20F))
					.add(LootItem.lootTableItem(SscAddon.HUMUS_RING));
			tableBuilder.withPool(pool);
		});
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isBatParasiticFruit(entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.humus_ring.tooltip_1").withStyle(ChatFormatting.DARK_PURPLE));
		tooltip.add(Component.translatable("item.ssc_addon.humus_ring.tooltip_2").withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.translatable("item.ssc_addon.humus_ring.tooltip_exclusive").withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, tooltip, type);
	}
}