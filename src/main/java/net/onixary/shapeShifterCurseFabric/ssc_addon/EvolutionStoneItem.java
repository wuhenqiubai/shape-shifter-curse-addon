package net.onixary.shapeShifterCurseFabric.ssc_addon;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.AdvancementUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;

public class EvolutionStoneItem extends Item {

	public EvolutionStoneItem(Properties settings) {
		super(settings);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 32;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		user.startUsingItem(hand);
		return InteractionResultHolder.consume(user.getItemInHand(hand));
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
		if (!world.isClientSide && user instanceof Player player) {
			ResourceLocation playerFormID = getPlayerFormID(player);

			ResourceLocation targetFormId = null;
			boolean canEvolve = false;

			if (playerFormID != null) {
				// Allow Wild Cat (Feral Cat SP) to evolve to Wild Cat SP
				if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "feral_cat_sp"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "wild_cat_sp");
					canEvolve = true;
				}
				// Allow Snow Fox 3 (permanent phase) to evolve to Snow Fox SP
				else if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "snow_fox_3"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "snow_fox_sp");
					canEvolve = true;
				}
				// Allow Allay to evolve to SP Allay
				else if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "allay_sp"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "allay_sp");
					canEvolve = true;
				}
				// 允许原版三阶段胡狼使用进化石进化为金沙岚
				else if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "anubis_wolf_3"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "golden_sandstorm_sp");
					canEvolve = true;
				}
				// 允许原版三阶段使魔使用进化石进化为契灵（与月髓环→灵界之主的路径并行存在）
				else if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "familiar_fox_3"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_mancianima");
					canEvolve = true;
				}
				// 允许进化使魔（SSCA 路线）使用进化石进化为契灵：需 50 级解锁两分支后才允许（门控在下方）
				else if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("my_addon", "upgrade_familiar_fox"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_mancianima");
					canEvolve = true;
				}
				// 允许原版三阶段蝙蝠使用进化石进化为寄生果蝠
				else if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "bat_3"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "bat_parasitic_fruit");
					canEvolve = true;
				}
				// 允许原版三阶段美西螈使用进化石进化为荧光幼灵
				else if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "axolotl_3"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "axolotl_fluorescent");
					canEvolve = true;				}
				// 允许进化美西螈（SSCA 路线）使用进化石进化为荧光幼灵：需 50 级解锁两分支后才允许（门控在下方）
				else if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("my_addon", "upgrade_axolotl"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "axolotl_fluorescent");
					canEvolve = true;				}				// 允许原版豹猫永久形态 ocelot_3 使用进化石进化为朔望（与月髓环→风灵并行存在，不同道具不冲突）
				else if (playerFormID.equals(ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "ocelot_3"))) {
					targetFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "ocelot_nova");
					canEvolve = true;
				}			}

			if (canEvolve) {
				// 进化形态门控（使魔 / 美西螈等所有 SSCA 进化路线起点形态）：必须先解锁全部分支才能用进化石继续进化
				if (player instanceof ServerPlayer spStone
						&& !EvolutionManager.canUpgradeFoxEvolve(spStone)) {
					player.displayClientMessage(Component.translatable("message.ssc_addon.evolution.fail.branches_locked").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
					return stack;
				}
				IForm formBase = RegPlayerForms.getPlayerForm(targetFormId);
				if (formBase != null) {
					// 带黑屏淡入淡出动画变身（startTransform），STUN 在动画期间定身
					TransformManager.startTransform(player, formBase, null);
					// 变身演出（黑屏淡入 IN + 淡出 OUT，共 160 tick）期间定身玩家，避免演出过程中走动
					player.addEffect(new MobEffectInstance(SscAddon.STUN_ENTRY,
							StaticParams.TRANSFORM_FX_DURATION_IN + StaticParams.TRANSFORM_FX_DURATION_OUT, 0, false, false, false));
					player.displayClientMessage(Component.translatable("message.ssc_addon.evolution_stone.success").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
					if (!player.getAbilities().instabuild) {
						stack.shrink(1);
					}
					// 成就：哦？玩家的样子......！- 首次使用进化石进化
					AdvancementUtils.grant(player, ResourceLocation.fromNamespaceAndPath("ssc_addon", "player_form_shock"));
				} else {
					// 走 lang key，避免硬编码（合规 #48）
					player.displayClientMessage(Component.translatable("message.ssc_addon.evolution_stone.target_not_found", targetFormId).withStyle(ChatFormatting.RED), false);
				}
			} else {
				player.displayClientMessage(Component.translatable("message.ssc_addon.evolution_stone.no_response").withStyle(ChatFormatting.RED), true);
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
		}
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		super.appendHoverText(stack, context, tooltip, type);
		String key = "item.ssc_addon.evolution_stone.tooltip";
		if (I18n.exists(key)) {
			String translated = I18n.get(key);
			for (String line : translated.split("\n")) {
				tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
			}
		} else {
			tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		}
	}

	private ResourceLocation getPlayerFormID(Player player) {
		IForm currentForm = FormUtils.getCurrentForm(player);
		return currentForm != null ? currentForm.getFormID() : null;
	}
}