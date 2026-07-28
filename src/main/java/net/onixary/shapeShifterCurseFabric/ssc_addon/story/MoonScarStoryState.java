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
 * 「月痕之力」剧情链的持久化存储（attach 到主世界 dat），跨重连 / 死亡重生 / 重启服务器均保留。
 * <ul>
 *     <li>storyRedPlayers：当前处于"剧情触发的 red 形态"的玩家，可用月髓环随时免费变回 sp 使魔。</li>
 *     <li>tippedPlayers：已收到过"月痕之力"低语提示的玩家（每名玩家仅提示一次）。</li>
 * </ul>
 */
public final class MoonScarStoryState extends SavedData {
	public static final String KEY = "ssc_addon_moon_scar_story";

	public final Set<UUID> storyRedPlayers = new HashSet<>();
	public final Set<UUID> tippedPlayers = new HashSet<>();

	public static MoonScarStoryState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(
				new SavedData.Factory<>(
						MoonScarStoryState::new,
						MoonScarStoryState::fromNbt,
						DataFixTypes.LEVEL),
				KEY);
	}

	public static MoonScarStoryState fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
		MoonScarStoryState s = new MoonScarStoryState();
		readUuidList(nbt, "story_red", s.storyRedPlayers);
		readUuidList(nbt, "tipped", s.tippedPlayers);
		return s;
	}

	private static void readUuidList(CompoundTag nbt, String key, Set<UUID> out) {
		ListTag list = nbt.getList(key, Tag.TAG_STRING);
		for (Tag e : list) {
			try {
				out.add(UUID.fromString(e.getAsString()));
			} catch (IllegalArgumentException ignored) {
				// 跳过非法 UUID 字符串，避免存档损坏导致加载失败
			}
		}
	}

	@Override
	public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
		nbt.put("story_red", writeUuidList(storyRedPlayers));
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