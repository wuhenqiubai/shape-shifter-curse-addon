package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.onixary.shapeShifterCurseFabric.additional_power.VirtualTotemPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;
import java.util.Optional;

/**
 * 安卡纹石 - SP阿努比斯之狼专属饰品（戒指槽）
 * 复活被动触发时：消除凋零和虚弱效果、减少80%冷却、物品消耗并播放碎裂音效
 */
public class AnkhStoneItem extends TrinketItem {

	public AnkhStoneItem(Properties settings) {
		super(settings);
	}

	/**
	 * 复活触发后的安卡纹石效果处理，由 AnkhStoneTotemMixin 调用
	 */
	public static void onRevival(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) return;
		if (!FormUtils.isAnubisWolfSP(player)) return;

		// 检查是否装备了安卡纹石
		Optional<TrinketComponent> trinketOpt = TrinketsApi.getTrinketComponent(player);
		if (trinketOpt.isEmpty()) return;
		TrinketComponent component = trinketOpt.get();
		if (!component.isEquipped(SscAddon.ANKH_STONE)) return;

		// 消除凋零和虚弱效果（保留火焰抗性）
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.WEAKNESS);

		// 减少 VirtualTotemPower 冷却 80%
		List<VirtualTotemPower> powers = PowerHolderComponent.getPowers(player, VirtualTotemPower.class);
		for (VirtualTotemPower power : powers) {
			int remaining = power.getRemainingTicks();
			int reduction = (int) (remaining * 0.8);
			power.modify(-reduction);
			PowerHolderComponent.syncPower(player, power.getType());
		}

		// 消耗安卡纹石（只消耗第一个）
		component.getEquipped(SscAddon.ANKH_STONE).stream().findFirst().ifPresent(pair -> pair.getB().shrink(1));

		// 播放物品碎裂音效
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_BREAK, player.getSoundSource(), 1.0f, 1.0f);
	}

	/**
	 * 注册安卡纹石到沙漠神殿战利品表（15%概率，1-2个）
	 */
	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (key.location().equals(ResourceLocation.fromNamespaceAndPath("minecraft", "chests/desert_pyramid"))) {
				LootPool.Builder poolBuilder = LootPool.lootPool()
						.setRolls(UniformGenerator.between(1.0f, 2.0f))
						.when(LootItemRandomChanceCondition.randomChance(0.15f))
						.add(LootItem.lootTableItem(SscAddon.ANKH_STONE));
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
		tooltip.add(Component.translatable("item.ssc_addon.ankh_stone.tooltip_1").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.ssc_addon.ankh_stone.tooltip_2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ssc_addon.ankh_stone.tooltip_3").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ssc_addon.ankh_stone.tooltip_4").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ssc_addon.ankh_stone.tooltip_5").withStyle(ChatFormatting.RED));
		super.appendHoverText(stack, context, tooltip, type);
	}
}