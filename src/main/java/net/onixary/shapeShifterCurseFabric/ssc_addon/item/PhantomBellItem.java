package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;

public class PhantomBellItem extends TrinketItem {

	public static final int MAX_COOLDOWN = 1200;

	public PhantomBellItem(Properties settings) {
		super(settings);
	}

	@Override
	public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
		return FormUtils.isFamiliarFoxForm(entity);
	}

	/**
	 * 获取当前冷却剩余ticks (仅客户端有效)。
	 * 通过 {@link FabricLoader} 环境守卫 + {@link LifesavingCatTailClient} 隔离类，
	 * 保证专用服务端不会触发 {@code MinecraftClient} 的类链接。
	 */
	private int getCooldownRemaining() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return 0;
		return LifesavingCatTailClient.getCooldownRemaining(this, MAX_COOLDOWN);
	}

	/**
	 * 判断是否在冷却中 (仅客户端有效)。
	 */
	private boolean isOnCooldown() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return false;
		return LifesavingCatTailClient.isOnCooldown(this);
	}

	/**
	 * 根据CD进度获取颜色 (红色->橙色->绿色)
	 * progress: 0.0 = 刚开始冷却, 1.0 = 冷却完成
	 */
	private int getColorForProgress(float progress) {
		if (progress < 0.33f) {
			// 红色区域 (0% - 33%)
			return 0xFF5555; // 红色
		} else if (progress < 0.66f) {
			// 橙色区域 (33% - 66%)
			return 0xFFAA00; // 橙色
		} else {
			// 绿色区域 (66% - 100%)
			return 0x55FF55; // 绿色
		}
	}

	/**
	 * 根据CD进度获取Formatting颜色
	 */
	private ChatFormatting getFormattingForProgress(float progress) {
		if (progress < 0.33f) {
			return ChatFormatting.RED;
		} else if (progress < 0.66f) {
			return ChatFormatting.GOLD;
		} else {
			return ChatFormatting.GREEN;
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		// 只有在冷却中才显示进度条
		return isOnCooldown();
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		// 返回0-13的值，表示进度条的长度
		// 冷却剩余越少，进度条越长
		int remaining = getCooldownRemaining();
		float progress = 1.0f - ((float) remaining / MAX_COOLDOWN);
		return Math.round(13.0f * progress);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		int remaining = getCooldownRemaining();
		float progress = 1.0f - ((float) remaining / MAX_COOLDOWN);
		return getColorForProgress(progress);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		// 移除了"装备在项链栏生效"的提示
		tooltip.add(Component.translatable("item.ssc_addon.phantom_bell.desc.1").withStyle(ChatFormatting.BLUE));
		tooltip.add(Component.translatable("item.ssc_addon.phantom_bell.desc.2").withStyle(ChatFormatting.BLUE));

		// 显示CD状态
		if (isOnCooldown()) {
			int remainingTicks = getCooldownRemaining();
			int remainingSeconds = remainingTicks / 20;
			float progress = 1.0f - ((float) remainingTicks / MAX_COOLDOWN);
			ChatFormatting color = getFormattingForProgress(progress);
			tooltip.add(Component.translatable("item.ssc_addon.phantom_bell.cooldown", remainingSeconds).withStyle(color));
		} else {
			tooltip.add(Component.translatable("item.ssc_addon.phantom_bell.ready").withStyle(ChatFormatting.GREEN));
		}

		tooltip.add(Component.translatable("item.ssc_addon.phantom_bell.tooltip.exclusive").withStyle(ChatFormatting.LIGHT_PURPLE));
		super.appendHoverText(stack, context, tooltip, type);
	}
}