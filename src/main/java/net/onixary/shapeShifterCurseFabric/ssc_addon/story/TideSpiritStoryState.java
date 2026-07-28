package net.onixary.shapeShifterCurseFabric.ssc_addon.story;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 「潮汐之灵」剧情链的持久化存储（attach 到主世界 dat），跨重连 / 死亡重生 / 重启服务器均保留。
 * <ul>
 *     <li>tippedPlayers：已收到过"潮汐之灵"低语提示的玩家（每名玩家仅提示一次）。</li>
 * </ul>
 * <p>阿澪变身后为永久形态（不自动变回），故无需跟踪"剧情态玩家"集合（与月痕之力 red 不同）。
 */
public final class TideSpiritStoryState extends SavedData {
	public static final String KEY = "ssc_addon_tide_spirit_story";

	public final Set<UUID> tippedPlayers = new HashSet<>();

	public static TideSpiritStoryState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(
						TideSpiritStoryState::new,
						TideSpiritStoryState::fromNbt,
						DataFixTypes.LEVEL),
				KEY);
	}

	public static TideSpiritStoryState fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
		TideSpiritStoryState s = new TideSpiritStoryState();
		readUuidList(nbt, "tipped", s.tippedPlayers);
		return s;
	}

	private static void readUuidList(CompoundTag nbt, String key, Set<UUID> out) {
		ListTag list = nbt.getList(key, Tag.TAG_STRING);
		for (Tag e : list) {
			try {
				out.add(UUID.fromString(e.getAsString()));
			} catch (IllegalArgumentException ignored) {
			}
		}
	}

	@Override
	public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
		nbt.put("tipped", writeUuidList(tippedPlayers));
		return nbt;
	}

	private static ListTag writeUuidList(Set<UUID> set) {
		ListTag list = new ListTag();
		for (UUID id : set) {
			list.add(StringTag.valueOf(id.toString()));
		}
		return list;
	}
}