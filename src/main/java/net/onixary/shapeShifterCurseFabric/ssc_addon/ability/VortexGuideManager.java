package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.AxolotlTree;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进化美西螈「涡流引导」次技能（sp_secondary）—— 服务端引导状态机。
 *
 * <p>按次技能键开始引导 3 秒：期间移速降低约 50%（缓慢 III），持续回血共 6 颗心（12 生命），
 * 引导完成后额外获得 2 颗黄心（4 吸收）。CD 8 秒，不消耗湿润度。
 * 所有判定在服务端，节点未解锁 / 非本形态不触发；死亡 / 形态丢失中断且不进 CD。</p>
 */
public final class VortexGuideManager {

	private static final int CHANNEL_TICKS = 60;   // 引导 3 秒
	private static final int CD_TICKS = 160;       // 8 秒
	private static final int HEAL_INTERVAL = 10;   // 每 0.5 秒回血一次
	private static final float HEAL_PER_TICK = 2.0f; // 每次回 1 心（共 6 次 = 6 心）
	private static final int ABSORPTION_DURATION = 600; // 黄心持续 30 秒

	private static final Map<UUID, Integer> CHANNELING = new ConcurrentHashMap<>();

	private VortexGuideManager() {
	}

	/** 客户端按次技能键（无 payload）。服务端校验并开始引导。 */
	public static void onKeyPress(ServerPlayer player) {
		if (CHANNELING.containsKey(player.getUUID())) return; // 引导中不可重入
		if (!FormUtils.isUpgradeAxolotl(player)) return;
		// 节点门控：未解锁「涡流引导」不触发
		if (!RegEvolutionComponent.EVOLUTION.get(player).isUnlocked(AxolotlTree.NODE_VORTEX_GUIDE)) return;
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD) > 0) return; // CD 中

		CHANNELING.put(player.getUUID(), 0);
		ServerLevel sw = (ServerLevel) player.level();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.7f, 1.4f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AXOLOTL_SPLASH, SoundSource.PLAYERS, 1.0f, 1.2f);
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayer player) {
		Integer t = CHANNELING.get(player.getUUID());
		if (t == null) return;
		if (player.isDeadOrDying() || !FormUtils.isUpgradeAxolotl(player)) {
			cancel(player); // 死亡 / 形态丢失 → 中断，不进 CD
			return;
		}
		int tick = t + 1;
		CHANNELING.put(player.getUUID(), tick);
		ServerLevel sw = (ServerLevel) player.level();

		// 引导期间减速约 50%（缓慢 III，短时长滚动续期，引导结束即消散）
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 2, false, false, false));

		// 周期回血（共 6 心）
		if (tick % HEAL_INTERVAL == 0) {
			player.heal(HEAL_PER_TICK);
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.6f, 1.4f);
		}

		// 环绕水/心形引导粒子
		double ang = tick * 0.4;
		double r = 0.8;
		sw.sendParticles(ParticleTypes.FALLING_WATER,
				player.getX() + Math.cos(ang) * r, player.getY() + 1.0, player.getZ() + Math.sin(ang) * r,
				2, 0.05, 0.2, 0.05, 0.0);
		sw.sendParticles(ParticleTypes.HEART,
				player.getX(), player.getY() + 1.4, player.getZ(), 1, 0.3, 0.3, 0.3, 0.0);

		if (tick >= CHANNEL_TICKS) {
			complete(player); // 引导完成 → 2 黄心 + CD
		}
	}

	/** 引导完成：授予 2 黄心（4 吸收）并进入 CD。 */
	private static void complete(ServerPlayer player) {
		CHANNELING.remove(player.getUUID());
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ABSORPTION_DURATION, 0, false, false, true));
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, CD_TICKS);
		ServerLevel sw = (ServerLevel) player.level();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.6f);
		sw.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.6, 0.6, 0.6, 0.5);
	}

	/** 中断（不进 CD、不给黄心）。 */
	public static void cancel(ServerPlayer player) {
		CHANNELING.remove(player.getUUID());
	}

	public static void onPlayerDisconnect(UUID uuid) {
		CHANNELING.remove(uuid);
	}
}
