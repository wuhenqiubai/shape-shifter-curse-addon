package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AllaySPTotem {

	private static final String ACTIVE_TAG = "ssc_totem_active";
	private static final double RANGE = 20.0;
	// 使用UUID追踪持有激活图腾的玩家，避免存储实体引用导致跨维度/重连后引用过期
	private static final Set<UUID> playersWithActiveTotems = ConcurrentHashMap.newKeySet();

	private AllaySPTotem() {
		// Utility class
	}

	public static void init() {
		UseItemCallback.EVENT.register(AllaySPTotem::onUseItem);
		// Register to server tick event but only check players with active totems
		ServerTickEvents.END_SERVER_TICK.register(AllaySPTotem::onServerTick);
	}

	private static void onServerTick(MinecraftServer server) {
		// 每40tick（2秒）检查一次，平衡响应性和性能
		long currentTick = server.overworld().getGameTime();
		if (currentTick % 40 != 0) {
			return;
		}

		// 使用Iterator安全遍历并移除，避免ConcurrentModificationException
		Iterator<UUID> it = playersWithActiveTotems.iterator();
		while (it.hasNext()) {
			UUID uuid = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);

			// 玩家离线或不存在，移除追踪
			if (player == null) {
				it.remove();
				continue;
			}

			// 检查是否仍持有激活的图腾
			boolean stillHasActiveTotem = false;

			for (ItemStack stack : player.getInventory().items) {
				if (isActiveTotem(stack)) {
					stillHasActiveTotem = true;
					break;
				}
			}

			if (!stillHasActiveTotem) {
				for (ItemStack stack : player.getInventory().offhand) {
					if (isActiveTotem(stack)) {
						stillHasActiveTotem = true;
						break;
					}
				}
			}

			if (!stillHasActiveTotem) {
				it.remove();
				continue;
			}

			// 不再是SP悦灵时：关闭图腾并移除追踪
			if (!isSpAllay(player)) {
				deactivateAllTotems(player);
				it.remove();
			}
		}
	}

	private static void deactivateAllTotems(ServerPlayer player) {
		// Check main inventory and offhand
		boolean deactivatedAny = false;

		// Check main inventory
		for (ItemStack stack : player.getInventory().items) {
			if (isActiveTotem(stack)) {
				deactivateTotem(stack);
				deactivatedAny = true;
			}
		}

		// Check offhand
		for (ItemStack stack : player.getInventory().offhand) {
			if (isActiveTotem(stack)) {
				deactivateTotem(stack);
				deactivatedAny = true;
			}
		}

		if (deactivatedAny) {
			player.displayClientMessage(Component.translatable("message.ssc_addon.totem.deactivated"), true);
			player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
		}
	}

	private static void deactivateTotem(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> {
			nbt.remove(ACTIVE_TAG);
			nbt.remove("Enchantments");
			nbt.remove("HideFlags");
		});
	}

	private static InteractionResultHolder<ItemStack> onUseItem(Player player, net.minecraft.world.level.Level world, InteractionHand hand) {
		if (world.isClientSide) return InteractionResultHolder.pass(player.getItemInHand(hand));

		ItemStack stack = player.getItemInHand(hand);

		// Only function for Totem of Undying
		if (!stack.is(Items.TOTEM_OF_UNDYING)) {
			return InteractionResultHolder.pass(stack);
		}

		// Must be SP Allay locally checked
		if (!isSpAllay(player)) {
			return InteractionResultHolder.pass(stack);
		}

		// Toggle Active State
		CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		boolean isActive = customData.getUnsafe().getBoolean(ACTIVE_TAG);

		if (isActive) {
			// Deactivate
			CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> {
				nbt.remove(ACTIVE_TAG);
				nbt.remove("Enchantments");
				nbt.remove("HideFlags");
			});

			player.displayClientMessage(Component.translatable("message.ssc_addon.totem.deactivated"), true);
			player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);

			if (player instanceof ServerPlayer serverPlayer) {
				updateActiveTotemTracking(serverPlayer, false);
			}
		} else {
			// Activate
			CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> {
				nbt.putBoolean(ACTIVE_TAG, true);
				if (!nbt.contains("Enchantments")) {
					ListTag enchantments = new ListTag();
					CompoundTag unbreaking = new CompoundTag();
					unbreaking.putString("id", "minecraft:unbreaking");
					unbreaking.putShort("lvl", (short) 1);
					enchantments.add(unbreaking);
					nbt.put("Enchantments", enchantments);
					nbt.putInt("HideFlags", 1);
				}
			});

			player.displayClientMessage(Component.translatable("message.ssc_addon.totem.activated"), true);
			player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);

			if (player instanceof ServerPlayer serverPlayer) {
				updateActiveTotemTracking(serverPlayer, true);
			}
		}

		return InteractionResultHolder.success(stack);
	}

	/**
	 * Called by Mixin when an entity would die or use a totem.
	 *
	 * @param entity The entity attempting to use a totem.
	 * @return true if an Allay SP totem was used and prevented death.
	 */
	public static boolean tryUseAllayTotem(LivingEntity entity) {
		if (entity.level().isClientSide) return false;

		// Get nearby players within range
		AABB box = entity.getBoundingBox().inflate(RANGE);
		List<Player> nearbyPlayers = entity.level().getEntitiesOfClass(Player.class, box, p -> p instanceof ServerPlayer);

		for (Player player : nearbyPlayers) {
			if (!(player instanceof ServerPlayer serverPlayer)) continue;

			// a. Check if they are SP Allay
			if (!isSpAllay(serverPlayer)) continue;

			// b. Check whitelist：自我救援总是允许；救他人统一走 isBuffTarget
			//   - whitelistEnabled = false：仅作用于非怪物/非敌对生物
			//   - whitelistEnabled = true：白名单空时玩家/驯服宠物/owner-tag；非空时仅白名单成员
			if (entity != serverPlayer) {
				if (!net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils.isBuffTarget(serverPlayer, entity)) {
					continue;
				}
			}

			// c. Check inventory for Active Totem
			ItemStack activeTotem = findActiveTotem(serverPlayer);

			if (!activeTotem.isEmpty()) {
				// d. Consume totem
				activeTotem.shrink(1);

				// e. Trigger Effect on DYING ENTITY
				entity.setHealth(1.0F);
				entity.removeAllEffects();
				entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1)); // 5 seconds
				entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1)); // 45 seconds
				entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0)); // 40 seconds

				// Visuals: Totem of Undying particle/sound
				entity.level().broadcastEntityEvent(entity, (byte) 35);

				// Notify SP Allay
				serverPlayer.displayClientMessage(Component.translatable("message.ssc_addon.totem.triggered", entity.getDisplayName()), true);

				return true; // Prevent death
			}
		}

		return false; // Did not prevent death
	}

	private static ItemStack findActiveTotem(ServerPlayer player) {
		// Check hands
		if (isActiveTotem(player.getMainHandItem())) return player.getMainHandItem();
		if (isActiveTotem(player.getOffhandItem())) return player.getOffhandItem();

		// Check inventory main
		for (ItemStack stack : player.getInventory().items) {
			if (isActiveTotem(stack)) return stack;
		}

		return ItemStack.EMPTY;
	}

	private static boolean isActiveTotem(ItemStack stack) {
		// Check if item is Totem and has active tag
		return !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)
			&& stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe().getBoolean(ACTIVE_TAG);
	}

	private static boolean isSpAllay(Player player) {
		return player instanceof ServerPlayer sp && PowerUtils.isSpAllay(sp);
	}

	private static void updateActiveTotemTracking(ServerPlayer player, boolean hasActiveTotem) {
		if (hasActiveTotem) {
			playersWithActiveTotems.add(player.getUUID());
		} else {
			playersWithActiveTotems.remove(player.getUUID());
		}
	}

	public static void clearPlayer(ServerPlayer player) {
		playersWithActiveTotems.remove(player.getUUID());
	}

	public static void clearAll() {
		playersWithActiveTotems.clear();
	}
}