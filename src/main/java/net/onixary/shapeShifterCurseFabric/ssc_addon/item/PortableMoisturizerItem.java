package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;

public class PortableMoisturizerItem extends Item {

	public static final int MAX_CHARGE = 5400;

	public PortableMoisturizerItem(Properties settings) {
		super(settings);
	}

	// Used by Recipe to set full charge
	public static void setFullCharge(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> {
			nbt.putInt("Charge", MAX_CHARGE);
		});
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (stack == null) {
			return InteractionResultHolder.fail(stack);
		}

		boolean isActive = isActive(stack);
		boolean newState = !isActive;
		setActive(stack, !isActive);

		boolean isValidForm = FormUtils.isMoistureDependent(player);

		if (newState && !isValidForm) {
			player.displayClientMessage(Component.translatable("message.ssc_addon.moisturizer.off"), true);
		} else {
			player.displayClientMessage(Component.translatable(newState ?
					"message.ssc_addon.moisturizer.on" :
					"message.ssc_addon.moisturizer.off"), true);
		}

		return InteractionResultHolder.success(stack);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
		if (world.isClientSide || !(entity instanceof Player player)) return;
		humidifyLogic(stack, world, player);
	}

	private void humidifyLogic(ItemStack stack, Level world, Player player) {
		if (!FormUtils.isMoistureDependent(player)) {
			if (isActive(stack)) {
				setActive(stack, false);
			}
			return;
		}

		if (isActive(stack)) {
			int currentCharge = getCharge(stack);

			if (currentCharge > 0) {
				if (world.getGameTime() % 20 == 0) {
					setCharge(stack, currentCharge - 1);

					int maxAir = player.getMaxAirSupply();
					int currentAir = player.getAirSupply();
					int recoveryAmount = (int) Math.ceil(maxAir * 0.02);

					if (currentAir < maxAir) {
						player.setAirSupply(Math.min(currentAir + recoveryAmount, maxAir));
					}
				}
			} else {
				setActive(stack, false);
				player.displayClientMessage(Component.translatable("message.ssc_addon.moisturizer.empty"), true);
			}
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		// Status
		boolean active = isActive(stack);
		tooltip.add(Component.translatable("tooltip.ssc_addon.moisturizer.status")
				.append(Component.translatable(active ? "options.on" : "options.off").withStyle(active ? ChatFormatting.GREEN : ChatFormatting.RED)));

		// Charge
		int charge = getCharge(stack);
		// 1 charge = 1 second
		int minutes = charge / 60;
		int seconds = charge % 60;

		// Calculate max time string dynamically
		int maxTotalSeconds = MAX_CHARGE;
		int maxMinutes = maxTotalSeconds / 60;
		int maxSeconds = maxTotalSeconds % 60;
		String maxTimeString = String.format("%02d:%02d", maxMinutes, maxSeconds);

		tooltip.add(Component.translatable("tooltip.ssc_addon.moisturizer.charge",
				String.format("%02d:%02d", minutes, seconds),
				maxTimeString).withStyle(ChatFormatting.AQUA));

		// Instructions
		tooltip.add(Component.translatable("tooltip.ssc_addon.moisturizer.usage").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.ssc_addon.moisturizer.refill").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("tooltip.ssc_addon.moisturizer.exclusive").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return isActive(stack);
	}

	// NBT Helpers
	private boolean isActive(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getBoolean("Active");
	}

	private void setActive(ItemStack stack, boolean active) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putBoolean("Active", active));
	}

	private int getCharge(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getInt("Charge");
	}

	private void setCharge(ItemStack stack, int charge) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putInt("Charge", Math.max(0, Math.min(charge, MAX_CHARGE))));
	}
}