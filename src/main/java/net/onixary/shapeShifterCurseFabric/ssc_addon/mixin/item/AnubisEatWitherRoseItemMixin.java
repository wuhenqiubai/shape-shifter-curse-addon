/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 冥裁者「吃凋零玫瑰」：让原版凋零玫瑰（方块物品）在 SP阿努比斯手中变成可进食
 * （32t 读条进食动画，同吃牛排）。直接 mixin 而非 custom_edible，保证进食动画确定触发、
 * 无 5s 异步刷新延迟、主客机一致。
 *
 * - use：SP阿努比斯手持凋零玫瑰 → setCurrentHand 起手进食；其它形态放行（保留原版放置花）。
 * - getMaxUseTime / getUseAction：凋零玫瑰 → 32t + EAT（仅进食期间被读取；非阿努比斯不会起手，故不受影响）。
 * - finishUsing：SP阿努比斯服务端 → 施加阶梯凋零（首次凋零I 15s；重复吃等级+1上限凋零III，刷新20s）+ 消耗1。
 *
 * 看向方块时的默认放置由 SscAddon 的 UseBlockCallback 返回 FAIL 取消，使交互落到 use()。
 */
@Mixin(Item.class)
public abstract class AnubisEatWitherRoseItemMixin {

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$anubisWitherRoseUse(Level world, Player user, InteractionHand hand,
			CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
		ItemStack stack = user.getItemInHand(hand);
		if (!stack.is(Items.WITHER_ROSE)) return;
		if (!FormUtils.isForm(user, FormIdentifiers.ANUBIS_WOLF_SP)) return;
		user.startUsingItem(hand);
		cir.setReturnValue(InteractionResultHolder.consume(stack));
	}

	@Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$anubisWitherRoseMaxUseTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
		if (stack.is(Items.WITHER_ROSE)) {
			cir.setReturnValue(32);
		}
	}

	@Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$anubisWitherRoseUseAction(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
		if (stack.is(Items.WITHER_ROSE)) {
			cir.setReturnValue(UseAnim.EAT);
		}
	}

	@Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$anubisWitherRoseFinish(ItemStack stack, Level world, LivingEntity user,
			CallbackInfoReturnable<ItemStack> cir) {
		if (!stack.is(Items.WITHER_ROSE)) return;
		if (!(user instanceof ServerPlayer sp)) return;
		if (!FormUtils.isForm(sp, FormIdentifiers.ANUBIS_WOLF_SP)) return;
		MobEffectInstance current = sp.getEffect(MobEffects.WITHER);
		int amplifier;
		int duration;
		if (current == null) {
			// 首次吃 → 凋零 I，15 秒
			amplifier = 0;
			duration = 300;
		} else {
			// 重复吃 → 等级 +1（上限凋零 III = amplifier 2），刷新 duration 到 20 秒
			amplifier = Math.min(current.getAmplifier() + 1, 2);
			duration = 400;
		}
		sp.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, amplifier));
		if (!sp.getAbilities().instabuild) {
			stack.shrink(1);
		}
		cir.setReturnValue(stack);
	}
}