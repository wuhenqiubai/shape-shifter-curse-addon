package net.onixary.shapeShifterCurseFabric.ssc_addon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.story.MoonScarStoryManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.AdvancementUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.MoonMarrowFormAdvancements;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpUpgradeItem extends Item {

	public static final ResourceKey<DamageType> CURSED_EROSION = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("my_addon", "cursed_erosion"));
	public static final ResourceKey<DamageType> CURSED_BURST = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("my_addon", "cursed_burst"));
	/**
	 * Map storing the mapping from Base Form ID to Target SP Form ID
	 * Key: Current Origin ID (Identifier), Value: Target Origin ID (Identifier)
	 */
	protected static final Map<ResourceLocation, ResourceLocation> UPGRADE_MAP = new HashMap<>();

	static {
		// Registers the SP evolution for Familiar Fox
		// From: shape-shifter-curse:form_familiar_fox_3
		// To:   my_addon:form_familiar_fox_sp
		registerUpgrade("shape-shifter-curse", "familiar_fox_3", "my_addon", "familiar_fox_sp");
		registerUpgrade("shape-shifter-curse", "axolotl_3", "my_addon", "axolotl_sp");
		registerUpgrade("shape-shifter-curse", "allay_sp", "my_addon", "fallen_allay_sp");
		registerUpgrade("shape-shifter-curse", "anubis_wolf_3", "my_addon", "anubis_wolf_sp");
		// 吸血蝙蝠：原版蝙蝠三阶段永久态 → desmodus（月髓环 + 诅咒之月夜进化）
		registerUpgrade("shape-shifter-curse", "bat_3", "my_addon", "bat_desmodus");
		// 风灵：原版豹猫永久形态 ocelot_3 → 风灵（月髓环 + 诅咒之月夜进化）
		registerUpgrade("shape-shifter-curse", "ocelot_3", "my_addon", "ocelot_wind_spirit");
		// 月织蛛：原版蜘蛛三阶段永久态 → 月织蛛（月髓环 + 诅咒之月夜进化）
		registerUpgrade("shape-shifter-curse", "spider_3", "my_addon", "spider_moon_weaver");
		// 进化使魔（SSCA 路线）→ 灵界之主：需 50 级解锁两分支后才允许（门控在 finishUsing）
		registerUpgrade("my_addon", "upgrade_familiar_fox", "my_addon", "familiar_fox_sp");
		// 进化美西螈（SSCA 路线）→ SP 美西螈：需 50 级解锁两分支后才允许（门控在 finishUsing）
		registerUpgrade("my_addon", "upgrade_axolotl", "my_addon", "axolotl_sp");
	}

	public SpUpgradeItem(Properties settings) {
		super(settings);
	}

	public static void registerUpgrade(String fromNamespace, String fromPath, String toNamespace, String toPath) {
		UPGRADE_MAP.put(ResourceLocation.fromNamespaceAndPath(fromNamespace, fromPath), ResourceLocation.fromNamespaceAndPath(toNamespace, toPath));
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
			// 剧情「月痕之力」：处于剧情触发的 red 形态时，月髓环可随时免费变回 sp 使魔（不消耗）
			if (MoonScarStoryManager.tryFreeRevertFromStoryRed(player)) {
				return stack;
			}
			ResourceLocation targetFormId = getTargetFormId(player);
			// 进化形态门控（使魔 / 美西螈等所有 SSCA 进化路线起点形态）：必须先解锁全部分支才能用月髃环继续进化
			if (targetFormId != null && player instanceof ServerPlayer spEvo
					&& !EvolutionManager.canUpgradeFoxEvolve(spEvo)) {
				player.displayClientMessage(Component.translatable("message.ssc_addon.evolution.fail.branches_locked").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), false);
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
				return stack;
			}
			boolean isCursedMoon = CursedMoon.isCursedMoonDay(world) && CursedMoon.isNight(world);
			boolean isValidForm = targetFormId != null;
			boolean isAlreadySP = isAlreadySP(player);

			if (isCursedMoon && isAlreadySP) {
				// Deadly Resonance: Already SP + Cursed Moon
				player.displayClientMessage(Component.translatable("message.ssc_addon.evolution.fail.already_sp").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), false);
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
				world.explode(player, player.getX(), player.getY(), player.getZ(), 6.0f, Level.ExplosionInteraction.BLOCK);
				player.hurt(world.damageSources().source(CURSED_BURST), 100000.0f);
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				// 成就：Boom Boom Boom! - 错误使用月髓环导致爆炸
				AdvancementUtils.grant(player, ResourceLocation.fromNamespaceAndPath("ssc_addon", "boom_boom_boom"));
			} else if (isValidForm && isCursedMoon) {
				// Success: Base Form + Cursed Moon

				// 5% Chance for Red Form (when upgrading to SP Fox)
				if (targetFormId != null && targetFormId.equals(ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_sp"))
						&& ResourceLocation.fromNamespaceAndPath("shape-shifter-curse", "familiar_fox_3").equals(getPlayerFormID(player))
						&& world.random.nextFloat() < 0.05f) {
					ResourceLocation redFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_red");
					IForm redForm = RegPlayerForms.getPlayerForm(redFormId);
					if (redForm != null) {
						// 带黑屏淡入淡出动画变身（原版 1.10.1 把 handleDirectTransform 拆为 startTransform/immediatelyTransform，
						// 此处对应原 handleDirectTransform(...,false) 的动画变身，应用 startTransform 而非 immediatelyTransform）
						TransformManager.startTransform(player, redForm, null);

						// 10 Minutes = 12000 ticks
						long expireTime = world.getGameTime() + 12000;
						player.addTag("ssc_addon_red_expire:" + expireTime);

						player.displayClientMessage(Component.translatable("message.ssc_addon.red_transformation_special").withStyle(ChatFormatting.GREEN), false);
					}
				}


				IForm formBase = RegPlayerForms.getPlayerForm(targetFormId);
				if (formBase != null) {
					// 带黑屏淡入淡出动画变身（startTransform），STUN 在动画期间定身
					TransformManager.startTransform(player, formBase, null);
					// 变身演出（黑屏淡入 IN + 淡出 OUT，共 160 tick）期间定身玩家，避免演出过程中走动
					player.addEffect(new MobEffectInstance(SscAddon.STUN_ENTRY,
							StaticParams.TRANSFORM_FX_DURATION_IN + StaticParams.TRANSFORM_FX_DURATION_OUT, 0, false, false, false));
					player.displayClientMessage(Component.translatable("message.ssc_addon.evolution.success").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
					if (!player.getAbilities().instabuild) {
						stack.shrink(1);
					}
					// 成就：今晚月色真美 - 首次使用月髓环成功变形
					AdvancementUtils.grant(player, ResourceLocation.fromNamespaceAndPath("ssc_addon", "tonight_moon_beautiful"));
					// 成就（可选）：每种目标形态对应的子成就，未注册时静默跳过
					ResourceLocation subAdv = MoonMarrowFormAdvancements.get(targetFormId);
					if (subAdv != null) {
						AdvancementUtils.grant(player, subAdv);
					}
				}
			} else {
				// Failure Logic
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}

				if (!isCursedMoon) {
					// No Moon Condition
					if (isValidForm || isAlreadySP) {
						// Correct Form (or SP), but No Moon
						player.displayClientMessage(Component.translatable("message.ssc_addon.evolution.fail.no_moon").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), false);
						world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
						// No damage in no moon condition
						// player.damage(player.getDamageSources().magic(), 0.0f);
					} else {
						// Wrong Form AND No Moon -> Fatal Fail
						player.displayClientMessage(Component.translatable("message.ssc_addon.evolution.fail.both").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), false);
						world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
						// No damage in no moon condition
					}
				} else {
					// Cursed Moon Present, but Wrong Form (and not SP)
					player.displayClientMessage(Component.translatable("message.ssc_addon.evolution.fail.bad_form").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), false);
					world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
					player.hurt(world.damageSources().source(CURSED_EROSION), 10.0f);
					player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 20, 1));
					player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 20, 1));
					player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20 * 20, 1));
				}
			}
		}
		return stack;
	}

	// Helper to check if player currently has a form that can be upgraded
    /*
    private boolean canUpgrade(PlayerEntity player) {
        return getTargetFormId(player) != null;
    }
     */

	private boolean isAlreadySP(Player player) {
		ResourceLocation playerFormID = getPlayerFormID(player);
		if (playerFormID == null) return false;
		for (ResourceLocation id : UPGRADE_MAP.values()) {
			if (id.equals(playerFormID)) {
				return true;
			}
		}
		return false;
	}

	private ResourceLocation getPlayerFormID(Player player) {
		IForm currentForm = FormUtils.getCurrentForm(player);
		return currentForm != null ? currentForm.getFormID() : null;
	}

	private ResourceLocation getTargetFormId(Player player) {
        /* OriginComponent component = ModComponents.ORIGIN.get(player);
         // Iterate through all origins the player has to see if any match our upgrade map
         for (Map.Entry<Identifier, Identifier> entry : UPGRADE_MAP.entrySet()) {
             Identifier currentFormId = entry.getKey();
             Identifier targetFormId = entry.getValue();

             // Check if player has the 'current' origin
             // We check specifically on the cursed_origin layer, or generally if layers aren't strict in the map
             // For safety, let's check if the player possesses this origin in ANY layer,
             // though typically it's the main layer.
             if (component.getOrigins().values().stream().anyMatch(o -> o.getIdentifier().equals(currentFormId))) {
                 return targetFormId;
             }
         }
         return null;
        */
		ResourceLocation playerFormID = getPlayerFormID(player);
		// playerFormID为null时，直接返回null，不进入遍历
		if (playerFormID == null) return null;
		for (Map.Entry<ResourceLocation, ResourceLocation> entry : UPGRADE_MAP.entrySet()) {
			ResourceLocation currentFormId = entry.getKey();
			if (playerFormID.equals(currentFormId)) {
				return entry.getValue();
			}
		}
		return null;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		super.appendHoverText(stack, context, tooltip, type);
		tooltip.add(Component.translatable("item.ssc_addon.sp_upgrade_thing.tooltip"));
	}
}