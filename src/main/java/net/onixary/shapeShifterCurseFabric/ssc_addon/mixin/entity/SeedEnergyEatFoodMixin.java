/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SeedEnergyEatingHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 寄生果蝠形态：在玩家吃完种子（custom_edible 触发的 eatFood 流程）时，回调 SeedEnergyEatingHandler 加 1 点能量并推配额队列。
 * 选择 RETURN inject 是因为：原版 PlayerEntity.eatFood 完成后才算真正吃下；保留原版饥饿值/统计变化，仅在尾部叠加附属逻辑。
 */
@Mixin(LivingEntity.class)
public abstract class SeedEnergyEatFoodMixin {

    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void my_addon$onEatFoodReturn(Level world, ItemStack stack, FoodProperties foodComponent, CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClientSide) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return;
        if (stack.isEmpty()) return;
        if (!SeedEnergyEatingHandler.EDIBLE_SEEDS.contains(stack.getItem())) return;
        SeedEnergyEatingHandler.onSeedEaten(sp, stack);
    }
}