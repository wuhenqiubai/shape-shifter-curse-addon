package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

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

	public AllayJukeboxItem(Settings settings) {
		super(settings);
	}

	// ===== Data component accessors =====

	public static int getCharge(ItemStack stack) {
		return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).getNbt().getInt("Charge");
	}

	public static void setCharge(ItemStack stack, int amount) {
		int clamped = Math.max(0, Math.min(amount, MAX_CHARGE));
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putInt("Charge", clamped));
	}

	public static boolean isActive(ItemStack stack) {
		return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).getNbt().getBoolean("Active");
	}

	public static void setActive(ItemStack stack, boolean active) {
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putBoolean("Active", active));
	}

	public static int getMode(ItemStack stack) {
		return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).getNbt().getInt("Mode");
	}

	public static void setMode(ItemStack stack, int mode) {
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putInt("Mode", mode));
	}

	/**
	 * 尝试用唱片充能（从 ScreenHandler 的 Mixin 中调用）
	 *
	 * @return true if charging succeeded
	 */
	public static boolean tryChargeWithDisc(ItemStack jukeboxStack, ItemStack discStack) {
		if (!discStack.contains(DataComponentTypes.JUKEBOX_PLAYABLE)) return false;
		int currentCharge = getCharge(jukeboxStack);
		if (currentCharge >= MAX_CHARGE) return false;

		setCharge(jukeboxStack, Math.min(currentCharge + CHARGE_PER_DISC, MAX_CHARGE));
		discStack.decrement(1);
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
			if (user.isSneaking()) {
				// 潜行+右键：切换激活状态
				boolean currentActive = isActive(stack);
				if (!currentActive) {
					// Activate - check charge
					if (getCharge(stack) <= 0) {
						serverPlayer.sendMessage(Text.translatable("item.ssc_addon.allay_jukebox.no_charge").formatted(Formatting.RED), true);
						return TypedActionResult.fail(stack);
					}
					setActive(stack, true);
					// Default mode: speed (加速)
					setMode(stack, MODE_SPEED);
					serverPlayer.sendMessage(Text.translatable("item.ssc_addon.allay_jukebox.activated").formatted(Formatting.GREEN), true);
					world.playSound(null, user.getX(), user.getY(), user.getZ(),
							SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
				} else {
					setActive(stack, false);
					serverPlayer.sendMessage(Text.translatable("item.ssc_addon.allay_jukebox.deactivated").formatted(Formatting.YELLOW), true);
					world.playSound(null, user.getX(), user.getY(), user.getZ(),
							SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.8f);
				}
				return TypedActionResult.success(stack);
			} else {
				// 非潜行+右键：切换模式
				if (isActive(stack)) {
					if (user.getItemCooldownManager().isCoolingDown(this)) {
						return TypedActionResult.pass(stack);
					}
					int currentMode = getMode(stack);
					int newMode = currentMode == MODE_SPEED ? MODE_HEAL : MODE_SPEED;
					setMode(stack, newMode);

					String modeKey = newMode == MODE_SPEED ? "item.ssc_addon.allay_jukebox.mode_speed" : "item.ssc_addon.allay_jukebox.mode_heal";
					serverPlayer.sendMessage(Text.translatable(modeKey).formatted(Formatting.AQUA), true);
					world.playSound(null, user.getX(), user.getY(), user.getZ(),
							SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, newMode == MODE_SPEED ? 1.2f : 0.8f);

					user.getItemCooldownManager().set(this, MODE_SWITCH_COOLDOWN);
					return TypedActionResult.success(stack);
				} else {
					serverPlayer.sendMessage(Text.translatable("item.ssc_addon.allay_jukebox.not_active").formatted(Formatting.GRAY), true);
				}
			}
		}

		return TypedActionResult.pass(stack);
	}

	// ===== Item bar (charge display like PortableFridge) =====

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		return Math.round(13.0f * getCharge(stack) / MAX_CHARGE);
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		if (isActive(stack)) {
			int mode = getMode(stack);
			return mode == MODE_SPEED ? 0x55FF55 : 0xFF55FF; // Green for speed, Pink for heal
		}
		return 0xAAAAFF; // Light purple when inactive
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("tooltip.ssc_addon.allay_jukebox.desc").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.ssc_addon.allay_jukebox.charge", getCharge(stack), MAX_CHARGE).formatted(Formatting.GRAY));

		if (isActive(stack)) {
			String modeKey = getMode(stack) == MODE_SPEED ? "tooltip.ssc_addon.allay_jukebox.mode_speed" : "tooltip.ssc_addon.allay_jukebox.mode_heal";
			tooltip.add(Text.translatable("tooltip.ssc_addon.allay_jukebox.active").formatted(Formatting.GREEN));
			tooltip.add(Text.translatable(modeKey).formatted(Formatting.YELLOW));
		} else {
			tooltip.add(Text.translatable("tooltip.ssc_addon.allay_jukebox.inactive").formatted(Formatting.DARK_GRAY));
		}

		tooltip.add(Text.translatable("tooltip.ssc_addon.allay_jukebox.usage").formatted(Formatting.GOLD));
		super.appendTooltip(stack, context, tooltip, type);
	}
}