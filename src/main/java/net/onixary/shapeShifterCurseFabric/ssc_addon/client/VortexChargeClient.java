package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.onixary.shapeShifterCurseFabric.networking.BytePayload;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexChargeManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

/**
 * SP 美西螈漩涡蓄力 - 客户端按键检测器。
 * 按 sp_primary：未蓄力 → 发「开始」包；蓄力中(vortex_state>0) → 发「释放」包。
 */
@Environment(EnvType.CLIENT)
public final class VortexChargeClient {
	private static boolean wasKeyPressed = false;

	private VortexChargeClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(VortexChargeClient::onClientTick);
	}

	private static void onClientTick(Minecraft client) {
		LocalPlayer player = client.player;
		KeyMapping key = SscAddonKeybindings.getPrimaryKey();
		if (player == null || client.level == null || key == null) {
			wasKeyPressed = false;
			return;
		}
		if (!FormUtils.isAxolotlSP(player)) {
			wasKeyPressed = false;
			return;
		}
		boolean pressed = key.isDown();
		if (pressed && !wasKeyPressed) {
			int vortexState = PowerUtils.getClientResourceValue(player, VortexChargeManager.VORTEX_STATE);
			if (vortexState > 0) {
				send(SscAddonNetworking.PACKET_VORTEX_RELEASE);
			} else {
				send(SscAddonNetworking.PACKET_VORTEX_START);
			}
		}
		wasKeyPressed = pressed;
	}

	private static void send(ResourceLocation packet) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ClientPlayNetworking.send(new BytePayload(BytePayload.id(packet), buf));
	}
}