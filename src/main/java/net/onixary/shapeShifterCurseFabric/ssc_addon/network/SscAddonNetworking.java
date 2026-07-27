package net.onixary.shapeShifterCurseFabric.ssc_addon.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.networking.BytePayload;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.skin.PlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPGroupHeal;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaTeleport;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaPrimary;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritClawManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRegistry;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class SscAddonNetworking {
	public static final Identifier PACKET_KEY_PRESS = Identifier.of("my_addon", "key_press");
	/** 契灵 - 次要技能：瞬移。payload: byte mode (0=RAYCAST, 1=PLATFORM) */
	public static final Identifier PACKET_MANCIANIMA_TELEPORT = Identifier.of("my_addon", "mancianima_teleport");
	/** 契灵 - 主要技能：三段标记。无 payload，服务端根据当前状态分支。 */
	public static final Identifier PACKET_MANCIANIMA_PRIMARY = Identifier.of("my_addon", "mancianima_primary");
	/** 风灵「疾风连爪」：C2S 上报左键按住(boolean)；S2C 同步爪击阶段(int)+准星条进度(float)。 */
	public static final Identifier PACKET_CLAW_HOLD = Identifier.of("my_addon", "claw_hold");
	public static final Identifier PACKET_CLAW_STATE = Identifier.of("my_addon", "claw_state");
	/** 风灵副技能：C2S 按 sp_secondary 触发 +50% 增伤 buff。无 payload。 */
	public static final Identifier PACKET_CLAW_BUFF = Identifier.of("my_addon", "claw_buff");
	/** 风灵「风之冲刺」：C2S 按主技能键（无 payload，服务端按阶段分支）；S2C 同步阶段(int)+targetY(double)。 */
	public static final Identifier PACKET_WIND_DASH = Identifier.of("my_addon", "wind_dash");
	public static final Identifier PACKET_DASH_STATE = Identifier.of("my_addon", "dash_state");

	// ===== 白名单 GUI 网络包 =====
	/** S2C：服务端把调用者当前白名单 UUID 集合推给客户端，用于打开/刷新 GUI。payload: int n + n*UUID */
	public static final Identifier PACKET_WHITELIST_GUI_SYNC = Identifier.of("my_addon", "whitelist_gui_sync");
	/** C2S：玩家在 GUI 中请求把某 UUID 加入自己的白名单。payload: UUID */
	public static final Identifier PACKET_WHITELIST_GUI_ADD = Identifier.of("my_addon", "whitelist_gui_add");
	/** C2S：玩家在 GUI 中请求把某 UUID 从自己的白名单移除。payload: UUID */
	public static final Identifier PACKET_WHITELIST_GUI_REMOVE = Identifier.of("my_addon", "whitelist_gui_remove");
	/** C2S：玩家切换模式。payload: byte (0=默认, 1=自定义) */
	public static final Identifier PACKET_WHITELIST_GUI_MODE = Identifier.of("my_addon", "whitelist_gui_mode");
	/** C2S：玩家从生物白名单中移除一个 UUID。payload: UUID */
	public static final Identifier PACKET_WHITELIST_GUI_MOB_REMOVE = Identifier.of("my_addon", "whitelist_gui_mob_remove");

	/** C2S：美西螈装死期间按技能键请求提前结束装死。无 payload。 */
	public static final Identifier PACKET_PLAY_DEAD_END = Identifier.of("my_addon", "play_dead_end");

	/** C2S：美西螈漩涡开始蓄力。无 payload。 */
	public static final Identifier PACKET_VORTEX_START = Identifier.of("my_addon", "vortex_start");
	/** C2S：美西螈漩涡释放（提前释放）。无 payload。 */
	public static final Identifier PACKET_VORTEX_RELEASE = Identifier.of("my_addon", "vortex_release");

	/** C2S：进化美西螈主技能「投掷水矛」按键。无 payload。 */
	public static final Identifier PACKET_UPGRADE_AXOLOTL_SPEAR = Identifier.of("my_addon", "upgrade_axolotl_spear");
	/** C2S：进化美西螈次技能「涡流引导」按键。无 payload。 */
	public static final Identifier PACKET_UPGRADE_AXOLOTL_VORTEX = Identifier.of("my_addon", "upgrade_axolotl_vortex");
	/** S2C：进化美西螈「投掷水矛」蓄力期手持水矛渲染状态（对追踪者+自身广播）。payload: UUID + boolean charging */
	public static final Identifier PACKET_SPEAR_CHARGE_STATE = Identifier.of("my_addon", "spear_charge_state");

	// ===== 荧光幼灵技能网络包 =====
	/** C2S：荧光幼灵主要技能（法阵激光）按键。无 payload。 */
	public static final Identifier PACKET_FLUO_LASER = Identifier.of("my_addon", "fluo_laser_key");
	/** C2S：荧光幼灵次要技能（潮汐波动）按键。无 payload。 */
	public static final Identifier PACKET_FLUO_TIDAL = Identifier.of("my_addon", "fluo_tidal_key");
	/** S2C：荧光幼灵「潮汐束缚」把被拴目标的 entityId 同步给客机，用于渲染守卫者激光。payload: varint orbId + varint count + count*varint entityId */
	public static final Identifier PACKET_TIDAL_TETHER = Identifier.of("my_addon", "tidal_tether");

	// ===== SSCA 进化加点系统网络包（框架） =====
	/** C2S：玩家选择进化路线。payload: String routeId */
	public static final Identifier PACKET_EVO_SELECT_ROUTE = Identifier.of("my_addon", "evo_select_route");
	/** C2S：玩家选择 SP 分支。payload: String branchId */
	public static final Identifier PACKET_EVO_SELECT_BRANCH = Identifier.of("my_addon", "evo_select_branch");
	/** C2S：玩家请求解锁一个天赋节点。payload: String nodeId */
	public static final Identifier PACKET_EVO_UNLOCK = Identifier.of("my_addon", "evo_unlock");
	/** C2S：一次性提交多个待确认节点（按点击顺序），服务端限频一次后顺序逐个解锁。payload: int count + count*String */
	public static final Identifier PACKET_EVO_UNLOCK_BATCH = Identifier.of("my_addon", "evo_unlock_batch");
	/** C2S：开局选形态界面选定一个 SSCA 进化形态、直接走 SSCA 路线进化。payload: String formId */
	public static final Identifier PACKET_SSCA_START_ROUTE = Identifier.of("my_addon", "ssca_start_route");
	/** C2S：客机加入后请求服务端把所有在场玩家的形态+皮肤同步过来（修复客机看其它玩家默认白模型）。无 payload */
	public static final Identifier PACKET_REQUEST_ALL_FORM_SYNC = Identifier.of("my_addon", "request_all_form_sync");
	/** S2C：服务端广播所有在场玩家的形态 ID。payload: int count + count*(UUID + String formId) */
	public static final Identifier PACKET_BROADCAST_FORMS = Identifier.of("my_addon", "broadcast_forms");
	/** S2C：把所有 SSCA 进化路线定义（JSON）同步给客户端，供进化树 UI 渲染。payload: int count + count*(routeId + rawJson) */
	public static final Identifier PACKET_EVO_ROUTES_SYNC = Identifier.of("my_addon", "evo_routes_sync");

	/** C2S 限频：每玩家每个事件类型记录上一次服务端接收时间，防外挂客户端 spam。 */
	private static final Map<UUID, Long> LAST_WHITELIST_PACKET_TICK = new ConcurrentHashMap<>();
	/** 同一玩家两次白名单操作的最小间隔，单位：millis。 */
	private static final long WHITELIST_PACKET_MIN_INTERVAL_MS = 100L;

	/**
	 * 检查玩家是否在限频阈值内 spam。返回 true 表示该包应被丢弃。
	 * 使用 ConcurrentHashMap 保证多玩家环境下线程安全。
	 */
	private static boolean isRateLimited(ServerPlayerEntity player) {
		long now = System.currentTimeMillis();
		Long last = LAST_WHITELIST_PACKET_TICK.put(player.getUuid(), now);
		return last != null && (now - last) < WHITELIST_PACKET_MIN_INTERVAL_MS;
	}

	/** 玩家退服时调用：清理限频时间戳，防止僵尸 UUID 长期积累。 */
	public static void onPlayerDisconnect(UUID uuid) {
		LAST_WHITELIST_PACKET_TICK.remove(uuid);
	}

	/** 风灵「疾风连爪」：同步爪击阶段(phase)与准星条进度给客户端。 */
	public static void syncClawState(ServerPlayerEntity player, int phase, float crosshairProgress) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeInt(phase);
		buf.writeFloat(crosshairProgress);
		ServerPlayNetworking.send(player, new BytePayload(BytePayload.id(PACKET_CLAW_STATE), buf));
	}

	/** 风灵「风之冲刺」：同步阶段(phase)与目标悬浮 Y 给客户端（驱动落点预览）。 */
	public static void syncDashState(ServerPlayerEntity player, int phase, double targetY) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeInt(phase);
		buf.writeDouble(targetY);
		ServerPlayNetworking.send(player, new BytePayload(BytePayload.id(PACKET_DASH_STATE), buf));
	}

	/** 进化美西螈「投掷水矛」：向追踪该玩家的客户端 + 玩家自身广播蓄力手持水矛渲染状态。 */
	public static void syncSpearChargeState(ServerPlayerEntity player, boolean charging) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeUuid(player.getUuid());
		buf.writeBoolean(charging);
		for (ServerPlayerEntity viewer :
				PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, new BytePayload(BytePayload.id(PACKET_SPEAR_CHARGE_STATE), PacketByteBufs.copy(buf)));
		}
		ServerPlayNetworking.send(player, new BytePayload(BytePayload.id(PACKET_SPEAR_CHARGE_STATE), buf));
	}

	public static void registerServerReceivers() {
		// 注册所有 C2S payload 类型
		BytePayload.registerC2S(PACKET_KEY_PRESS);
		BytePayload.registerC2S(PACKET_MANCIANIMA_TELEPORT);
		BytePayload.registerC2S(PACKET_MANCIANIMA_PRIMARY);
		BytePayload.registerC2S(PACKET_WHITELIST_GUI_ADD);
		BytePayload.registerC2S(PACKET_WHITELIST_GUI_REMOVE);
		BytePayload.registerC2S(PACKET_WHITELIST_GUI_MODE);
		BytePayload.registerC2S(PACKET_WHITELIST_GUI_MOB_REMOVE);
		BytePayload.registerC2S(PACKET_PLAY_DEAD_END);
		BytePayload.registerC2S(PACKET_VORTEX_START);
		BytePayload.registerC2S(PACKET_VORTEX_RELEASE);
		BytePayload.registerC2S(PACKET_UPGRADE_AXOLOTL_SPEAR);
		BytePayload.registerC2S(PACKET_UPGRADE_AXOLOTL_VORTEX);
		BytePayload.registerC2S(PACKET_CLAW_HOLD);
		BytePayload.registerC2S(PACKET_CLAW_BUFF);
		BytePayload.registerC2S(PACKET_WIND_DASH);
		BytePayload.registerC2S(PACKET_FLUO_LASER);
		BytePayload.registerC2S(PACKET_FLUO_TIDAL);
		BytePayload.registerC2S(PACKET_EVO_SELECT_ROUTE);
		BytePayload.registerC2S(PACKET_EVO_SELECT_BRANCH);
		BytePayload.registerC2S(PACKET_EVO_UNLOCK);
		BytePayload.registerC2S(PACKET_EVO_UNLOCK_BATCH);
		BytePayload.registerC2S(PACKET_SSCA_START_ROUTE);
		BytePayload.registerC2S(PACKET_REQUEST_ALL_FORM_SYNC);

		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_KEY_PRESS), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			int keyId = buf.readInt();
			ctx.server().execute(() -> handleKeyPress(ctx.player(), keyId));
		});

		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_MANCIANIMA_TELEPORT), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			byte mode = buf.readByte();
			ctx.server().execute(() -> MancianimaTeleport.execute(ctx.player(), mode));
		});

		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_MANCIANIMA_PRIMARY), (BytePayload payload, ServerPlayNetworking.Context ctx) -> { PacketByteBuf buf = payload.data(); ctx.server().execute(() -> MancianimaPrimary.execute(ctx.player())); });

		// 白名单 GUI - 添加
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_WHITELIST_GUI_ADD), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			UUID target = buf.readUuid();
			ctx.server().execute(() -> {
				if (isRateLimited(ctx.player())) return; // 防 spam
				if (target.equals(ctx.player().getUuid())) return; // 不允许把自己加入自己的白名单
				// 限制单玩家白名单总容量，防恶意客户端纯增加坚持性 tag 撑爆服务端存储
				String tag = AllaySPGroupHeal.WHITELIST_TAG_PREFIX + target.toString();
				long existing = ctx.player().getCommandTags().stream()
					.filter(t -> t.startsWith(AllaySPGroupHeal.WHITELIST_TAG_PREFIX)).count();
				if (!ctx.player().getCommandTags().contains(tag) && existing >= 256L) return; // 每人最多 256 个
				ctx.player().getCommandTags().add(tag);
				sendWhitelistSync(ctx.player());
			});
		});

		// 白名单 GUI - 移除
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_WHITELIST_GUI_REMOVE), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			UUID target = buf.readUuid();
			ctx.server().execute(() -> {
				if (isRateLimited(ctx.player())) return;
				String tag = AllaySPGroupHeal.WHITELIST_TAG_PREFIX + target.toString();
				ctx.player().getCommandTags().remove(tag);
				sendWhitelistSync(ctx.player());
			});
		});

		// 白名单 GUI - 切换默认/自定义模式
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_WHITELIST_GUI_MODE), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			byte mode = buf.readByte();
			ctx.server().execute(() -> {
				if (isRateLimited(ctx.player())) return;
				WhitelistUtils.setCustomMode(ctx.player(), mode == 1);
				sendWhitelistSync(ctx.player());
			});
		});

		// 白名单 GUI - 生物移除
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_WHITELIST_GUI_MOB_REMOVE), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			UUID mobUuid = buf.readUuid();
			ctx.server().execute(() -> {
				if (isRateLimited(ctx.player())) return;
				WhitelistUtils.removeMobFromWhitelist(ctx.player(), mobUuid);
				// 同时清掉友军标记双写时写入的 player 前缀 tag，避免残留导致 count 不一致
				AllaySPGroupHeal.removeFromWhitelistByUuid(ctx.player(), mobUuid);
				sendWhitelistSync(ctx.player());
			});
		});

		// SSCA 美西螈装死 - 提前结束（装死期间按 sp_secondary）
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_PLAY_DEAD_END), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> {
			if (!ctx.player().hasStatusEffect(SscAddon.PLAYING_DEAD_ENTRY)) return;
			ctx.player().removeStatusEffect(SscAddon.PLAYING_DEAD_ENTRY);
			ctx.player().removeStatusEffect(StatusEffects.BLINDNESS);
			ctx.player().removeStatusEffect(StatusEffects.SLOWNESS);
			ctx.player().setPose(net.minecraft.entity.EntityPose.STANDING);
			// 提前结束：CD 从此刻起算 25 秒
			PowerUtils.setResourceValueAndSync(ctx.player(), FormIdentifiers.SP_SECONDARY_CD, 500);
		}));

		// SSCA 美西螈漩涡蓄力 - 开始 / 释放
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_VORTEX_START), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexChargeManager.start(ctx.player())));
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_VORTEX_RELEASE), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexChargeManager.release(ctx.player())));

		// SSCA 进化美西螈技能：主「投掷水矛」 / 次「涡流引导」
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_UPGRADE_AXOLOTL_SPEAR), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WaterSpearLeapManager.onKeyPress(ctx.player())));
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_UPGRADE_AXOLOTL_VORTEX), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.VortexGuideManager.onKeyPress(ctx.player())));

		// 风灵「疾风连爪」：客户端上报左键按住状态
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_CLAW_HOLD), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			boolean hold = buf.readBoolean();
			ctx.server().execute(() -> WindSpiritClawManager.setHolding(ctx.player(), hold));
		});

		// 风灵副技能：sp_secondary 触发 +50% 增伤 buff
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_CLAW_BUFF), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindSpiritClawManager.activateSecondaryBuff(ctx.player())));

		// 风灵「风之冲刺」：主技能键（服务端按当前阶段分支：起飞 / 悬浮中冲刺）
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_WIND_DASH), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.WindDashManager.onKeyPress(ctx.player())));

		// 荧光幼灵技能按键：主要（法阵激光）/ 次要（潮汐波动）
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_FLUO_LASER), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FluorescentLaserManager.onKeyPress(ctx.player())));
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_FLUO_TIDAL), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FluorescentTidalManager.onKeyPress(ctx.player())));

		// ===== SSCA 进化加点系统 =====
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_EVO_SELECT_ROUTE), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			String routeId = buf.readString(256);
			ctx.server().execute(() -> {
				if (isRateLimited(ctx.player())) return;
				EvolutionManager.selectRoute(ctx.player(), routeId);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_EVO_SELECT_BRANCH), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			String branchId = buf.readString(256);
			ctx.server().execute(() -> {
				if (isRateLimited(ctx.player())) return;
				EvolutionManager.selectBranch(ctx.player(), branchId);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_EVO_UNLOCK), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			String nodeId = buf.readString(256);
			ctx.server().execute(() -> {
				if (isRateLimited(ctx.player())) return;
				EvolutionManager.tryUnlock(ctx.player(), nodeId);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_EVO_UNLOCK_BATCH), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			int count = Math.max(0, Math.min(64, buf.readInt()));
			java.util.List<String> ids = new java.util.ArrayList<>(count);
			for (int i = 0; i < count; i++) ids.add(buf.readString(256));
			ctx.server().execute(() -> {
				if (isRateLimited(ctx.player())) return;
				for (String id : ids) EvolutionManager.tryUnlock(ctx.player(), id);
			});
		});

		// 开局选形态界面：直接走 SSCA 进化路线进入选定形态
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_SSCA_START_ROUTE), (BytePayload payload, ServerPlayNetworking.Context ctx) -> {
			PacketByteBuf buf = payload.data();
			String formId = buf.readString(256);
			ctx.server().execute(() -> {
				if (isRateLimited(ctx.player())) return;
				EvolutionManager.startSscaRoute(ctx.player(), formId);
			});
		});

		// 客机加入后请求：把所有在场玩家的形态 ID + 皮肤数据广播给「所有在线玩家」（含请求者与已在线客机），
		// 绕过 CCA 同步的不确定性，修复刚进游戏 / 新玩家加入时看其它玩家是默认白模型。
		// 形态模型由客机据 formId 重建 origin 决定，颜色据皮肤数据上色。
		ServerPlayNetworking.registerGlobalReceiver(BytePayload.id(PACKET_REQUEST_ALL_FORM_SYNC), (BytePayload payload, ServerPlayNetworking.Context ctx) -> ctx.server().execute(() -> {
			List<ServerPlayerEntity> players = ctx.server().getPlayerManager().getPlayerList();
			for (ServerPlayerEntity recipient : players) {
				PacketByteBuf out = PacketByteBufs.create();
				out.writeInt(players.size());
				for (ServerPlayerEntity p : players) {
					out.writeUuid(p.getUuid());
					Identifier fid =
							RegPlayerFormComponent.PLAYER_FORM.get(p).nowFormID;
					out.writeString(fid == null ? "" : fid.toString());
					// 皮肤数据：保留原皮 / 是否启用形态颜色 / 五种颜色(ABGR) / 灰度反转 / 随机音效
					PlayerSkinComponent skin =
							RegPlayerSkinComponent.SKIN_SETTINGS.get(p);
					out.writeBoolean(skin.shouldKeepOriginalSkin());
					out.writeBoolean(skin.isEnableFormColor());
					FormTextureUtils.ColorSetting c = skin.getFormColor();
					out.writeInt(c.getPrimaryColor());
					out.writeInt(c.getAccentColor1());
					out.writeInt(c.getAccentColor2());
					out.writeInt(c.getEyeColorA());
					out.writeInt(c.getEyeColorB());
					out.writeBoolean(c.getPrimaryGreyReverse());
					out.writeBoolean(c.getAccent1GreyReverse());
					out.writeBoolean(c.getAccent2GreyReverse());
					out.writeBoolean(skin.isEnableFormRandomSound());
				}
				ServerPlayNetworking.send(recipient, new BytePayload(BytePayload.id(PACKET_BROADCAST_FORMS), out));
			}
			// 同步 SSCA 进化路线定义给请求者（客户端进化树 UI 渲染需要，多人环境客户端无 datapack 数据）
			PacketByteBuf routesOut = PacketByteBufs.create();
			Map<String, String> rawRoutes =
					EvolutionRegistry.INSTANCE.getRawJson();
			routesOut.writeInt(rawRoutes.size());
			for (Map.Entry<String, String> e : rawRoutes.entrySet()) {
				routesOut.writeString(e.getKey(), 256);
				routesOut.writeString(e.getValue(), 2000000);
			}
			ServerPlayNetworking.send(ctx.player(), new BytePayload(BytePayload.id(PACKET_EVO_ROUTES_SYNC), routesOut));
		}));
	}

	/** 服务端：把指定玩家当前白名单推送到其客户端，用于打开/刷新白名单 GUI。 */
	public static void sendWhitelistSync(ServerPlayerEntity player) {
		List<UUID> uuids = AllaySPGroupHeal.getWhitelistUuids(player);
		List<UUID> mobs = WhitelistUtils.getWhitelistedMobUuids(player);
		// 去重：生物 UUID 同时存于玩家前缀时（双写机制产生），不要出现在玩家 tab
		java.util.Set<UUID> mobSet = new java.util.HashSet<>(mobs);
		List<UUID> filteredPlayers = new java.util.ArrayList<>();
		for (UUID u : uuids) if (!mobSet.contains(u)) filteredPlayers.add(u);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(WhitelistUtils.isCustomMode(player));
		buf.writeInt(filteredPlayers.size());
		for (UUID u : filteredPlayers) buf.writeUuid(u);
		// 生物列表：int n + n * (UUID + String typeId or "")
		buf.writeInt(mobs.size());
		for (UUID u : mobs) {
			buf.writeUuid(u);
			String typeId = WhitelistUtils.getMobTypeId(player, u);
			buf.writeString(typeId != null ? typeId : "");
		}
		ServerPlayNetworking.send(player, new BytePayload(BytePayload.id(PACKET_WHITELIST_GUI_SYNC), buf));
	}

	private static void handleKeyPress(ServerPlayerEntity player, int keyId) {
		// Find current form
		IForm form = FormUtils.getCurrentForm(player);
		if (form == null) return;

		// formId 暂未使用（旧 Ability_AllayHeal 已废弃），保留 form 引用以便后续按形态分发
		// Allay Heal (using keyId 1 for now, mapped from client)
        /*if (keyId == 1 && (formId.getPath().equals("form_allay_sp") || formId.getPath().equals("allay_sp"))) {
             Ability_AllayHeal.onHold(player);
        }*/

		// Add other key handlers here if needed (e.g. Fox Fire)
	}
}