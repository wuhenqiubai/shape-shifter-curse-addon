package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionRegistry;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 灵能宝珠：仅进化形态（数据驱动 —— 某进化路线的起点形态）可用。
 *
 * <p>右键长按后打开「选择形态」界面（复用 {@code SscaFormSelectScreen} 转职模式），玩家选定一个进化形态确认后
 * 触发正常变身动画流程转职到该形态。当前所处形态在界面里灰显不可选。</p>
 *
 * <p>转职需 ≥ {@code JOB_CHANGE_COST}(=3) 个进化加点点数，倒退 3 个里程碑档；具体校验 / 扣点 / 变身 / 消耗道具
 * 全在服务端 {@code EvolutionManager.startJobChange}（防作弊）。本道具只负责「长按打开界面」这一步。</p>
 */
public class PsionicOrbItem extends Item {

    public PsionicOrbItem(Properties settings) {
        super(settings);
    }

    @Override
    public @NotNull UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 32;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(user.getItemInHand(hand));
    }

    @Override
    public @NotNull ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        if (!world.isClientSide && user instanceof ServerPlayer player) {
            IForm currentForm = FormUtils.getCurrentForm(player);
            ResourceLocation formId = currentForm != null ? currentForm.getFormID() : null;
            // 仅进化形态（某进化路线的起点形态）可用；数据驱动，未来新增进化形态自动接入
            if (formId != null && EvolutionRegistry.INSTANCE.getRouteByStartForm(formId) != null) {
                // 服务端发 S2C 包让客户端打开转职选择界面（此处不消耗道具，玩家确认转职成功时才消耗）
                SscAddonNetworking.sendOpenJobChange(player);
            } else {
                player.displayClientMessage(Component.translatable("message.ssc_addon.job_change.fail.not_evolution").withStyle(ChatFormatting.RED), true);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext tooltipContext, List<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, tooltipContext, tooltip, tooltipFlag);
        String key = "item.ssc_addon.psionic_orb.tooltip";
        if (I18n.exists(key)) {
            for (String line : I18n.get(key).split("\n")) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}