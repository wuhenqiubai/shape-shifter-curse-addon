package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.onixary.shapeShifterCurseFabric.networking.BytePayload;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契灵主要技能 - 标记状态机（服务端权威）。
 *
 * 颜色等级：YELLOW（>24 格）↔ ORANGE（≤24 格）↔ RED（已升级）。
 * 距离变化时 RED→YELLOW，但 expireTick 继承不变。
 *
 * 同一时间每个契灵玩家只能持有 1 个标记；上一个目标的副作用立刻清除。
 *
 * 颜色高亮：通过 S2C 同步包 + my_addon:mancianima_mark_color 条件 + 三个 entity_glow power 实现，
 * 仅契灵本人客户端可见，不污染原版 GLOWING / scoreboard team。
 *
 * 红色标记锁定 15 秒：每个被红色标记过的目标，15 秒内不能被同一玩家再次红色标记。
 */
public final class MancianimaMarkManager {

	private MancianimaMarkManager() {}

	public enum MarkColor { YELLOW, ORANGE, RED }

	public static final int MARK_DURATION_TICKS = 300;        // 15s
	public static final int RANGE_ORANGE = 24;
	public static final int RANGE_RED_KEEP = 24;
	public static final int RED_RELOCK_COOLDOWN_TICKS = 300;  // 15s 内不能对同一目标再红标
	/** 阶段升级冷却：黄标→升红、红标→引爆都至少需要等待 3s */
	public static final int STAGE_GATE_TICKS = 60;            // 3s

	/** S2C 包：将本地玩家持有的标记同步到客户端，用于 entity_glow */
	public static final ResourceLocation PACKET_MARK_SYNC = ResourceLocation.fromNamespaceAndPath("ssc_addon", "mancianima_mark_sync");

	public static final class Mark {
		public final UUID targetUuid;
		public MarkColor color;
		public long expireTick;
		/** 当前阶段开始 tick：黄标=首次标记 tick；红标=升红 tick。用于阶段冷却判定。 */
		public long colorSetTick;
		public Mark(UUID t, MarkColor c, long e, long set) {
			targetUuid = t; color = c; expireTick = e; colorSetTick = set;
		}
	}

	/** marker player UUID -> Mark */
	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
	/** 每 (markerUuid + targetUuid) 的红标重锁冷却结束 tick */
	private static final Map<String, Long> RED_LOCKOUT = new ConcurrentHashMap<>();
	/** 当前正在引导段：marker UUID -> ChannelState */
	public static final Map<UUID, ChannelState> CHANNELING = new ConcurrentHashMap<>();
	/** 待同步玩家集合 */
	private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();

	/** 契灵玩家最后一次战斗 tick（受击或主动伤敌） */
	private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();
	/** 上次抗伤回复的 tick */
	private static final Map<UUID, Long> LAST_REGEN = new ConcurrentHashMap<>();
	/** 上次脱战 mana 回复的 tick（进化使魔专用） */
	private static final Map<UUID, Long> LAST_MANA_REGEN = new ConcurrentHashMap<>();
	/** 非战斗判定阈值：5s */
	public static final int OUT_OF_COMBAT_TICKS = 100;
	/** 抗伤回复间隔：15s */
	public static final int RESIST_REGEN_INTERVAL_TICKS = 300;
	/** 进化使魔脱战 mana 回复间隔：1s（脱战后每秒回1点） */
	public static final int UPGRADE_FOX_MANA_REGEN_INTERVAL_TICKS = 20;
	/** 进化使魔脱战每次回复的 mana 量 */
	public static final double UPGRADE_FOX_MANA_REGEN_AMOUNT = 1.0;

	/** 标记战斗发生（伤害进出契灵玩家时调用） */
	public static void markCombat(UUID playerUuid, long now) {
		if (playerUuid != null) LAST_COMBAT.put(playerUuid, now);
	}

	public static final class ChannelState {
		public final UUID targetUuid;
		public final long endTick;
		/** 1=主要技能真伤段(2s)，2=次要技能瞬移斩杀(1s) */
		public final int type;
		public ChannelState(UUID target, long endTick, int type) {
			this.targetUuid = target; this.endTick = endTick; this.type = type;
		}
	}

