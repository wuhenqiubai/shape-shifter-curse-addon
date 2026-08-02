package net.jackcooper.shapeShifterCurseAddon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 海晶荧光坠：荧光幼灵形态专属项链。
 * 装备后强化两个主动技能——潮汐球变一次性爆炸球（加速、范围伤害与减速），
 * 法阵激光变缩范围三连发（施法期仅半速而非完全定身）。
 * 具体效果在 TidalOrbEntity / LaserBeamEntity / 两个 Manager 里通过
 * TrinketUtils.isWearing(owner, SscAddon.SEA_CRYSTAL_PENDANT) 判定生效。
 */
public class SeaCrystalPendantItem extends TrinketItem {
	public SeaCrystalPendantItem(Properties settings) {
		super(settings);
	}

	// 沉船宝藏箱 + 藏宝图（埋藏的宝藏）箱，均以 15% 概率掉落；本饰品无法合成，仅此两种途径获得
	private static final ResourceLocation SHIPWRECK_TREASURE_LOOT = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/shipwreck_treasure");
	private static final ResourceLocation BURIED_TREASURE_LOOT = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/buried_treasure");

	/** 注册海晶荧光坠到沉船宝藏箱 + 藏宝图宝藏箱战利品表（各 15% 概率）；本饰品无法合成，仅此两种途径获得。 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!SHIPWRECK_TREASURE_LOOT.equals(key.location()) && !BURIED_TREASURE_LOOT.equals(key.location())) return;
			LootPool.Builder pool = LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.conditionally(LootItemRandomChanceCondition.randomChance(0.15F).build())
					.with(LootItem.lootTableItem(SscAddon.SEA_CRYSTAL_PENDANT).build());
			tableBuilder.pool(pool.build());
		});
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		// 专属限制：只有荧光幼灵（含阿澪）能装备
		return FormUtils.isAxolotlFluorescent(entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable TooltipContext world, List<Component> tooltip, TooltipFlag context) {
		tooltip.add(Component.translatable("item.ssc_addon.sea_crystal_pendant.desc").withStyle(ChatFormatting.BLUE));
		tooltip.add(Component.translatable("item.ssc_addon.sea_crystal_pendant.tooltip.exclusive").withStyle(ChatFormatting.LIGHT_PURPLE));
		super.appendHoverText(stack, world, tooltip, context);
	}
}