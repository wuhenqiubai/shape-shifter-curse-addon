package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蚀沙棱晶 - SP金沙岚专属饰品（项链槽）
 * 效果：引爆/被动爆发时，主目标承受60%伤害，40%伤害扩散给周围5格内其它目标，并给4格内目标额外叠1层烙印
 * 副作用：烙印叠加冷却从1秒变为1.3秒
 * 获取途径：沙漠神殿战利品箱，15%概率
 */
public class ErosionSandPrismItem extends TrinketItem {

	/** 服务端装备状态追踪：玩家UUID -> 最后一次tick的游戏时间 */
	private static final ConcurrentHashMap<UUID, Long> EQUIPPED_PLAYERS = new ConcurrentHashMap<>();

	public ErosionSandPrismItem(Properties settings) {
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
						.add(LootItem.lootTableItem(SscAddon.EROSION_SAND_PRISM));
				tableBuilder.withPool(poolBuilder);
			}
		});
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isGoldenSandstormSP(entity);
	}

	@Override
	public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			EQUIPPED_PLAYERS.put(player.getUUID(), entity.level().getGameTime());
		}
	}

	@Override
	public void onEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			EQUIPPED_PLAYERS.put(player.getUUID(), entity.level().getGameTime());
		}
	}

	@Override
	public void onUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		if (entity instanceof ServerPlayer) {
			EQUIPPED_PLAYERS.remove(entity.getUUID());
		}
	}

	/**
	 * 检查玩家是否装备了蚀沙棱晶（基于tick回调追踪，比isEquipped API更可靠）
	 */
	public static boolean isEquippedBy(ServerPlayer player) {
		Long lastTick = EQUIPPED_PLAYERS.get(player.getUUID());
		if (lastTick == null) return false;
		// 超过3tick未更新视为已卸下（容错）
		return Math.abs(player.level().getGameTime() - lastTick) <= 3;
	}

	/** 清理玩家数据（退出/切换形态时调用） */
	public static void clearPlayer(UUID uuid) {
		EQUIPPED_PLAYERS.remove(uuid);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.erosion_sand_prism.tooltip_1").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.ssc_addon.erosion_sand_prism.tooltip_2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ssc_addon.erosion_sand_prism.tooltip_3").withStyle(ChatFormatting.RED));
		super.appendHoverText(stack, context, tooltip, type);
	}
}