	// ============== 注册 ==============
	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MARKS.clear(); RED_LOCKOUT.clear(); CHANNELING.clear(); DIRTY.clear();
			LAST_COMBAT.clear(); LAST_REGEN.clear(); LAST_MANA_REGEN.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(MancianimaMarkManager::onTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			clearMark(server, id);
			CHANNELING.remove(id);
			DIRTY.remove(id);
			LAST_COMBAT.remove(id);
			LAST_REGEN.remove(id);
			LAST_MANA_REGEN.remove(id);
		});
	}

	// ============== Public API ==============

	public static Mark getMark(UUID markerUuid) { return MARKS.get(markerUuid); }

	public static boolean canRedRelock(UUID markerUuid, UUID targetUuid, long now) {
		Long end = RED_LOCKOUT.get(markerUuid + ":" + targetUuid);
		return end == null || now >= end;
	}

	public static String getColorString(UUID markerUuid, UUID targetUuid) {
		Mark m = MARKS.get(markerUuid);
		if (m == null || !m.targetUuid.equals(targetUuid)) return null;
		return colorString(m.color);
	}

	private static String colorString(MarkColor c) {
		return switch (c) { case YELLOW -> "yellow"; case ORANGE -> "orange"; case RED -> "red"; };
	}

	/** 设置或替换标记（YELLOW 起始）。如果替换会先清理上一个目标的副作用。 */
	public static void setMark(ServerPlayer marker, LivingEntity target, MarkColor color) {
		UUID markerId = marker.getUUID();
		Mark old = MARKS.get(markerId);
		if (old != null && (target == null || !old.targetUuid.equals(target.getUUID()))) {
			clearTargetSideEffects(marker.getServer(), old.targetUuid);
		}
		if (target == null) { MARKS.remove(markerId); DIRTY.add(markerId); return; }
		long now = ((ServerLevel) marker.level()).getGameTime();
		long expire = (old != null && old.targetUuid.equals(target.getUUID())) ? old.expireTick : (now + MARK_DURATION_TICKS);
		// 全新 YELLOW 标记 → 重置该 marker+target 的红标重锁 CD（允许立刻升红）
		boolean isFreshYellow = color == MarkColor.YELLOW
				&& (old == null || !old.targetUuid.equals(target.getUUID()));
		if (isFreshYellow) {
			RED_LOCKOUT.remove(markerId + ":" + target.getUUID());
		}
		// 全新标记（不同目标）→ colorSetTick 取 now；同目标延续 → 保留旧的 colorSetTick
		long setTick = (old != null && old.targetUuid.equals(target.getUUID())) ? old.colorSetTick : now;
		MARKS.put(markerId, new Mark(target.getUUID(), color, expire, setTick));
		DIRTY.add(markerId);
	}

	/** 升级到红色（必须当前是 ORANGE、过黄标 3s 阶段冷却且未在锁定期内）。 */
	public static boolean upgradeToRed(ServerPlayer marker, LivingEntity target) {
		Mark m = MARKS.get(marker.getUUID());
		if (m == null || !m.targetUuid.equals(target.getUUID())) return false;
		if (m.color != MarkColor.ORANGE) return false;
		long now = ((ServerLevel) marker.level()).getGameTime();
		if (now - m.colorSetTick < STAGE_GATE_TICKS) return false;
		if (!canRedRelock(marker.getUUID(), target.getUUID(), now)) return false;
		m.color = MarkColor.RED;
		m.expireTick = now + MARK_DURATION_TICKS;
		m.colorSetTick = now; // 重置阶段计时，红标→引爆需再等 3s
		RED_LOCKOUT.put(marker.getUUID() + ":" + target.getUUID(), now + RED_RELOCK_COOLDOWN_TICKS);
		DIRTY.add(marker.getUUID());
		return true;
	}

	public static void clearMark(MinecraftServer server, UUID markerUuid) {
		Mark m = MARKS.remove(markerUuid);
		if (m != null) clearTargetSideEffects(server, m.targetUuid);
		DIRTY.add(markerUuid);
	}

	public static boolean isRedMarkedBy(UUID markerUuid, UUID targetUuid) {
		Mark m = MARKS.get(markerUuid);
		return m != null && m.color == MarkColor.RED && m.targetUuid.equals(targetUuid);
	}

	public static UUID getMarkerOf(UUID targetUuid) {
		for (Map.Entry<UUID, Mark> e : MARKS.entrySet()) {
			if (e.getValue().targetUuid.equals(targetUuid)) return e.getKey();
		}
		return null;
	}

	// ============== Tick ==============
	private static void onTick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		RED_LOCKOUT.entrySet().removeIf(e -> e.getValue() <= now);

		Iterator<Map.Entry<UUID, Mark>> it = MARKS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Mark> e = it.next();
			UUID markerId = e.getKey();
			Mark m = e.getValue();
			ServerPlayer marker = server.getPlayerList().getPlayer(markerId);
			if (marker == null) { removeAndCleanup(server, it, m, markerId); continue; }
			IForm form = FormUtils.getCurrentForm(marker);
			if (form == null || !FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.getFormID())) {
				removeAndCleanup(server, it, m, markerId); continue;
			}
			if (now >= m.expireTick) { removeAndCleanup(server, it, m, markerId); continue; }
			Entity tgt = findEntity(server, m.targetUuid);
			if (!(tgt instanceof LivingEntity living) || !living.isAlive()) { removeAndCleanup(server, it, m, markerId); continue; }

			double dist = marker.distanceTo(living);
			MarkColor newColor = m.color;
			if (m.color == MarkColor.YELLOW) {
				if (dist <= RANGE_ORANGE) newColor = MarkColor.ORANGE;
			} else if (m.color == MarkColor.ORANGE) {
				if (dist > RANGE_ORANGE) newColor = MarkColor.YELLOW;
			} else { // RED
				if (dist > RANGE_RED_KEEP) newColor = MarkColor.YELLOW;
			}
			if (newColor != m.color) {
				m.color = newColor;
				DIRTY.add(markerId);
				if (newColor == MarkColor.YELLOW && living instanceof ServerPlayer sp) {
					playSoundToPlayer(sp, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
				}
			}
			// 持续辅助效果：仅 SLOWNESS（颜色高亮由 entity_glow 提供）
			if (now % 20 == 0) {
				living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 0, false, false, false));
			}
		}

		// 引导态 tick
		Iterator<Map.Entry<UUID, ChannelState>> cit = CHANNELING.entrySet().iterator();
		while (cit.hasNext()) {
			Map.Entry<UUID, ChannelState> e = cit.next();
			UUID markerId = e.getKey();
			ChannelState cs = e.getValue();
			ServerPlayer marker = server.getPlayerList().getPlayer(markerId);
			if (marker == null) { cit.remove(); continue; }
			Mark m = MARKS.get(markerId);
			boolean valid = m != null && m.color == MarkColor.RED && m.targetUuid.equals(cs.targetUuid);
			Entity tgt = findEntity(server, cs.targetUuid);
			boolean targetAlive = tgt instanceof LivingEntity le && le.isAlive();
			if (!valid || !targetAlive) {
				cit.remove();
				marker.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.ssc_addon.mancianima.channel_fail"), true);
				continue;
			}
			marker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false, false));
			// 蓄力期间第三人称特效：marker 周围 + target 周围 + 中间路径
			ServerLevel sw = (ServerLevel) marker.level();
			LivingEntity le = (LivingEntity) tgt;
			double mx = marker.getX(), my = marker.getY() + marker.getEyeHeight() * 0.6, mz = marker.getZ();
			double tx = le.getX(), ty = le.getY() + le.getBbHeight() * 0.5, tz = le.getZ();
			// marker 周围旋转粒子环（紫色魅力）
			for (int i = 0; i < 4; i++) {
				double angle = (now * 0.3 + i * Math.PI / 2.0);
				double rx = mx + Math.cos(angle) * 0.8;
				double rz = mz + Math.sin(angle) * 0.8;
				sw.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, rx, my, rz, 1, 0, 0, 0, 0);
			}
			// target 周围旋转粒子环（红色危险）
			for (int i = 0; i < 4; i++) {
				double angle = (-now * 0.3 + i * Math.PI / 2.0);
				double rx = tx + Math.cos(angle) * 1.0;
				double rz = tz + Math.sin(angle) * 1.0;
				sw.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, rx, ty, rz, 1, 0, 0, 0, 0);
			}
			// marker → target 路径上每 4 tick 撒一颗灵魂火光
			if (now % 4 == 0) {
				int steps = 6;
				for (int s = 1; s < steps; s++) {
					double t = s / (double) steps;
					double px = mx + (tx - mx) * t;
					double py = my + (ty - my) * t;
					double pz = mz + (tz - mz) * t;
					sw.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0.05, 0.05, 0.05, 0.0);
				}
			}
			if (now >= cs.endTick) {
				cit.remove();
				if (cs.type == 1) {
					MancianimaPrimary.executeChannelComplete(marker, (LivingEntity) tgt);
				} else if (cs.type == 2) {
					MancianimaTeleport.executeRedMarkChannelComplete(marker, (LivingEntity) tgt);
				}
			}
		}

		// 同步脏数据
		if (!DIRTY.isEmpty()) {
			Set<UUID> snapshot = new HashSet<>(DIRTY);
			DIRTY.clear();
			for (UUID id : snapshot) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) sendSyncPacket(p);
			}
		}

		// 抗伤回复：契灵玩家非战斗 5s 后，每 15s 回 1 抗伤
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			IForm form = FormUtils.getCurrentForm(sp);
			if (form == null || !FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.getFormID())) continue;
			UUID id = sp.getUUID();
			long lastCombat = LAST_COMBAT.getOrDefault(id, 0L);
			if (now - lastCombat < OUT_OF_COMBAT_TICKS) continue;
			long lastRegen = LAST_REGEN.getOrDefault(id, 0L);
			if (now - lastRegen < RESIST_REGEN_INTERVAL_TICKS) continue;
			int cur = PowerUtils.getResourceValue(sp, FormIdentifiers.MANCIANIMA_RESISTANCE);
			int max = PowerUtils.getResourceMax(sp, FormIdentifiers.MANCIANIMA_RESISTANCE);
			if (max <= 0) max = 2;
			if (cur >= max) { LAST_REGEN.put(id, now); continue; }
			PowerUtils.changeResourceValueAndSync(sp, FormIdentifiers.MANCIANIMA_RESISTANCE, 1);
			LAST_REGEN.put(id, now);
		}

		// 进化使魔脱战 mana 回复：脱战 5s 后每 1s 回 1 点 mana（需已解锁 mana_system 节点）
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			IForm form = FormUtils.getCurrentForm(sp);
			if (form == null || !FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(form.getFormID())) continue;
			// 仅在已解锁 mana_system 节点时生效（mana 条显示门控一致）
			if (!net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent.EVOLUTION
					.get(sp).isUnlocked(net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.FamiliarFoxTree.NODE_MANA)) continue;
			UUID id = sp.getUUID();
			long lastCombat = LAST_COMBAT.getOrDefault(id, 0L);
			if (now - lastCombat < OUT_OF_COMBAT_TICKS) continue;
			// 消耗 mana 后 5s 内暂停自动回复（regen_pause_timer 资源 > 0 表示在暂停窗口）
			int pauseTimer = PowerUtils.getResourceValue(sp, ResourceLocation.fromNamespaceAndPath("my_addon", "form_upgrade_familiar_fox_mana_regen_pause_pause_timer"));
			if (pauseTimer > 0) continue;
			long lastRegen = LAST_MANA_REGEN.getOrDefault(id, 0L);
			if (now - lastRegen < UPGRADE_FOX_MANA_REGEN_INTERVAL_TICKS) continue;
			double curMana = net.onixary.shapeShifterCurseFabric.mana.ManaUtils.getPlayerMana(sp);
			double maxMana = net.onixary.shapeShifterCurseFabric.mana.ManaUtils.getPlayerMaxMana(sp);
			if (maxMana <= 0 || curMana >= maxMana) { LAST_MANA_REGEN.put(id, now); continue; }
			// 灵视节点：mana 自动回复速率 +10%
			double regenAmount = UPGRADE_FOX_MANA_REGEN_AMOUNT;
			net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionComponent svComp =
						net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent.EVOLUTION.get(sp);
			if (svComp.isUnlocked(net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.FamiliarFoxTree.NODE_SPIRIT_VISION)) {
				regenAmount *= 1.1;
			}
			// 火环节点：mana 自动回复速率 +20%
			if (svComp.isUnlocked(net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.FamiliarFoxTree.NODE_FIRE_RING)) {
				regenAmount *= 1.2;
			}
			net.onixary.shapeShifterCurseFabric.mana.ManaUtils.gainPlayerMana(sp, regenAmount);
			LAST_MANA_REGEN.put(id, now);
		}
	}

	private static void removeAndCleanup(MinecraftServer server, Iterator<Map.Entry<UUID, Mark>> it, Mark m, UUID markerId) {
		it.remove();
		clearTargetSideEffects(server, m.targetUuid);
		DIRTY.add(markerId);
	}

	private static void clearTargetSideEffects(MinecraftServer server, UUID targetUuid) {
		if (server == null) return;
		Entity ent = findEntity(server, targetUuid);
		if (ent instanceof LivingEntity le) {
			le.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
	}

	// ============== Helpers ==============
	private static Entity findEntity(MinecraftServer server, UUID uuid) {
		for (ServerLevel w : server.getAllLevels()) {
			Entity e = w.getEntity(uuid);
			if (e != null) return e;
		}
		return null;
	}

	private static void sendSyncPacket(ServerPlayer player) {
		Mark m = MARKS.get(player.getUUID());
		FriendlyByteBuf buf = PacketByteBufs.create();
		if (m == null) {
			buf.writeInt(0);
		} else {
			buf.writeInt(1);
			buf.writeUUID(m.targetUuid);
			buf.writeUtf(colorString(m.color));
		}
		try {
			ServerPlayNetworking.send(player, new BytePayload(BytePayload.id(PACKET_MARK_SYNC), buf));
		} catch (Exception ignored) {}
	}

	/** 重连/换维度后强制把当前 mark 状态同步给该玩家，保证客户端 HUD/标记正确 */
	public static void resyncToPlayer(ServerPlayer player) {
		sendSyncPacket(player);
	}

	public static void playSoundToPlayer(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		Holder<SoundEvent> entry = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
		player.connection.send(new ClientboundSoundPacket(
				entry, SoundSource.PLAYERS,
				player.getX(), player.getY(), player.getZ(),
				volume, pitch, player.getRandom().nextLong()));
	}

	public static void broadcastSoundAtEntity(ServerLevel world, Entity entity, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}
}