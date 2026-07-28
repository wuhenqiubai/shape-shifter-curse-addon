package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.AABB;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;
import java.util.Optional;

/**
 * 绑定脚环（Binding Anklet）—— 契灵首个专属饰品。
 *
 * 槽位：复用 SSC 守御脚环的 trinkets:feet/aglet 槽（与守御脚环互斥）。
 * 装备限制：仅契灵形态可装备，其他形态拒绝。
 * 被动效果：在 16 格范围内为其他**劫掠阵营 NPC**（pillager / vindicator / evoker /
 *           illusioner / ravager / witch）提供 +20% 造成伤害加成；
 *           佩戴者本人（玩家/契灵）不享受此加成。
 * 获取途径：仅 25% 概率出现在劫掠者哨塔战利品箱中。
 *
 * 加成的伤害侧由 {@link net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity.BindingAnkletAuraMixin}
 * 在 LivingEntity#damage 入口 ModifyVariable，调用本类静态方法判定。
 */
public class BindingAnkletItem extends TrinketItem {

	/** 灵气范围（格） */
	public static final double AURA_RADIUS = 16.0D;
	/** 加成倍数 */
	public static final float DAMAGE_MULTIPLIER = 1.20F;

	public BindingAnkletItem(Properties settings) {
		super(settings);
	}

	/* ------------------------------------------------------------ */
	/*  装备限制                                                       */
	/* ------------------------------------------------------------ */

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		// 仅契灵形态可装备
		return FormUtils.isForm(entity, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
	}

	/* ------------------------------------------------------------ */
	/*  灵气加成判定（供 Mixin 调用）                                    */
	/* ------------------------------------------------------------ */

	/**
	 * 判定 attacker 是否属于劫掠阵营 NPC（不含玩家、不含玩家的召唤物 / 宠物）。
	 */
	public static boolean isRaiderFaction(LivingEntity attacker) {
		if (attacker instanceof Player) return false;
		if (attacker instanceof Raider) return true; // 含 pillager / vindicator / evoker / illusioner / ravager / witch（注：witch 在 1.20 也是 RaiderEntity 子类）
		// 兜底：直接列举（避免某些 mod 替换继承链时漏判）
		return attacker instanceof Pillager
				|| attacker instanceof Vindicator
				|| attacker instanceof Evoker
				|| attacker instanceof Illusioner
				|| attacker instanceof Witch
				|| attacker instanceof AbstractIllager;
	}

	/**
	 * 检测 attacker 周围 AURA_RADIUS 格内是否存在佩戴绑定脚环的契灵玩家。
	 * 多人环境下：必须在服务器线程调用；佩戴者自身的攻击不在此函数判定（attacker
	 * 必为劫掠 NPC，玩家本人天然不满足 {@link #isRaiderFaction}）。
	 */
	public static boolean hasAnkletAuraNearby(LivingEntity attacker) {
		Level world = attacker.level();
		if (world.isClientSide) return false;
		AABB box = attacker.getBoundingBox().inflate(AURA_RADIUS);
		// getEntitiesByClass 已自带 box 过滤，再补距离平方过滤保证球形范围
		double r2 = AURA_RADIUS * AURA_RADIUS;
		List<Player> players = world.getEntitiesOfClass(Player.class, box, p -> !p.isSpectator());
		for (Player p : players) {
			if (p.distanceToSqr(attacker) > r2) continue;
			// 必须是契灵形态（否则装备早就被拒绝，但热切换形态时双保险）
			if (!FormUtils.isForm(p, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) continue;
			if (isAnkletEquipped(p)) return true;
		}
		return false;
	}

	private static boolean isAnkletEquipped(Player player) {
		Optional<TrinketComponent> opt = TrinketsApi.getTrinketComponent(player);
		if (opt.isEmpty()) return false;
		return opt.get().isEquipped(SscAddon.BINDING_ANKLET);
	}

	/* ------------------------------------------------------------ */
	/*  战利品注入：劫掠者哨塔 25%                                       */
	/* ------------------------------------------------------------ */

	private static final ResourceLocation PILLAGER_OUTPOST_LOOT = ResourceLocation.fromNamespaceAndPath("minecraft", "chests/pillager_outpost");

	public static void registerLootTable() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!PILLAGER_OUTPOST_LOOT.equals(key.location())) return;
			LootPool.Builder pool = LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.when(LootItemRandomChanceCondition.randomChance(0.25F))
					.add(LootItem.lootTableItem(SscAddon.BINDING_ANKLET));
			tableBuilder.withPool(pool);
		});
	}

	/* ------------------------------------------------------------ */
	/*  Tooltip                                                       */
	/* ------------------------------------------------------------ */

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.binding_anklet.tooltip_1").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.ssc_addon.binding_anklet.tooltip_2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ssc_addon.binding_anklet.tooltip_3").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ssc_addon.binding_anklet.tooltip_4").withStyle(ChatFormatting.DARK_GRAY));
		super.appendHoverText(stack, context, tooltip, type);
	}
}