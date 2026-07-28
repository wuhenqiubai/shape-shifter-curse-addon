package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;

/**
 * 凋零药水（自定义可堆叠药水，饮用 / 喷溅 / 滞留 三型）。
 *
 * 设计（用户 2026-07-04 确认）：
 * - 任何人都能饮用 / 投掷；制作基础饮用型仍需 SP阿努比斯（item_on_item：凋零玫瑰 + 药水 → 1 瓶）。
 * - 效果：固定凋零 II（amplifier 1），20 秒。饮用型作用于自己；喷溅 / 滞留型投掷后对范围内生物 AOE。
 * - 酿造：饮用型 + 火药 → 喷溅型；喷溅型 + 龙息 → 滞留型（见 BrewingRegistryInfiniteMixin）。
 * - 瓶身带附魔光效（hasGlint）。可叠 3 瓶（maxCount 由注册处控制）。
 * - 不继承 PotionItem，避免被"药水可堆叠"类模组误伤。
 */
public class WitherPotionItem extends Item {

	/** 凋零等级（amplifier 1 = 凋零 II） */
	private static final int WITHER_AMPLIFIER = 1;
	/** 凋零时长（tick）= 20秒 */
	private static final int WITHER_DURATION = 400;
	/** 饮用读条时长，与原版药水一致（32 tick） */
	private static final int DRINK_TIME = 32;
	/** 瓶身 / AOE 着色（凋零暗褐） */
	private static final int POTION_COLOR = 0x4A403A;

	public enum Type {
		DRINK, SPLASH, LINGERING
	}

	private final Type type;

	public WitherPotionItem(Properties settings, Type type) {
		super(settings);
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	/**
	 * 按玩家形态返回凋零药水堆叠上限：使魔系 8 / SP阿努比斯 3 / 其它 1。
	 * 供 Slot.getMaxItemCount / 创造中键复制 / 双击合并 等多处统一调用。
	 */
	public static int getStackLimitFor(Player player) {
		if (player == null) {
			return 1;
		}
		if (FormUtils.isFamiliarFoxFamily(player)) {
			return 8;
		}
		if (FormUtils.isForm(player, FormIdentifiers.ANUBIS_WOLF_SP)) {
			return 3;
		}
		return 1;
	}

	/** 瓶身附魔光效。 */
	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return type == Type.DRINK ? DRINK_TIME : 0;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return type == Type.DRINK ? UseAnim.DRINK : UseAnim.NONE;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (type == Type.DRINK) {
			// 饮用型：起手读条，效果在 finishUsing 施加
			user.startUsingItem(hand);
			return InteractionResultHolder.consume(stack);
		}
		// 喷溅 / 滞留型：投掷原版药水弹射物（携带凋零效果），复用原版 AOE / 地面云
		if (!world.isClientSide) {
			spawnThrownPotion(world, user);
		}
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS,
				0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
		if (!user.getAbilities().instabuild) {
			stack.shrink(1);
		}
		user.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
		if (type != Type.DRINK || !(user instanceof Player player)) {
			return super.finishUsingItem(stack, world, user);
		}
		if (!world.isClientSide) {
			// 固定凋零 II（amplifier 1），20 秒（400t）作用于饮用者自己
			player.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER));
			world.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 0.6f, 1.0f);
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return stack;
	}

	/** 生成携带凋零效果的原版投掷药水（喷溅 / 滞留），复用原版 AOE / 地面云机制。仅服务端调用。 */
	private void spawnThrownPotion(Level world, Player user) {
		ItemStack thrown = new ItemStack(type == Type.LINGERING ? Items.LINGERING_POTION : Items.SPLASH_POTION);
		net.minecraft.world.item.alchemy.PotionContents contents = new net.minecraft.world.item.alchemy.PotionContents(
				java.util.Optional.empty(),
				java.util.Optional.of(POTION_COLOR),
				List.of(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER)));
		thrown.set(DataComponents.POTION_CONTENTS, contents);
		ThrownPotion entity = new ThrownPotion(world, user);
		entity.setItem(thrown);
		entity.shootFromRotation(user, user.getXRot(), user.getYRot(), -20.0F, 0.5F, 1.0F);
		world.addFreshEntity(entity);
	}
}