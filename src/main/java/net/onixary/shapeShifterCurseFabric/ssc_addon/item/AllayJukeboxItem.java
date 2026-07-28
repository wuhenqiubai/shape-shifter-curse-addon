package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * SP悦灵唱片机物品
 * - 用唱片充能（背包中将唱片拖到此物品上右键），每次+300充能，上限600
 * - 潜行+右键激活/关闭，激活时默认播放加速音乐
 * - 手持+右键切换模式（回血↔加速），1秒CD
 * - 激活时每秒消耗1点充能
 * - 回血模式：20格内白名单生物每5秒回1HP
 * - 加速模式：20格内白名单生物+10%移速
 */
public class AllayJukeboxItem extends Item {

	public static final int MAX_CHARGE = 600;
	public static final int CHARGE_PER_DISC = 300;
	// Mode: 0 = speed (加速), 1 = heal (回血)
	public static final int MODE_SPEED = 0;
	public static final int MODE_HEAL = 1;
	public static final int MODE_SWITCH_COOLDOWN = 20; // 1 second

	public AllayJukeboxItem(Properties settings) {
		super(settings);
	}

	// ===== Data component accessors =====

	public static int getCharge(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getInt("Charge");
	}

	public static void setCharge(ItemStack stack, int amount) {
		int clamped = Math.max(0, Math.min(amount, MAX_CHARGE));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putInt("Charge", clamped));
	}

	public static boolean isActive(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getBoolean("Active");
	}

	public static void setActive(ItemStack stack, boolean active) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putBoolean("Active", active));
	}

	public static int getMode(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getInt("Mode");
	}

	public static void setMode(ItemStack stack, int mode) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putInt("Mode", mode));
	}

	/**
	 * 尝试用唱片充能（从 ScreenHandler 的 Mixin 中调用）
	 *
	 * @return true if charging succeeded
	 */
	public static boolean tryChargeWithDisc(ItemStack jukeboxStack, ItemStack discStack) {
		if (!discStack.has(DataComponents.JUKEBOX_PLAYABLE)) return false;
		int currentCharge = getCharge(jukeboxStack);
		if (currentCharge >= MAX_CHARGE) return false;

		setCharge(jukeboxStack, Math.min(currentCharge + CHARGE_PER_DISC, MAX_CHARGE));
		discStack.shrink(1);
		return true;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);

		if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) {
			if (user.isShiftKeyDown()) {
				// 潜行+右键：切换激活状态
				boolean currentActive = isActive(stack);
				if (!currentActive) {
					// Activate - check charge
					if (getCharge(stack) <= 0) {
						serverPlayer.displayClientMessage(Component.translatable("item.ssc_addon.allay_jukebox.no_charge").withStyle(ChatFormatting.RED), true);
						return InteractionResultHolder.fail(stack);
					}
					setActive(stack, true);
					// Default mode: speed (加速)
					setMode(stack, MODE_SPEED);
					serverPlayer.displayClientMessage(Component.translatable("item.ssc_addon.allay_jukebox.activated").withStyle(ChatFormatting.GREEN), true);
					world.playSound(null, user.getX(), user.getY(), user.getZ(),
							SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
				} else {
					setActive(stack, false);
					serverPlayer.displayClientMessage(Component.translatable("item.ssc_addon.allay_jukebox.deactivated").withStyle(ChatFormatting.YELLOW), true);
					world.playSound(null, user.getX(), user.getY(), user.getZ(),
							SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
				}
				return InteractionResultHolder.success(stack);
			} else {
				// 非潜行+右键：切换模式
				if (isActive(stack)) {
					if (user.getCooldowns().isOnCooldown(this)) {
						return InteractionResultHolder.pass(stack);
					}
					int currentMode = getMode(stack);
					int newMode = currentMode == MODE_SPEED ? MODE_HEAL : MODE_SPEED;
					setMode(stack, newMode);

					String modeKey = newMode == MODE_SPEED ? "item.ssc_addon.allay_jukebox.mode_speed" : "item.ssc_addon.allay_jukebox.mode_heal";
					serverPlayer.displayClientMessage(Component.translatable(modeKey).withStyle(ChatFormatting.AQUA), true);
					world.playSound(null, user.getX(), user.getY(), user.getZ(),
							SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, newMode == MODE_SPEED ? 1.2f : 0.8f);

					user.getCooldowns().addCooldown(this, MODE_SWITCH_COOLDOWN);
					return InteractionResultHolder.success(stack);
				} else {
					serverPlayer.displayClientMessage(Component.translatable("item.ssc_addon.allay_jukebox.not_active").withStyle(ChatFormatting.GRAY), true);
				}
			}
		}

		return InteractionResultHolder.pass(stack);
	}

	// ===== Item bar (charge display like PortableFridge) =====

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0f * getCharge(stack) / MAX_CHARGE);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		if (isActive(stack)) {
			int mode = getMode(stack);
			return mode == MODE_SPEED ? 0x55FF55 : 0xFF55FF; // Green for speed, Pink for heal
		}
		return 0xAAAAFF; // Light purple when inactive
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("tooltip.ssc_addon.allay_jukebox.desc").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("tooltip.ssc_addon.allay_jukebox.charge", getCharge(stack), MAX_CHARGE).withStyle(ChatFormatting.GRAY));

		if (isActive(stack)) {
			String modeKey = getMode(stack) == MODE_SPEED ? "tooltip.ssc_addon.allay_jukebox.mode_speed" : "tooltip.ssc_addon.allay_jukebox.mode_heal";
			tooltip.add(Component.translatable("tooltip.ssc_addon.allay_jukebox.active").withStyle(ChatFormatting.GREEN));
			tooltip.add(Component.translatable(modeKey).withStyle(ChatFormatting.YELLOW));
		} else {
			tooltip.add(Component.translatable("tooltip.ssc_addon.allay_jukebox.inactive").withStyle(ChatFormatting.DARK_GRAY));
		}

		tooltip.add(Component.translatable("tooltip.ssc_addon.allay_jukebox.usage").withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, tooltip, type);
	}
}