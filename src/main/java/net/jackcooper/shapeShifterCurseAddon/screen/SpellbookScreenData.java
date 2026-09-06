package net.jackcooper.shapeShifterCurseAddon.screen;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.RegistryByteBuf;

/**
 * 月尘魔法书配置界面开屏数据（jackcooper）。服务端开屏时把卷轴槽数与等级/法力快照发给客户端，
 * 客户端 {@link SpellbookScreenHandler} 用它初始化槽位与显示。对应 ExtendedScreenHandler 的 D 载荷。
 */
public record SpellbookScreenData(int slotCount, int level, int exp, int mana, int maxMana) {

	public static final PacketCodec<RegistryByteBuf, SpellbookScreenData> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SpellbookScreenData::slotCount,
			PacketCodecs.VAR_INT, SpellbookScreenData::level,
			PacketCodecs.VAR_INT, SpellbookScreenData::exp,
			PacketCodecs.VAR_INT, SpellbookScreenData::mana,
			PacketCodecs.VAR_INT, SpellbookScreenData::maxMana,
			SpellbookScreenData::new
	);
}
