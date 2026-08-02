package net.jackcooper.shapeShifterCurseAddon.loot;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.Set;

/**
 * 月髓环（{@link SscAddon#SP_UPGRADE_THING}）与进化石（{@link SscAddon#EVOLUTION_STONE}）的自然宝箱生成。
 * <p>
 * 仅沉船 / 古城 / 堡垒遗迹（猪人城堡） / 末地船 的箱子生成：每个箱子 <b>3% 概率</b>触发，
 * 触发后在月髓环与进化石之间<b>二选一</b>（等权重各 50%）。
 * 注：战利品表粒度只能到结构级——堡垒遗迹 4 种箱子、沉船补给/宝藏箱、末地船共用末地城宝藏表，均一并覆盖。
 */
public final class EvolutionItemsLoot {
	private EvolutionItemsLoot() {
	}

	/** 目标原版结构箱子战利品表。 */
	private static final Set<ResourceLocation> TARGET_CHESTS = Set.of(
			// 沉船（补给舱 + 宝藏舱）
			ResourceLocation.fromNamespaceAndPath("minecraft", "chests/shipwreck_supply"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "chests/shipwreck_treasure"),
			// 古城
			ResourceLocation.fromNamespaceAndPath("minecraft", "chests/ancient_city"),
			// 堡垒遗迹（猪人城堡）4 种箱子
			ResourceLocation.fromNamespaceAndPath("minecraft", "chests/bastion_treasure"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "chests/bastion_other"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "chests/bastion_bridge"),
			ResourceLocation.fromNamespaceAndPath("minecraft", "chests/bastion_hoglin_stable"),
			// 末地船（与末地城宝藏箱共用 end_city_treasure）
			ResourceLocation.fromNamespaceAndPath("minecraft", "chests/end_city_treasure")
	);

	/** 每个箱子生成月髓环 / 进化石（二选一）的总概率。 */
	private static final float CHANCE = 0.03F;

	public static void register() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!TARGET_CHESTS.contains(key.location())) {
				return;
			}
			// 3% 概率触发；触发后在月髓环与进化石之间二选一（等权重各 50%）
			tableBuilder.pool(LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.conditionally(LootItemRandomChanceCondition.randomChance(CHANCE).build())
					.with(LootItem.lootTableItem(SscAddon.SP_UPGRADE_THING).build())
					.with(LootItem.lootTableItem(SscAddon.EVOLUTION_STONE).build()).build());
		});
	}
}