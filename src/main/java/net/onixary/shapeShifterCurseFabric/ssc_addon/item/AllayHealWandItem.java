package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * SP悦灵单体治疗物品
 * 手持时准星对准20格内生物会高亮（白色框），
 * 右键治疗目标4血（2颗心），消耗12点能量，2.5秒冷却
 */
public class AllayHealWandItem extends Item {

	public static final float HEAL_AMOUNT = 8.0f;
	public static final int COOLDOWN_TICKS = 50; // 2.5 seconds
	public static final double MAX_RANGE = 20.0;
	public static final int MANA_COST = 12;

	private static final ResourceLocation MANA_RESOURCE_ID = ResourceLocation.fromNamespaceAndPath("my_addon", "form_allay_sp_mana_resource");
	private static final ResourceLocation MANA_COOLDOWN_ID = ResourceLocation.fromNamespaceAndPath("my_addon", "form_allay_sp_mana_cooldown_resource");

	public AllayHealWandItem(Properties settings) {
		super(settings);
	}

	/**
	 * Get the entity the player is looking at within MAX_RANGE
	 */
	@Nullable
	public static LivingEntity getTargetedEntity(Player player) {
		Vec3 eyePos = player.getEyePosition();
		Vec3 lookDir = player.getViewVector(1.0f);
		Vec3 endPos = eyePos.add(lookDir.scale(MAX_RANGE));

		// Get all entities in the range
		AABB searchBox = player.getBoundingBox().inflate(MAX_RANGE);
		Predicate<Entity> predicate = entity -> !entity.isSpectator() && entity.isPickable() && entity instanceof LivingEntity && entity != player;

		double closestDist = MAX_RANGE * MAX_RANGE;
		LivingEntity closestEntity = null;

		for (Entity entity : player.level().getEntities(player, searchBox, predicate)) {
			AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
			var optional = entityBox.clip(eyePos, endPos);
			if (optional.isPresent()) {
				double dist = eyePos.distanceToSqr(optional.get());
				if (dist < closestDist) {
					closestDist = dist;
					closestEntity = (LivingEntity) entity;
				}
			}
		}

		return closestEntity;
	}

	/**
	 * Check if there's a clear line of sight between player and target (no blocks in the way)
	 */
	public static boolean hasLineOfSight(Player player, LivingEntity target) {
		Vec3 eyePos = player.getEyePosition();
		Vec3 targetPos = target.getEyePosition();

		HitResult blockHit = player.level().clip(new ClipContext(
				eyePos, targetPos,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				player
		));

		// If the block hit is beyond the target or missed, we have line of sight
		if (blockHit.getType() == HitResult.Type.MISS) {
			return true;
		}

		double blockDist = eyePos.distanceToSqr(blockHit.getLocation());
		double targetDist = eyePos.distanceToSqr(targetPos);

		return blockDist >= targetDist;
	}

	private static int getManaValue(ServerPlayer player) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(MANA_RESOURCE_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				return variablePower.getValue();
			}
		} catch (Exception e) {
			// Resource not found
		}
		return 0;
	}

	// ===== Mana resource read/write =====

	private static void setManaValue(ServerPlayer player, int value) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(MANA_RESOURCE_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				variablePower.setValue(Math.max(0, value));
				// 只同步mana这一个power，避免全量sync重置飘浮power客户端的ascendProgress
				PowerHolderComponent.syncPower(player, powerType);
			}
		} catch (Exception e) {
			// Resource not found
		}
	}

	private static void triggerManaCooldown(ServerPlayer player) {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(player);
			PowerType<?> powerType = PowerTypeRegistry.get(MANA_COOLDOWN_ID);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				variablePower.setValue(70); // 3.5 seconds cooldown
				// 只同步cooldown这一个power
				PowerHolderComponent.syncPower(player, powerType);
			}
		} catch (Exception e) {
			// Resource not found
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);

		if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) {
			// Find the targeted entity
			LivingEntity target = getTargetedEntity(serverPlayer);

			if (target != null) {
				// 统一强化目标判定：受服务端白名单总开关控制
				// - 开关关闭：仅作用于非怪物/非敌对生物
				// - 开关开启：白名单空时仅治疗玩家/驯服宠物/owner-tag；非空时仅治疗白名单内对象
				if (!net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils.isBuffTarget(serverPlayer, target)) {
					serverPlayer.displayClientMessage(Component.translatable("item.ssc_addon.allay_heal_wand.no_target").withStyle(ChatFormatting.RED), true);
					return InteractionResultHolder.fail(stack);
				}
				// Check line of sight (no block obstruction)
				boolean hasLineOfSight = hasLineOfSight(serverPlayer, target);

				if (hasLineOfSight) {
					// Check mana
					int currentMana = getManaValue(serverPlayer);
					if (currentMana < MANA_COST) {
						serverPlayer.displayClientMessage(Component.translatable("item.ssc_addon.allay_heal_wand.no_mana").withStyle(ChatFormatting.RED), true);
						return InteractionResultHolder.fail(stack);
					}

					// Consume mana
					setManaValue(serverPlayer, currentMana - MANA_COST);

					// Trigger mana cooldown
					triggerManaCooldown(serverPlayer);

					// Heal the target
					target.heal(HEAL_AMOUNT);

					// Spawn heal particles
					ServerLevel serverWorld = (ServerLevel) world;
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.HEART,
							target.getX(), target.getY() + target.getBbHeight() + 0.5, target.getZ(),
							5, 0.3, 0.3, 0.3, 0.01);

					// Play heal sound
					// User hears private sound
					serverPlayer.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
					// Target and nearby players hear positional sound (exclude user to avoid double sound)
					world.playSound(serverPlayer, target.getX(), target.getY(), target.getZ(),
							SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.5f);

					// Set cooldown
					user.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

					return InteractionResultHolder.success(stack);
				} else {
					// Target is behind a wall
					serverPlayer.displayClientMessage(Component.translatable("item.ssc_addon.allay_heal_wand.blocked").withStyle(ChatFormatting.RED), true);
				}
			} else {
				serverPlayer.displayClientMessage(Component.translatable("item.ssc_addon.allay_heal_wand.no_target").withStyle(ChatFormatting.GRAY), true);
			}
		}

		return InteractionResultHolder.pass(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		tooltip.add(Component.translatable("item.ssc_addon.allay_heal_wand.tooltip").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("item.ssc_addon.allay_heal_wand.tooltip.exclusive").withStyle(ChatFormatting.LIGHT_PURPLE));
		super.appendHoverText(stack, context, tooltip, type);
	}
}