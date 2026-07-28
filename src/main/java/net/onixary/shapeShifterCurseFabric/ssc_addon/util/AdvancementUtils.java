package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 工具方法：在服务端给玩家发放自定义成就。
 * 用于 minecraft:impossible 触发的成就（由 Java 主动 grant criterion）。
 */
public final class AdvancementUtils {

	private AdvancementUtils() {}

	/**
	 * 给指定玩家发放成就（grant 所有未完成的 criteria）。
	 * 仅服务端可用；客户端调用会静默忽略。
	 *
	 * @param player    目标玩家
	 * @param advId     成就 Identifier，如 Identifier.of("ssc_addon", "ssc_addon/tonight_moon_beautiful")
	 */
	public static void grant(Player player, ResourceLocation advId) {
		if (player == null || player.level().isClientSide) return;
		if (!(player instanceof ServerPlayer sp)) return;
		MinecraftServer server = sp.getServer();
		if (server == null) return;
		AdvancementHolder adv = server.getAdvancements().get(advId);
		if (adv == null) return;
		AdvancementProgress progress = sp.getAdvancements().getOrStartProgress(adv);
		if (progress.isDone()) return;
		for (String c : progress.getRemainingCriteria()) {
			sp.getAdvancements().award(adv, c);
		}
	}
}