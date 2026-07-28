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
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

/**
 * 荧光幼灵按键检测器（客户端边沿触发）。
 * 主要键(sp_primary)：荧光幼灵形态下按下 → 发法阵激光包。
 * 次要键(sp_secondary)：荧光幼灵形态下按下 → 发潮汐波动包。
 * 采用边沿触发（pressed && !wasPressed），避免长按连发。
 */
@Environment(EnvType.CLIENT)
public final class FluorescentKeyClient {
	private static boolean wasPrimary = false;
	private static boolean wasSecondary = false;

	private FluorescentKeyClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FluorescentKeyClient::onClientTick);
	}

	private static void onClientTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			wasPrimary = wasSecondary = false;
			return;
		}
		// 仅荧光幼灵形态响应
		if (!FormUtils.isAxolotlFluorescent(player)) {
			wasPrimary = wasSecondary = false;
			return;
		}
		KeyMapping primary = SscAddonKeybindings.getPrimaryKey();
		KeyMapping secondary = SscAddonKeybindings.getSecondaryKey();

		boolean pNow = primary != null && primary.isDown();
		if (pNow && !wasPrimary) {
			send(SscAddonNetworking.PACKET_FLUO_LASER);
		}
		wasPrimary = pNow;

		boolean sNow = secondary != null && secondary.isDown();
		if (sNow && !wasSecondary) {
			send(SscAddonNetworking.PACKET_FLUO_TIDAL);
		}
		wasSecondary = sNow;
	}

	private static void send(ResourceLocation packet) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ClientPlayNetworking.send(new BytePayload(BytePayload.id(packet), buf));
	}
}