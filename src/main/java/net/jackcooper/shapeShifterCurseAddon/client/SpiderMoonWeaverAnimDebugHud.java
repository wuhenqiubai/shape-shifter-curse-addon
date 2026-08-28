package net.jackcooper.shapeShifterCurseAddon.client;

import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.layered.AnimationStack;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.ModifierLayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 月织蛛动画调试 - 日志记录器。
 *
 * <p>由指令 {@code /ssc_addon debug anim} 开关（客户端指令切换）。
 * 开启期间每 tick 检测动画切换并写入游戏日志（搜 SSCA_AnimDebug），包含当前动画名、播放进度(tick/总长)、
 * velY、fallDistance、onGround、airTicks、sprint、二段跳额度——便于事后分析二段跳 jump→fall 衔接时序。
 *
 * <p>另提供 {@link #logSnapshotOnce} 供指令输出单条快照，以及 {@link #findActivePlayerFromStack}
 * 供二段跳动画修复逻辑读取当前播放动画。
 *
 * <p>纯客户端、仅调试用；不影响任何游戏逻辑。
 */
@Environment(EnvType.CLIENT)
public final class SpiderMoonWeaverAnimDebugHud {

	private static final Logger LOGGER = LoggerFactory.getLogger("SSCA_AnimDebug");
	/** 日志记录是否已开启（由 /ssc_addon debug anim 指令切换）。 */
	private static boolean recording = false;
	/** 上一 tick 的动画名 + tick，用于检测动画切换并写日志。 */
	private static String lastAnimName = null;
	private static int lastAnimTick = -1;

	private SpiderMoonWeaverAnimDebugHud() {}

	/** 注册客户端 tick 监听（检测动画切换写日志）。 */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpiderMoonWeaverAnimDebugHud::onClientTick);
	}

	/** 指令调用：切换日志记录开关，返回切换后的状态（true=已开启）。 */
	public static boolean toggleRecording() {
		recording = !recording;
		if (!recording) {
			// 关闭时重置状态，下次开启从干净状态开始
			lastAnimName = null;
			lastAnimTick = -1;
		}
		return recording;
	}

	/** 指令调用：无论是否开启记录，都向日志输出一条完整快照。 */
	public static void logSnapshotOnce(String action) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) return;
		logSnapshot(player, action);
	}

	private static void onClientTick(MinecraftClient client) {
		if (!recording || client.player == null) return;
		// 检测动画切换并写日志（便于分析二段跳衔接时序）
		String cur = readCurrentAnimation(client.player);
		String shortName = cur.contains("tick=") ? cur.substring(0, cur.indexOf("  tick=")) : cur;
		int curTick = extractTick(cur);
		if (!shortName.equals(lastAnimName)) {
			// 动画切换：记录前后对比 + 物理状态
			logSnapshot(client.player, "动画切换: [" + lastAnimName + "@tick" + lastAnimTick
					+ "] -> [" + shortName + "@tick" + curTick + "]");
			lastAnimName = shortName;
			lastAnimTick = curTick;
		} else if (curTick != lastAnimTick) {
			lastAnimTick = curTick;
		}
	}

	/** 从 readCurrentAnimation 的输出里提取 tick=后的整数。 */
	private static int extractTick(String animInfo) {
		try {
			int i = animInfo.indexOf("tick=");
			int j = animInfo.indexOf('/', i + 5);
			return Integer.parseInt(animInfo.substring(i + 5, j));
		} catch (Exception e) {
			return -1;
		}
	}

	/** 向游戏日志写一条完整快照：动作描述 + 当前动画 + 物理状态。 */
	private static void logSnapshot(ClientPlayerEntity player, String action) {
		String anim = readCurrentAnimation(player);
		Vec3d v = player.getVelocity();
		LOGGER.info("[{}] anim=[{}] velY={} fallDist={} onGround={} airTicks={} sprint={} djAvail={}",
				action, anim, String.format("%.3f", v.getY()),
				String.format("%.3f", player.fallDistance), player.isOnGround(),
				SpiderMoonWeaverDoubleJumpClient.getAirTicks(), player.isSprinting(),
				SpiderMoonWeaverDoubleJumpClient.isJumpAvailable());
	}

	/** 通过 PAL 1.1.5 API 读玩家当前播放的动画名 + 进度。 */
	public static String readCurrentAnimation(ClientPlayerEntity player) {
		try {
			PlayerAnimManager manager = PlayerAnimationAccess.getPlayerAnimManager(player);
			// manager 是 AnimationStack 子类：getLayers() 公开返回所有动画层，遍历找当前激活的控制器
			AnimationController ctrl = findActivePlayerFromStack(manager);
			if (ctrl == null) return "无动画 / T-pose";
			Animation animation = ctrl.getCurrentAnimation() != null ? ctrl.getCurrentAnimation().animation() : null;
			String name = "未知动画";
			if (animation != null) {
				// 动画名存于 Animation.getNameOrId()（PAL 1.1.5）
				name = animation.getNameOrId();
				if (name == null || name.isEmpty() || "null".equals(name)) name = "uuid=" + animation.uuid();
			}
			float tick = ctrl.getAnimationTicks();
			float total = animation != null ? animation.length() : -1;
			boolean infinite = animation != null && animation.loopType() == Animation.LoopType.LOOP;
			return String.format("%s  tick=%.1f/%.1f (state=%s inf=%b)",
					name, tick, total, ctrl.getAnimationState(), infinite);
		} catch (Throwable t) {
			return "读取失败: " + t.getClass().getSimpleName() + ": " + t.getMessage();
		}
	}

	/** 从 AnimationStack（PlayerAnimManager）的公开层列表遍历，找当前激活的 AnimationController。 */
	public static AnimationController findActivePlayerFromStack(AnimationStack stack) {
		if (stack == null) return null;
		try {
			// AnimationStack.getLayers() 返回 List<Pair<Integer, IAnimation>>（无需反射）
			List<it.unimi.dsi.fastutil.Pair<Integer, IAnimation>> layers = stack.getLayers();
			if (layers == null) return null;
			for (it.unimi.dsi.fastutil.Pair<Integer, IAnimation> entry : layers) {
				if (entry == null) continue;
				AnimationController ctrl = findActivePlayer(entry.second());
				if (ctrl != null) return ctrl;
			}
		} catch (Throwable ignored) { }
		return null;
	}

	/** 在单个动画对象中递归找当前激活的 AnimationController（穿透 ModifierLayer 修饰链）。 */
	private static AnimationController findActivePlayer(Object anim) {
		if (anim == null) return null;
		if (anim instanceof AnimationController ctrl) return ctrl.isActive() ? ctrl : null;
		// ModifierLayer.getAnimation() 返回当前播放的基础动画
		if (anim instanceof ModifierLayer<?> ml) {
			try {
				IAnimation base = ml.getAnimation();
				return findActivePlayer(base);
			} catch (Throwable ignored) { return null; }
		}
		return null;
	}
}