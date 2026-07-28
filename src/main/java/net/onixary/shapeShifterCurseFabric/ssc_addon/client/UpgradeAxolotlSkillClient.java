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
 * 进化美西螈技能 - 客户端按键检测器。
 * 主技能键（sp_primary）→ 投掷水矛；次技能键（sp_secondary）→ 涡流引导。
 * 仅在进化美西螈形态下发包；节点解锁 / CD 判定全在服务端。
 */
@Environment(EnvType.CLIENT)
public final class UpgradeAxolotlSkillClient {
	private static boolean wasPrimaryPressed = false;
	private static boolean wasSecondaryPressed = false;

	private UpgradeAxolotlSkillClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(UpgradeAxolotlSkillClient::onClientTick);
	}

	private static void onClientTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || !FormUtils.isUpgradeAxolotl(player)) {
			wasPrimaryPressed = false;
			wasSecondaryPressed = false;
			return;
		}
		KeyMapping primary = SscAddonKeybindings.getPrimaryKey();
		KeyMapping secondary = SscAddonKeybindings.getSecondaryKey();
		boolean p = primary != null && primary.isDown();
		boolean s = secondary != null && secondary.isDown();
		if (p && !wasPrimaryPressed) {
			send(SscAddonNetworking.PACKET_UPGRADE_AXOLOTL_SPEAR);
		}
		if (s && !wasSecondaryPressed) {
			send(SscAddonNetworking.PACKET_UPGRADE_AXOLOTL_VORTEX);
		}
		wasPrimaryPressed = p;
		wasSecondaryPressed = s;
	}

	private static void send(ResourceLocation packet) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ClientPlayNetworking.send(new BytePayload(BytePayload.id(packet), buf));
	}
}