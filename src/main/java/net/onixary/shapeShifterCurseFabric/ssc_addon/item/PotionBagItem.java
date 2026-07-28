package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.screen.PotionBagScreenHandler;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;

import java.util.List;

public class PotionBagItem extends Item {

	/** 快捷投放栏槽位索引（药水包最左侧槽位）。 */
	private static final int QUICK_SLOT = 0;
	/**
	 * 普通投掷药水的投掷间隔冷却：5 秒（100 tick）。记录在药水袋 NBT（{@link #NBT_THROW_END}），
	 * 不直接占用 ItemCooldownManager —— 白色遮罩统一由 {@link #inventoryTick} 按「快捷栏药水自身剩余冷却」
	 * 同步，使遮罩长度与所投药水一致（普通药水=投掷间隔；无限药水=各形态充能时长），换药水时自动重置。
	 */
	private static final int THROW_COOLDOWN = 100;
	/** 饮用型药水的饮用读条时长：比原版直接喝（32 tick）长 15% ≈ 37 tick。 */
	private static final int DRINK_TIME = 37;
	/** 药水袋 NBT：普通投掷药水的投掷冷却结束世界时间（game time）。 */
	private static final String NBT_THROW_END = "ThrowEndTime";
	/** 药水袋 NBT：冷却遮罩同步令牌（= 当前快捷栏药水冷却结束的世界时间，0 表示无冷却）。 */
	private static final String NBT_CD_TOKEN = "CdToken";

	public PotionBagItem(Properties settings) {
		super(settings.stacksTo(1));
	}

	/** 是否为无限压缩能量药水（任意形态）。 */

	private static boolean isInfinite(ItemStack stack) {
		return stack.getItem() instanceof InfiniteEnergyPotionItem;
	}

	/** 投掷型（溅射/滞留），含无限药水的喷溅/滞留形态。 */
	private static boolean isThrowable(ItemStack stack) {
		if (stack.getItem() instanceof SplashPotionItem || stack.getItem() instanceof LingeringPotionItem) {
			return true;
		}
		return stack.getItem() instanceof InfiniteEnergyPotionItem inf
				&& inf.getType() != InfiniteEnergyPotionItem.Type.DRINK;
	}

	/** 饮用型（普通药水或无限药水的饮用形态，排除溅射/滞留）。 */
	private static boolean isDrinkable(ItemStack stack) {
		if (stack.getItem() instanceof InfiniteEnergyPotionItem inf) {
			return inf.getType() == InfiniteEnergyPotionItem.Type.DRINK;
		}
		return !stack.isEmpty() && stack.getItem() instanceof PotionItem && !isThrowable(stack);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);

