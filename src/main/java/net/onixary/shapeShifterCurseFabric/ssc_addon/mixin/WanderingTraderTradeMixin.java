package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 流浪商人交易注入 - 1%概率售卖阿努比斯权杖上的水晶
 * 价格：约20个钻石 + 约32个绿宝石（±5随机浮动）
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderTradeMixin {

	@Inject(method = "rewardTradeXp", at = @At("HEAD"))
	private void sscAddon$doNothing(MerchantOffer offer, CallbackInfo ci) {
		// 占位，确保mixin能正确加载
	}

	/**
	 * 在流浪商人填充交易列表后，以1%概率追加阿努比斯水晶交易
	 */
	@Inject(method = "updateTrades", at = @At("TAIL"))
	private void sscAddon$injectAnubisCrystalTrade(CallbackInfo ci) {
		WanderingTrader trader = (WanderingTrader) (Object) this;
		MerchantOffers offers = trader.getOffers();
		if (offers == null) return;

		// 1%概率
		if (trader.getRandom().nextFloat() >= 0.01f) return;

		// 随机价格：钻石 20±5, 绿宝石 32±5
		int diamondCount = 20 + trader.getRandom().nextIntBetweenInclusive(-5, 5);
		int emeraldCount = 32 + trader.getRandom().nextIntBetweenInclusive(-5, 5);

		// TradeOffer(第一槽位, 第二槽位, 输出, 最大使用次数, 经验, 价格乘数)
		// 仅可购买1次，不给商人经验，无价格波动
		MerchantOffer crystalOffer = new MerchantOffer(
				new ItemCost(Items.DIAMOND, diamondCount),
				java.util.Optional.of(new ItemCost(Items.EMERALD, emeraldCount)),
				new ItemStack(SscAddon.ANUBIS_CRYSTAL),
				1,   // 最大交易次数
				0,   // 商人经验
				0.0f // 价格乘数（无涨价）
		);
		offers.add(crystalOffer);
	}
}
