package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

public class AllaySPPortableBeacon {

    private static final ResourceLocation BEACON_ACTIVE_ID = ResourceLocation.fromNamespaceAndPath("my_addon", "form_allay_sp_beacon_active");

	private AllaySPPortableBeacon() {
		// Utility class
	}

	public static void init() {
		UseItemCallback.EVENT.register(AllaySPPortableBeacon::onUseItem);
	}

	private static InteractionResultHolder<ItemStack> onUseItem(Player player, Level world, InteractionHand hand) {
		if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
			ItemStack stack = player.getItemInHand(hand);
			// Toggle logic:
			// If sneaking -> Do nothing (let vanilla behavior happen, i.e., place block)
			// If not sneaking -> Toggle activation (and consume item use)
			if (stack.is(Items.BEACON) && !player.isShiftKeyDown() && isSpAllay(serverPlayer)) {
				toggleBeacon(serverPlayer);
				return InteractionResultHolder.success(stack); // Consume the action so block is not placed
			}
		}
		// Pass to allow vanilla behavior (or other mods)
		return InteractionResultHolder.pass(player.getItemInHand(hand));
	}

	private static boolean isSpAllay(ServerPlayer player) {
		return PowerUtils.isSpAllay(player);
	}

	private static boolean isBeaconActive(ServerPlayer player) {
		return PowerUtils.getResourceValue(player, BEACON_ACTIVE_ID) == 1;
	}

	public static void toggleBeacon(ServerPlayer player) {
		if (isBeaconActive(player)) {
			deactivateBeacon(player);
		} else {
			activateBeacon(player);
		}
	}

	private static void activateBeacon(ServerPlayer player) {
		PowerUtils.setResourceValueAndSync(player, BEACON_ACTIVE_ID, 1);

		player.playSound(SoundEvents.BEACON_ACTIVATE, 1.0f, 1.0f);
		player.displayClientMessage(Component.translatable("message.ssc_addon.beacon.activated"), true);
	}

	public static void deactivateBeacon(ServerPlayer player) {
		PowerUtils.setResourceValueAndSync(player, BEACON_ACTIVE_ID, 0);

		player.playSound(SoundEvents.BEACON_DEACTIVATE, 1.0f, 1.0f);
		player.displayClientMessage(Component.translatable("message.ssc_addon.beacon.deactivated"), true);
	}
}