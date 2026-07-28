package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ThrownWaterSpearEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.AxolotlTree;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进化美西螈「投掷水矛」主技能（sp_primary）—— 服务端状态机。
 *
 * <p>按主技能键：<b>不往背包放任何物品</b>，仅同步「蓄力中」状态给客户端 →
 * 由渲染 mixin 纯渲染在手上画出 3D 水矛模型 + 举矛过肩姿势（{@code getArmPose}/第一人称矩阵 mixin，
 * 靠 {@code CustomModelData=1} 恒定 3D，不依赖 vanilla「正在使用物品」状态、不动玩家背包）。
 * 同时冲量斜后上跃并<b>无重力悬浮</b>；蓄力 1.35 秒后朝准星投出直线水矛。
 * 直击 12 物理 + 2 格半径 5 点 AOE，耗 6% 湿润度（18 air），CD 8 秒。</p>
 *
 * <p>零背包污染：全程不生成/替换任何物品，投出 / 取消 / 死亡 / 形态丢失 / 断线 只需恢复重力 + 结束渲染。</p>
 */
public final class WaterSpearLeapManager {

	private static final int CHARGE_TICKS = 27;    // 蓄力 ~1.35 秒后投矛
	private static final int CD_TICKS = 160;       // 8 秒
	private static final int AIR_COST = 18;        // 6% 湿润度
	private static final double LEAP_BACK = 0.80;  // 起跃向后冲量（更斜后）
	private static final double LEAP_UP = 0.62;    // 起跃向上冲量（跳更高）
	private static final double DECAY = 0.80;       // 每 tick 速度衰减（缓入缓出 + 收束到悬浮）

	private static final Map<UUID, LeapState> STATES = new ConcurrentHashMap<>();

	private static final class LeapState {
		int tick = 0;
	}

	private WaterSpearLeapManager() {
	}

	/** 客户端按主技能键（无 payload）。服务端校验、换水矛并起跃悬浮。 */
	public static void onKeyPress(ServerPlayer player) {
		if (STATES.containsKey(player.getUUID())) return; // 施法中不可重入
		if (!FormUtils.isUpgradeAxolotl(player)) return;
		if (!RegEvolutionComponent.EVOLUTION.get(player).isUnlocked(AxolotlTree.NODE_WATER_SPEAR)) return;
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return; // CD 中
		if (player.getAirSupply() < AIR_COST) return; // 湿润度不足

		player.setAirSupply(player.getAirSupply() - AIR_COST);

		LeapState s = new LeapState();
		STATES.put(player.getUUID(), s);

		// 同步「蓄力中」→ 客户端纯渲染：手上渲染 3D 水矛模型（不往背包放任何物品）+ 举矛过肩姿势
		SscAddonNetworking.syncSpearChargeState(player, true);

		// 无重力 + 一次性冲量斜后上跃（视线反方向 + 上），随后衰减到空中悬浮
		player.setNoGravity(true);
		Vec3 look = player.getLookAngle();
		Vec3 back = new Vec3(-look.x, 0, -look.z);
		if (back.lengthSqr() < 1.0e-4) back = new Vec3(0, 0, -1);
		back = back.normalize();
		player.setDeltaMovement(back.x * LEAP_BACK, LEAP_UP, back.z * LEAP_BACK);
		player.hurtMarked = true;
		player.fallDistance = 0.0f;

		ServerLevel sw = (ServerLevel) player.level();
		// 起手蓄力音效：海晶核激活（魔法水涌，音量拉大做明显）+ 高速水花 + 美西螈入水（全员可闻 null）
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.2f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0f, 1.1f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AXOLOTL_SPLASH, SoundSource.PLAYERS, 1.2f, 0.8f);
		sw.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.2, player.getZ(),
				30, 0.4, 0.2, 0.4, 0.3);
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayer player) {
		LeapState s = STATES.get(player.getUUID());
		if (s == null) return;
		if (player.isDeadOrDying() || !FormUtils.isUpgradeAxolotl(player)) {
			cancel(player); // 死亡 / 形态丢失 → 取消并归还物品、恢复重力
			return;
		}
		s.tick++;
		ServerLevel sw = (ServerLevel) player.level();

		if (s.tick < CHARGE_TICKS) {
			// 无重力下速度衰减：起跃冲量平滑收束到 0 → 跃起后悬浮在空中（不落）
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * DECAY, v.y * DECAY, v.z * DECAY);
			player.hurtMarked = true;
			player.fallDistance = 0.0f;
			if (s.tick % 4 == 0) {
				sw.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY() + 1.0, player.getZ(),
						6, 0.4, 0.4, 0.4, 0.0);
			}
			// 蓄力音效：每 5 tick 一声上升气泡（音调随蓄力进度 0.8→1.7，营造能量聚集感）
			if (s.tick % 5 == 0) {
				float progress = (float) s.tick / (float) CHARGE_TICKS;
				float pitch = 0.8f + progress * 0.9f;
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 0.7f, pitch);
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.35f, pitch);
			}
			// 蓄满前瞬间（最后 3 tick）：海晶核短鸣提示「即将投出」
			if (s.tick == CHARGE_TICKS - 3) {
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.PLAYERS, 0.9f, 1.4f);
			}
		} else {
			// 投矛：恢复重力、朝准星发射直线水矛
			throwSpear(player, s);
			finish(player);
		}
	}

	/** 朝准星方向投出直线水矛。 */
	private static void throwSpear(ServerPlayer player, LeapState s) {
		ServerLevel sw = (ServerLevel) player.level();
		Vec3 dir = player.getLookAngle().normalize();
		ThrownWaterSpearEntity spear = new ThrownWaterSpearEntity(sw, player);
		spear.setPos(player.getX() + dir.x * 0.6, player.getEyeY() - 0.1 + dir.y * 0.6, player.getZ() + dir.z * 0.6);
		spear.setDirection(dir);
		sw.addFreshEntity(spear);
		// 不 swingHand：与 vanilla 水矛（三叉戟）投掷一致——矛从举矛蓄力姿势直接飞出、手臂落回正常，
		// 而非普通攻击挥手（TridentItem.onStoppedUsing 同样不挥手）。
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.2f, 1.1f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 1.0f, 0.9f);
	}

	/** 投矛完成：恢复重力、结束蓄力渲染、进入 CD 并清理状态。 */
	private static void finish(ServerPlayer player) {
		STATES.remove(player.getUUID());
		player.setNoGravity(false);
		SscAddonNetworking.syncSpearChargeState(player, false);
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, CD_TICKS);
	}

	/** 取消（不进 CD、不投矛）：恢复重力、结束蓄力渲染。 */
	public static void cancel(ServerPlayer player) {
		LeapState s = STATES.remove(player.getUUID());
		if (s != null) {
			player.setNoGravity(false);
			SscAddonNetworking.syncSpearChargeState(player, false);
		}
	}

	/** 该玩家（按 UUID）是否处于投掷水矛蓄力中（服务端权威，用于蓄力期禁用右键交互）。 */
	public static boolean isCharging(java.util.UUID id) {
		return STATES.containsKey(id);
	}

	/** 断线：恢复重力并清理。 */
	public static void onPlayerDisconnect(ServerPlayer player) {
		LeapState s = STATES.remove(player.getUUID());
		if (s != null) {
			player.setNoGravity(false);
		}
	}
}
