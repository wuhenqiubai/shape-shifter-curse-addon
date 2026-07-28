package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPassive;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

/**
 * SSCA 各形态交互事件注册（从 SscAddon.registerMancianimaEvents 拆分而来）：
 * - 契灵：村民/商人击杀掉落、唤魔者+下界之星换图腾、敲钟触发袭击
 * - 吸血蝙蝠血雾期间禁用一切左右键交互
 * - 进化美西螈：副手持箭右键“合成”水矛
 * - SP阿努比斯：手持凋零玫瑰右键改为进食
 */
public final class SscAddonInteractionEvents {

	private SscAddonInteractionEvents() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// 1. 袭击目标死亡检测
			MancianimaPassive.onAssaultTargetDeath(entity);
			// 2. 村民/商人 被契灵击杀 → 掉落
			if (entity instanceof AbstractVillager merchant
					&& source.getEntity() instanceof ServerPlayer killer
					&& FormUtils.isForm(killer,
							FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				MancianimaPassive
						.onMerchantKilledByMancianima(merchant, killer);
			}
		});

		// 唤魔者 + 下界之星 → 2 个不死图腾
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide()) return InteractionResult.PASS;
			if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (!FormUtils.isForm(sp,
					FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				return InteractionResult.PASS;
			}
			// 契灵不能与村民/商人交易
			if (entity instanceof AbstractVillager) {
				return InteractionResult.FAIL;
			}
			if (!(entity instanceof Evoker)) return InteractionResult.PASS;
			ItemStack stack = sp.getItemInHand(hand);
			if (!stack.is(Items.NETHER_STAR)) return InteractionResult.PASS;
			if (!sp.getAbilities().instabuild) stack.shrink(1);
			ItemStack reward = new ItemStack(Items.TOTEM_OF_UNDYING, 2);
			if (!sp.getInventory().add(reward)) {
				sp.drop(reward, false);
			}
			if (sp.level() instanceof ServerLevel sw) {
				sw.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
						SoundEvents.EVOKER_PREPARE_SUMMON,
						sp.getSoundSource(), 1.0f, 1.2f);
			}
			return InteractionResult.SUCCESS;
		});

		// 契灵敲钟触发村庄袭击（1 MC 天 1 次）
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide()) return InteractionResult.PASS;
			if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (!FormUtils.isForm(sp,
					FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
				return InteractionResult.PASS;
			}
			BlockState state = world.getBlockState(hitResult.getBlockPos());
			if (!(state.getBlock() instanceof BellBlock)) return InteractionResult.PASS;
			MancianimaPassive.tryTriggerAssaultByBell(sp);
			// 让钟声照常播放
			return InteractionResult.PASS;
		});

		// 吸血蝙蝠血雾期间禁用一切右键交互（用物品/放方块/与生物互动/吃喝/盾牌副手等）
		UseItemCallback.EVENT.register((player, world, hand) -> {			if (player.hasEffect(SscAddon.MIST_FORM_ENTRY)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return InteractionResultHolder.fail(player.getItemInHand(hand));
			}			return InteractionResultHolder.pass(player.getItemInHand(hand));
		});

		SscAddon.WS_DBG.info("[WS] ===== DEBUG BUILD LOADED (v2): 水矛合成+最多1把 监测启用 =====");
		// SP美西螈：选中快捷栏(主手)为空 + 副手持箭 + 右键 → 消耗 1 支箭“合成”获得水矛（5 秒CD；身上最多 1 把）
		// 注：主手为空时 MC 只触发副手(OFF_HAND)交互，故用副手回调
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
			boolean axo = net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isAxolotlSP(player);
			net.minecraft.world.item.ItemStack mainStack = player.getMainHandItem();
			net.minecraft.world.item.ItemStack offStack = player.getOffhandItem();
			boolean arrowCd = player.getCooldowns().isOnCooldown(net.minecraft.world.item.Items.ARROW);
			int spearCount = 0;
			if (axo) {
				net.minecraft.world.entity.player.Inventory inv = player.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (inv.getItem(i).is(SscAddon.WATER_SPEAR)) spearCount++;
				}
				SscAddon.WS_DBG.info("[WS] side={} hand={} main={} mainEmpty={} off={} offIsArrow={} arrowCD={} spearInInv={}",
						world.isClientSide() ? "CLIENT" : "SERVER", hand,
						net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mainStack.getItem()), mainStack.isEmpty(),
						net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offStack.getItem()), offStack.is(net.minecraft.world.item.Items.ARROW), arrowCd, spearCount);
			}
			if (hand != net.minecraft.world.InteractionHand.OFF_HAND || !axo || !mainStack.isEmpty()
					|| !offStack.is(net.minecraft.world.item.Items.ARROW)) {
				return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
			}
			// 身上最多一把水矛：背包已有则不合成
			if (spearCount > 0) {
				SscAddon.WS_DBG.info("[WS][{}] BLOCKED: already has {} water_spear (max 1)", world.isClientSide() ? "CLIENT" : "SERVER", spearCount);
				return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
			}
			if (world.isClientSide()) {
				SscAddon.WS_DBG.info("[WS][CLIENT] gate-passed arrowCD={} -> {}", arrowCd, arrowCd ? "PASS(cooling)" : "SUCCESS");
				return arrowCd ? net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand))
						: net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
			}
			if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
				int slot = sp.getInventory().selected;
				net.minecraft.world.item.ItemStack selStack = sp.getInventory().getItem(slot);
				int srvSpears = 0;
				for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
					if (sp.getInventory().getItem(i).is(SscAddon.WATER_SPEAR)) srvSpears++;
				}
				long now = sp.getServer().getTickCount();
				Long until = SscAddon.WATER_SPEAR_CRAFT_CD.get(sp.getUUID());
				boolean cooling = until != null && now < until;
				SscAddon.WS_DBG.info("[WS][SERVER] gate cooling={} now={} until={} selSlot={} selStack={} selEmpty={} srvSpears={}",
						cooling, now, until, slot,
						net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(selStack.getItem()), selStack.isEmpty(), srvSpears);
				if (cooling) {
					return net.minecraft.world.InteractionResultHolder.pass(sp.getItemInHand(hand));
				}
				// 服务端二次硬校验（防御）：选中槽必须真空、且身上无水矛
				if (!selStack.isEmpty()) {
					SscAddon.WS_DBG.warn("[WS][SERVER] ABORT: 选中槽非空({})，不合成", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(selStack.getItem()));
					return net.minecraft.world.InteractionResultHolder.pass(sp.getItemInHand(hand));
				}
				if (srvSpears > 0) {
					SscAddon.WS_DBG.warn("[WS][SERVER] ABORT: 身上已有 {} 把水矛", srvSpears);
					return net.minecraft.world.InteractionResultHolder.pass(sp.getItemInHand(hand));
				}
				sp.getOffhandItem().shrink(1);
				net.minecraft.world.item.ItemStack spear = new net.minecraft.world.item.ItemStack(SscAddon.WATER_SPEAR);
				sp.getInventory().setItem(slot, spear);
				sp.getInventory().setChanged();
				SscAddon.WS_DBG.info("[WS][SERVER] >>> CRAFTED into selSlot={} ; offhandEmptyNow={} (CD改为水矛消失后触发)", slot, sp.getOffhandItem().isEmpty());
				if (sp.level() instanceof net.minecraft.server.level.ServerLevel sw) {
					sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
							net.minecraft.sounds.SoundEvents.BOTTLE_FILL, sp.getSoundSource(), 0.8f, 1.0f);
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnWaterBurst(sw, sp.getX(), sp.getY() + 1.0, sp.getZ(), 0.5);
				}
				return net.minecraft.world.InteractionResultHolder.success(sp.getItemInHand(hand));
			}
			return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
		});
		// SP阿努比斯吃凋零玫瑰：进食效果由 custom_edible(form_anubis_wolf_sp_eat_wither_rose) + eatFood mixin 处理。
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			// SP阿努比斯手持凋零玫瑰看向方块右键时，原版会放置花；返回 FAIL 取消放置，
			// 让交互落到 use() → 由 custom_edible(form_anubis_wolf_sp_eat_wither_rose) 驱动进食(32t 读条，同吃牛排)。
			// 看向空气时不经过 UseBlockCallback，use() 直接进食，无需在此处理。
			if (hand == InteractionHand.MAIN_HAND
					&& player.getMainHandItem().is(Items.WITHER_ROSE)
					&& FormUtils.isForm(player,
							FormIdentifiers.ANUBIS_WOLF_SP)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player.hasEffect(SscAddon.MIST_FORM_ENTRY)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player.hasEffect(SscAddon.MIST_FORM_ENTRY)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		// 同时禁用左键破坏方块
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.hasEffect(SscAddon.MIST_FORM_ENTRY)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		// 禁用左键攻击实体（含挥剑/普攻起手动作本身）
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player.hasEffect(SscAddon.MIST_FORM_ENTRY)
					&& FormUtils.isForm(player,
							FormIdentifiers.BAT_DESMODUS)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}
}