package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 契灵敲钟袭击的"每自然天 1 次"冷却的持久化存储（attach 到主世界 dat）。
 * 退出存档/重启服务器后仍然生效。
 */
public final class MancianimaAssaultState extends SavedData {
	public static final String KEY = "ssc_addon_mancianima_assault";
	public final Map<UUID, Long> lastRoll = new HashMap<>();

	public static MancianimaAssaultState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(
					new SavedData.Factory<>(
							MancianimaAssaultState::new,
							MancianimaAssaultState::fromNbt,
							DataFixTypes.LEVEL),
					KEY);
	}

	public static MancianimaAssaultState fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
		MancianimaAssaultState s = new MancianimaAssaultState();
		ListTag list = nbt.getList("last_roll", Tag.TAG_COMPOUND);
		for (Tag e : list) {
			CompoundTag c = (CompoundTag) e;
			s.lastRoll.put(c.getUUID("uuid"), c.getLong("time"));
		}
		return s;
	}

	@Override
	public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Long> e : lastRoll.entrySet()) {
			CompoundTag c = new CompoundTag();
			c.putUUID("uuid", e.getKey());
			c.putLong("time", e.getValue());
			list.add(c);
		}
		nbt.put("last_roll", list);
		return nbt;
	}
}