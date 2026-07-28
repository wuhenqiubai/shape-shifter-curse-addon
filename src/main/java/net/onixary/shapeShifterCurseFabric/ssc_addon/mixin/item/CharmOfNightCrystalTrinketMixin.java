package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import dev.emi.trinkets.api.SlotReference;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.items.trinkets.CharmOfNightCrystalTrinket;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 黑夜水晶吊坠（charm_of_night_crystal）寄生果蝠适配：
 * - 寄生果蝠固定血量上限、不受日照掉血影响，禁止其装备黑夜水晶吊坠。
 * - canEquip：果蝠形态下无法装入饰品槽。
 * - tick：若玩家在其它形态先戴上吊坠后再变成果蝠，自动卸下并归还（服务端，延迟到下一 tick 执行避免迭代冲突）。
 * 通过同签名方法软覆盖 SSC 主包 TrinketImpl mixin 的桥接（与 AmuletBraceletTrinketMixin 同理），仅作用于本物品类。
 * 其它形态（含吸血蝙蝠）不受影响，保持主包默认可装备。
 */
@Mixin(CharmOfNightCrystalTrinket.class)
public abstract class CharmOfNightCrystalTrinketMixin {

	@Unique
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		// 寄生果蝠禁止装备；其它形态保持主包默认（可装备）
		return !FormUtils.isBatParasiticFruit(entity);
	}

	@Unique
	public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
		// 玩家在其它形态戴上吊坠后变成寄生果蝠时，自动卸下并归还
		if (!(entity instanceof Player player)) return;
		if (player.level().isClientSide) return;
		if (!FormUtils.isBatParasiticFruit(player)) return;
		MinecraftServer server = player.getServer();
		if (server == null) return;
		// 延迟到下一 tick 执行，避免在 trinkets 遍历已装备饰品时修改饰品栏
		server.execute(() -> {
			if (!FormUtils.isBatParasiticFruit(player)) return;
			ItemStack current = slot.inventory().getItem(slot.index());
			if (current.isEmpty() || current.getItem() != (Item) (Object) this) return;
			slot.inventory().setItem(slot.index(), ItemStack.EMPTY);
			if (!player.getInventory().add(current)) {
				player.drop(current, false);
			}
			player.displayClientMessage(
					Component.translatable("msg.my_addon.charm_night_crystal_cant_equip_parasitic_fruit")
							.withStyle(ChatFormatting.RED),
					true
			);
		});
	}
}