		// 潜行 + 右键：打开药水包 GUI
		if (user.isShiftKeyDown()) {
			if (!world.isClientSide) {
				user.openMenu(new MenuProvider() {
					@Override
					public Component getDisplayName() {
						return Component.translatable("item.ssc_addon.potion_bag");
					}

					@Override
					public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
						return new PotionBagScreenHandler(syncId, inv, stack);
					}
				});
			}
			return InteractionResultHolder.success(stack);
		}

		// 普通右键：快捷投放栏（最左侧槽位）快速使用一瓶药水
		ItemStack potion = ItemStack.EMPTY;
		if (stack.getItem() instanceof PotionBagItem) {
			potion = PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT, net.minecraft.core.RegistryAccess.EMPTY);
		}
		if (potion.isEmpty()) {
			// 快捷栏已空，无法继续使用（不摆手、不消耗）
			return InteractionResultHolder.pass(stack);
		}

		if (isThrowable(potion)) {
			if (isInfinite(potion)) {
				// 无限药水：仅由自身空瓶充能门控；白色遮罩由 inventoryTick 按其充能时长同步
				if (InfiniteEnergyPotionItem.isRecharging(potion, world)) {
					return InteractionResultHolder.fail(stack);
				}
				if (!world.isClientSide) {
					throwPotion(world, user, stack, potion); // 内部 markUsed → 写 FullAtTime
				}
				return InteractionResultHolder.success(stack);
			}
			// 普通投掷药水：药水袋自身投掷间隔冷却（记录在药水袋 NBT，双端一致）
			if (isThrowCoolingDown(stack, world)) {
				return InteractionResultHolder.fail(stack);
			}
			if (!world.isClientSide) {
				throwPotion(world, user, stack, potion);
			}
			// 双端记录投掷冷却结束时间（world.getTime 双端同步）；白色遮罩由 inventoryTick 同步
			CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putLong(NBT_THROW_END, world.getGameTime() + THROW_COOLDOWN));
			return InteractionResultHolder.success(stack);
		}

		// 饮用型：无限药水空瓶充能中则不可饮用
		if (isInfinite(potion) && InfiniteEnergyPotionItem.isRecharging(potion, world)) {
			return InteractionResultHolder.fail(stack);
		}
		// 开始饮用读条（DRINK 动作 + getMaxUseTime），无冷却
		user.startUsingItem(hand);
		return InteractionResultHolder.consume(stack);
	}

	/**
	 * 饮用读条完成：施加药水效果并消耗 1 瓶，返还空玻璃瓶；药水袋本身不被消耗。
	 * 仅服务端结算，多人主客机一致。
	 */
	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
		if (!world.isClientSide && user instanceof Player player) {
			ItemStack potion = PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT, world.registryAccess());
			// 无限压缩能量药水（饮用型）：施加 feed_effect，标记空瓶充能，不消耗数量、不返还玻璃瓶
			if (potion.getItem() instanceof InfiniteEnergyPotionItem inf
					&& inf.getType() == InfiniteEnergyPotionItem.Type.DRINK) {
				if (!InfiniteEnergyPotionItem.isRecharging(potion, world)) {
					RegOtherStatusEffects.FEED_EFFECT.applyInstantenousEffect(player, player, player, 0, 1.0);
					inf.markUsed(potion, world);
					PotionBagScreenHandler.setStoredStack(stack, QUICK_SLOT, potion, world.registryAccess());
				}
			} else if (isDrinkable(potion)) {
				// 施加药水效果（瞬时效果立即结算，持续效果加为状态）
				PotionContents pContents = potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
				for (MobEffectInstance effect : pContents.getAllEffects()) {
					if (effect.getEffect().value().isInstantenous()) {
						effect.getEffect().value().applyInstantenousEffect(player, player, player, effect.getAmplifier(), 1.0);
					} else {
						player.addEffect(new MobEffectInstance(effect));
					}
				}
				// 正常饮用返还空玻璃瓶（创造模式不返还；背包满则掉落）
				if (!player.getAbilities().instabuild) {
					ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
					if (!player.getInventory().add(bottle)) {
						player.drop(bottle, false);
					}
				}
				// 消耗 1 瓶并写回药水包存储
				potion.shrink(1);
				PotionBagScreenHandler.setStoredStack(stack, QUICK_SLOT, potion, world.registryAccess());
			}
		}
		return stack; // 药水袋本身不被消耗
	}

	/** 饮用读条时长：仅当快捷栏为饮用型药水时返回 {@link #DRINK_TIME}，否则 0（投掷型/空为即时/无动作）。 */
	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return isDrinkable(PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT, net.minecraft.core.RegistryAccess.EMPTY)) ? DRINK_TIME : 0;
	}

	/** 饮用型药水显示喝药动作（含原版饮用粒子与音效），否则无动作。 */
	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return isDrinkable(PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT, net.minecraft.core.RegistryAccess.EMPTY)) ? UseAnim.DRINK : UseAnim.NONE;
	}

	/**
	 * 投掷一瓶药水：生成投掷药水弹射物（setItem 让弹射物渲染使用对应药水的纹理），
	 * 并消耗 1 瓶写回药水包存储。仅服务端调用。
	 */
	private void throwPotion(Level world, Player user, ItemStack bagStack, ItemStack potion) {
		// 无限压缩能量药水（喷溅/滞留型）：复用其自身投掷逻辑，标记空瓶充能而非消耗数量
		if (potion.getItem() instanceof InfiniteEnergyPotionItem inf) {
			InfiniteEnergyPotionItem.playThrowSound(world, user);
			inf.spawnThrownPotion(world, user);
			inf.markUsed(potion, world);
			PotionBagScreenHandler.setStoredStack(bagStack, QUICK_SLOT, potion, world.registryAccess());
			return;
		}
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS,
				0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
		ThrownPotion potionEntity = new ThrownPotion(world, user);
		potionEntity.setItem(potion.copyWithCount(1));
		potionEntity.shootFromRotation(user, user.getXRot(), user.getYRot(), -20.0F, 0.5F, 1.0F);
		world.addFreshEntity(potionEntity);
		potion.shrink(1);
		PotionBagScreenHandler.setStoredStack(bagStack, QUICK_SLOT, potion, world.registryAccess());
	}

	/** 普通投掷药水是否仍在投掷间隔冷却中（基于药水袋 NBT 记录的结束世界时间）。 */
	private static boolean isThrowCoolingDown(ItemStack bag, Level world) {
		CustomData nbt = bag.get(DataComponents.CUSTOM_DATA);
		return nbt != null && nbt.contains(NBT_THROW_END) && world.getGameTime() < nbt.getUnsafe().getLong(NBT_THROW_END);
	}

	/**
	 * 每 tick 把「快捷栏槽位药水自身的剩余冷却」同步到药水袋的 ItemCooldownManager 白色遮罩：
	 * <ul>
	 *   <li>无限药水 → 其空瓶充能结束时间（各形态时长不同：饮用 10s / 喷溅 15s / 滞留 20s）</li>
	 *   <li>普通投掷药水 → 药水袋记录的投掷间隔冷却结束时间（5s）</li>
	 *   <li>其它（普通可饮用药水/空）→ 无冷却</li>
	 * </ul>
	 * 以「冷却结束的世界时间」作为同步令牌（{@link #NBT_CD_TOKEN}），仅当令牌变化（换药水 / 再次使用）时
	 * 才重新 set/remove 遮罩，避免每 tick 重置导致遮罩卡满。仅服务端执行，set 会自动同步到客户端遮罩。
	 */
	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
		if (!world.isClientSide && entity instanceof Player player) {
			ItemStack potion = PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT, world.registryAccess());
			long time = world.getGameTime();
			long endTime = 0L;
			if (potion.getItem() instanceof InfiniteEnergyPotionItem) {
				endTime = InfiniteEnergyPotionItem.getRechargeEndTime(potion);
			} else if (isThrowable(potion)) {
				CustomData nbt = stack.get(DataComponents.CUSTOM_DATA);
				if (nbt != null && nbt.contains(NBT_THROW_END)) {
					endTime = nbt.getUnsafe().getLong(NBT_THROW_END);
				}
			}
			long token = endTime > time ? endTime : 0L; // 已过期视为无冷却
			long lastToken = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getLong(NBT_CD_TOKEN);
			if (token != lastToken) {
				CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putLong(NBT_CD_TOKEN, token));
				if (token > 0L) {
					player.getCooldowns().addCooldown(this, (int) (token - time));
				} else {
					player.getCooldowns().removeCooldown(this);
				}
			}
		}
		super.inventoryTick(stack, world, entity, slot, selected);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.potion_bag.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.ssc_addon.potion_bag.tooltip.controls").withStyle(ChatFormatting.DARK_GRAY));
		super.appendHoverText(stack, context, tooltip, type);
	}

	/**
	 * 物品名实时反映快捷投放栏（槽位 0）的药水：空时显示「药水包（空）」，有药水时显示「药水包（药水名）」。
	 * 名称取自快捷栏药水自身的 {@link ItemStack#getHoverName()}（普通药水含药效后缀，无限药水为其本地名）。
	 */
	@Override
	public Component getName(ItemStack stack) {
		ItemStack potion = ItemStack.EMPTY;
		if (stack.getItem() instanceof PotionBagItem) {
			potion = PotionBagScreenHandler.getStoredStack(stack, QUICK_SLOT, net.minecraft.core.RegistryAccess.EMPTY);
		}
		if (potion.isEmpty()) {
			return Component.translatable("item.ssc_addon.potion_bag.empty");
		}
		return Component.translatable("item.ssc_addon.potion_bag.named", potion.getHoverName());
	}

	// ====== 快捷收纳：在其它界面把药水直接放入药水袋（交互逻辑与原版收纳袋一致，优先放入非快捷消耗栏） ======

	/** 药水袋槽位总数（与 {@link PotionBagScreenHandler} 一致：槽位 0 为快捷投放栏，1-8 为非快捷消耗栏）。 */
	private static final int BAG_SLOTS = 9;

	/** 是否为可放入药水袋的药水类物品（与药水包 GUI 的可放入规则一致）。 */
	public static boolean isStorable(ItemStack stack) {
		return stack.getItem() instanceof PotionItem
				|| stack.getItem() instanceof SplashPotionItem
				|| stack.getItem() instanceof LingeringPotionItem
				|| stack.getItem() instanceof InfiniteEnergyPotionItem;
	}

	/** 单格最大堆叠：无限能量药水每格 1，其余药水每格 8（与药水包 GUI 槽位规则一致）。 */
	private static int maxPerSlot(ItemStack stack) {
		return stack.getItem() instanceof InfiniteEnergyPotionItem ? 1 : 8;
	}

	/**
	 * 把 {@code source} 中的药水尽量放进药水袋并扣减其数量，返回实际放入的数量。
	 * 放置优先级：先填非快捷消耗栏（槽位 1-8），快捷投放栏（槽位 0）仅在前者放不下时兜底；
	 * 各区间内均先合并到同类未满堆叠，再占用空槽位。堆叠规则与药水包 GUI 一致（普通药水每格 8、无限能量药水每格 1）。
	 */
	public static int insertIntoBag(ItemStack bag, ItemStack source, HolderLookup.Provider registries) {
		if (source.isEmpty() || !isStorable(source)) {
			return 0;
		}
		int perSlot = maxPerSlot(source);
		ItemStack[] slots = new ItemStack[BAG_SLOTS];
		for (int i = 0; i < BAG_SLOTS; ++i) {
			slots[i] = PotionBagScreenHandler.getStoredStack(bag, i, registries);
		}
		// 优先非快捷消耗栏（槽位 1-8），快捷投放栏（槽位 0）兜底
		int inserted = fillRange(slots, source, perSlot, 1, BAG_SLOTS, registries);
		inserted += fillRange(slots, source, perSlot, QUICK_SLOT, QUICK_SLOT + 1, registries);
		if (inserted > 0) {
			for (int i = 0; i < BAG_SLOTS; ++i) {
				PotionBagScreenHandler.setStoredStack(bag, i, slots[i], registries);
			}
		}
		return inserted;
	}

	/** 在 {@code [from, to)} 槽位区间内放入药水：先合并到同类未满堆叠、再占用空槽位；扣减 {@code source} 并返回放入数量。 */
	private static int fillRange(ItemStack[] slots, ItemStack source, int perSlot, int from, int to, HolderLookup.Provider registries) {
		int inserted = 0;
		for (int i = from; i < to && !source.isEmpty(); ++i) {
			ItemStack slot = slots[i];
			if (!slot.isEmpty() && slot.getCount() < perSlot && ItemStack.isSameItemSameComponents(slot, source)) {
				int add = Math.min(perSlot - slot.getCount(), source.getCount());
				slot.grow(add);
				source.shrink(add);
				inserted += add;
			}
		}
		for (int i = from; i < to && !source.isEmpty(); ++i) {
			if (slots[i].isEmpty()) {
				int add = Math.min(perSlot, source.getCount());
				slots[i] = source.copyWithCount(add);
				source.shrink(add);
				inserted += add;
			}
		}
		return inserted;
	}
}