package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(ServerPlayer.class)
public class RedFormTickMixin {

	// 捕获玩家客户端语言设置，存入SscAddon.PLAYER_LANGUAGES
	@Inject(method = "updateOptions", at = @At("HEAD"))
	private void onSetClientSettings(ClientInformation clientOptions, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		SscAddon.PLAYER_LANGUAGES.put(player.getUUID(), clientOptions.language());
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void onTick(CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;

		// Performance check: Only run logic every 20 ticks (1 second)
		if (player.tickCount % 20 != 0) return;

		boolean isCursedMoon = CursedMoon.isCursedMoonDay(player.level());

		// Reset the attempt tag if it is not Cursed Moon
		if (!isCursedMoon && player.getTags().contains("ssc_addon_red_attempted")) {
			player.getTags().remove("ssc_addon_red_attempted");
		}

		// Potion Bag Logic
		IForm currentForm = FormUtils.getCurrentForm(player);
		boolean isRedForm = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_red"));

		// SP Form + Cursed Moon Transformation Logic
		if (currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_sp")) && isCursedMoon && !player.getTags().contains("ssc_addon_red_attempted")) {
			player.addTag("ssc_addon_red_attempted");
			// 5% Chance to transform to Red
			if (player.getRandom().nextFloat() < 0.05f) {
				ResourceLocation redFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_red");
				IForm redForm = RegPlayerForms.getPlayerForm(redFormId);
				if (redForm != null) {
					TransformManager.immediatelyTransform(player, redForm);

					// 10 Minutes = 12000 ticks
					long expireTime = player.level().getGameTime() + 12000;
					player.addTag("ssc_addon_red_expire:" + expireTime);

					player.displayClientMessage(Component.translatable("message.ssc_addon.red_transformation_special").withStyle(ChatFormatting.GREEN), false);
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 1.0F);
					return; // Exit after successful transformation
				}
			}
		}

		// === SP Allay Form: Auto-grant heal wand (slot 0) and jukebox (slot 1) ===
		boolean isAllaySp = currentForm != null && currentForm.getFormID().equals(ResourceLocation.fromNamespaceAndPath("my_addon", "allay_sp"));
		if (isAllaySp) {
			placeFormItemSafe(player, 0, SscAddon.ALLAY_HEAL_WAND);
			placeFormItemSafe(player, 1, SscAddon.ALLAY_JUKEBOX);
		} else {
			// Not Allay SP: Remove any allay items found
			for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(SscAddon.ALLAY_HEAL_WAND)) {
					player.getInventory().setItem(i, ItemStack.EMPTY);
				} else if (stack.is(SscAddon.ALLAY_JUKEBOX)) {
					player.getInventory().setItem(i, ItemStack.EMPTY);
				}
			}
		}

		if (isRedForm) {
			placeFormItemSafe(player, 8, SscAddon.POTION_BAG);
		} else {
			// Not Red Form: Remove any Potion Bag found
			for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(SscAddon.POTION_BAG)) {
					// Found a bag, drop its contents
						if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA) && stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).getUnsafe().contains("Items", 9)) {
							ListTag list = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).getUnsafe().getList("Items", 10);
						for (int j = 0; j < list.size(); ++j) {
							CompoundTag itemTag = list.getCompound(j);
								net.minecraft.core.HolderLookup.Provider registries = player.level().registryAccess();
								ItemStack contentStack = ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
							if (!contentStack.isEmpty()) {
								player.drop(contentStack, false, true);
							}
						}
					}
					// Remove bag itself
					player.getInventory().setItem(i, ItemStack.EMPTY);
				}
			}
		}

		Set<String> tagsToRemove = new HashSet<>();
		boolean shouldRevert = false;
		long currentTime = player.level().getGameTime();

		for (String tag : player.getTags()) {
			if (tag.startsWith("ssc_addon_red_expire:")) {
				try {
					long expireTime = Long.parseLong(tag.split(":")[1]);

					// Remaining time logic: simply check for expiration
					if (currentTime >= expireTime) {
						shouldRevert = true;
						tagsToRemove.add(tag);
					}
				} catch (NumberFormatException ignored) {
					tagsToRemove.add(tag); // Invalid tag, remove it
				}
			}
		}

		if (!tagsToRemove.isEmpty()) {
			for (String tag : tagsToRemove) {
				player.getTags().remove(tag);
			}
		}

		if (shouldRevert) {
			ResourceLocation spFormId = ResourceLocation.fromNamespaceAndPath("my_addon", "familiar_fox_sp");
			IForm spForm = RegPlayerForms.getPlayerForm(spFormId);
			if (spForm != null) {
				// Use setFormDirectly instead of handleDirectTransform to avoid animation
				TransformManager.immediatelyTransform(player, spForm);

				// Spawn a large amount of white particles to cover the player
				if (player.level() instanceof ServerLevel serverWorld) {
					// 100 CLOUD particles + 50 POOF particles
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 100, 0.5, 1.0, 0.5, 0.1);
					net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.5, 1.0, 0.5, 0.1);
				}


				// Clear the negative effects immediately (just in case they were applied, though we removed that logic)
				player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
				player.removeEffect(MobEffects.JUMP);

				// Send timeout message
				player.displayClientMessage(Component.translatable("message.ssc_addon.red_revert_timeout").withStyle(ChatFormatting.GREEN), false);
			}
		}
	}

	/**
	 * 安全地把指定形态物品放进固定槽位，避免覆盖玩家原有物品。
	 * 严格按用户要求执行：
	 *   1. 检测目标槽位是否已经是该形态物品；是则直接返回，什么都不做。
	 *   2. 把原物品取出（copy 后清空源槽），先尝试合并到背包内已存在的同种物品堆（包含快捷栏）。
	 *   3. 若仍有剩余，尝试塞入背包任意空槽（已排除目标槽位）。
	 *   4. 仍剩余则丢到地上，绝对不会静默删除。
	 * 最后把形态物品放进目标槽位。
	 */
	@org.spongepowered.asm.mixin.Unique
	private void placeFormItemSafe(ServerPlayer player, int targetSlot, Item formItem) {
		Inventory inv = player.getInventory();
		ItemStack existing = inv.getItem(targetSlot);

		// Step 1: 目标槽位已是该物品 → 不动
		if (existing.is(formItem)) return;

		// 先把原物品 copy 出来，并清空源槽，以便插入算法不会再把它放回原位
		ItemStack moved = existing.copy();
		inv.setItem(targetSlot, ItemStack.EMPTY);

		if (!moved.isEmpty()) {
			// Step 2: 优先合并到背包内同种物品（遍历整个主物品栏 0~35，含快捷栏，跳过 targetSlot）
			for (int i = 0; i < inv.items.size() && !moved.isEmpty(); ++i) {
				if (i == targetSlot) continue;
				ItemStack slotStack = inv.items.get(i);
				if (slotStack.isEmpty()) continue;
				if (!ItemStack.isSameItemSameComponents(slotStack, moved)) continue;
				int room = slotStack.getMaxStackSize() - slotStack.getCount();
				if (room <= 0) continue;
				int merge = Math.min(room, moved.getCount());
				slotStack.grow(merge);
				moved.shrink(merge);
			}

			// Step 3: 还有剩余 → 找空槽（排除 targetSlot）
			if (!moved.isEmpty()) {
				for (int i = 0; i < inv.items.size() && !moved.isEmpty(); ++i) {
					if (i == targetSlot) continue;
					if (!inv.items.get(i).isEmpty()) continue;
					inv.items.set(i, moved.copy());
					moved.setCount(0);
				}
			}

			// Step 4: 仍有剩余 → 丢到地上
			if (!moved.isEmpty()) {
				player.drop(moved, false, true);
			}
		}

		// 最后放入形态物品
		inv.setItem(targetSlot, new ItemStack(formItem));
	}
}