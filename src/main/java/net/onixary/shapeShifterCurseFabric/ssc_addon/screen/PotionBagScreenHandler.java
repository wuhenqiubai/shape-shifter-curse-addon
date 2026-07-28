package net.onixary.shapeShifterCurseFabric.ssc_addon.screen;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.InfiniteEnergyPotionItem;

public class PotionBagScreenHandler extends AbstractContainerMenu {
	private final Container inventory;
	private final ItemStack bagStack;
	private final Player player;

	public PotionBagScreenHandler(int syncId, Inventory playerInventory, ItemStack bagStack) {
		super(SscAddon.POTION_BAG_SCREEN_HANDLER, syncId);
		this.bagStack = bagStack;
		this.player = playerInventory.player;
		// 1 Row x 9 Columns (Standard Single Chest Layout) = 9 Slots
		this.inventory = new SimpleContainer(9) {
			@Override
			public void setChanged() {
				super.setChanged();
				PotionBagScreenHandler.this.saveToNbt();
			}

			@Override
			public int getMaxStackSize() {
				return 8; // Max stack size: 8
			}

			@Override
			public boolean canPlaceItem(int slot, ItemStack stack) {
				return stack.getItem() instanceof PotionItem ||
						stack.getItem() instanceof SplashPotionItem ||
						stack.getItem() instanceof LingeringPotionItem ||
						stack.getItem() instanceof InfiniteEnergyPotionItem;
			}
		};

		// Load NBT
		loadFromNbt(bagStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).getUnsafe());
		// 清理已充满的无限药水空瓶标记，使打开时袋内显示正常名称
		cleanRechargedInfinitePotions();

		inventory.startOpen(playerInventory.player);

		// 1 Row x 9 Columns for Potion Bag
		// Standard start X=8, Y=18
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(inventory, col, 8 + col * 18, 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.getItem() instanceof PotionItem ||
							stack.getItem() instanceof SplashPotionItem ||
							stack.getItem() instanceof LingeringPotionItem ||
							stack.getItem() instanceof InfiniteEnergyPotionItem;
				}

				@Override
				public int getMaxStackSize(ItemStack stack) {
					// 无限压缩能量药水每瓶独立自充能，不可叠加（每格仅 1 个）
					if (stack.getItem() instanceof InfiniteEnergyPotionItem) {
						return 1;
					}
					if (stack.getItem() instanceof PotionItem ||
							stack.getItem() instanceof SplashPotionItem ||
							stack.getItem() instanceof LingeringPotionItem) {
						return 8;
					}
					return super.getMaxStackSize(stack);
				}
			});
		}

		// Player Inventory
		// Standard single chest GUI (1 row) places player inventory at Y=51 approx (actually 17 + 1*18 + 14 = 49)
		// With standard generic_54 texture, single row implies rows=1
		// Let's assume standard positioning:
		// Top chest rows end at Y = 17 + rows*18. Padding = 14. So player inv Y = 17 + 18 + 14 = 49
		for (int row = 0; row < 3; ++row) {
			for (int col = 0; col < 9; ++col) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 50 + row * 18));
			}
		}

		// Hotbar (Y = Player Inv End + 4 = 49 + 54 + 4 = 107) -> 108 usually for 1 row chest
		for (int col = 0; col < 9; ++col) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 108));
		}
	}

	// Client Constructor
	public PotionBagScreenHandler(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, ItemStack.EMPTY);
	}


	@Override
	public boolean stillValid(Player player) {
		return this.inventory.stillValid(player); // Simple close if too far
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack newStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack originalStack = slot.getItem();
			newStack = originalStack.copy();
			if (index < this.inventory.getContainerSize()) {
				if (!this.moveItemStackTo(originalStack, this.inventory.getContainerSize(), this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(originalStack, 0, this.inventory.getContainerSize(), false)) {
				return ItemStack.EMPTY;
			}

			if (originalStack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return newStack;
	}

	private void loadFromNbt(CompoundTag nbt) {
		if (nbt != null && nbt.contains("Items", 9)) {
			net.minecraft.core.RegistryAccess registries = player.level().registryAccess();
			ListTag list = nbt.getList("Items", 10);
			for (int i = 0; i < list.size(); ++i) {
				CompoundTag itemTag = list.getCompound(i);
				int slot = itemTag.getByte("Slot") & 255;
				if (slot >= 0 && slot < inventory.getContainerSize()) {
					ItemStack.parse(registries, itemTag).ifPresent(s -> inventory.setItem(slot, s));
				}
			}
		}
	}

	private void saveToNbt() {
		if (!bagStack.isEmpty()) {
			net.minecraft.core.RegistryAccess registries = player.level().registryAccess();
			ListTag list = new ListTag();
			for (int i = 0; i < inventory.getContainerSize(); ++i) {
				ItemStack s = inventory.getItem(i);
				if (!s.isEmpty()) {
					CompoundTag itemTag = new CompoundTag();
					itemTag.putByte("Slot", (byte) i);
					itemTag = (CompoundTag) s.save(registries, itemTag);
					list.add(itemTag);
				}
			}
			net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, bagStack, bn -> bn.put("Items", list));
		}
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		saveToNbt(); // Ensure save on close
	}

	/**
	 * 同步内容前先清理已充满的无限药水空瓶标记，使打开期间充满的药水实时恢复正常名称（仅服务端）。
	 */
	@Override
	public void broadcastChanges() {
		cleanRechargedInfinitePotions();
		super.broadcastChanges();
	}

	/**
	 * 清除袋内已充满的无限压缩能量药水的空瓶标记（仅服务端）。
	 * 药水袋存储物不走物品 inventoryTick，需在此主动清理，否则充满后仍显示「（空）」。
	 */
	private void cleanRechargedInfinitePotions() {
		if (player == null || player.level() == null || player.level().isClientSide) {
			return;
		}
		net.minecraft.world.level.Level world = player.level();
		for (int i = 0; i < inventory.getContainerSize(); ++i) {
			ItemStack s = inventory.getItem(i);
			if (s.getItem() instanceof InfiniteEnergyPotionItem
					&& InfiniteEnergyPotionItem.clearRechargeMarkIfDone(s, world)) {
				inventory.setChanged(); // 触发写回药水袋 NBT
			}
		}
	}

	/**
	 * 读取药水包指定槽位存储的物品（NBT 结构与 {@link #saveToNbt} 一致）。
	 * 供 PotionBagItem 的快捷投放栏（槽位 0）在不打开 GUI 时直接读取，多人下读的是服务端同步过来的手持物 NBT。
	 *
	 * @return 该槽位物品；不存在时返回 {@link ItemStack#EMPTY}
	 */
	public static ItemStack getStoredStack(ItemStack bagStack, int slot, HolderLookup.Provider registries) {
		net.minecraft.world.item.component.CustomData nbt = bagStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (nbt == null) return ItemStack.EMPTY;
		CompoundTag nbtCompound = nbt.getUnsafe();
		if (!nbtCompound.contains("Items", 9)) return ItemStack.EMPTY;
		ListTag list = nbtCompound.getList("Items", 10);
		for (int i = 0; i < list.size(); ++i) {
			CompoundTag itemTag = list.getCompound(i);
			if ((itemTag.getByte("Slot") & 255) == slot) {
				return ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 写回药水包指定槽位的物品（{@code stack} 为空则移除该槽位条目）。
	 * 供快捷投放栏消耗药水后更新存储，与 GUI 的 {@link #saveToNbt} 使用同一 NBT 结构。
	 */
	public static void setStoredStack(ItemStack bagStack, int slot, ItemStack stack, HolderLookup.Provider registries) {
		net.minecraft.world.item.component.CustomData nbt = bagStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
		net.minecraft.nbt.ListTag list = nbt.getUnsafe().contains("Items", 9) ? nbt.getUnsafe().getList("Items", 10) : new net.minecraft.nbt.ListTag();
		for (int i = list.size() - 1; i >= 0; --i) {
			if ((list.getCompound(i).getByte("Slot") & 255) == slot) {
				list.remove(i);
			}
		}
		if (!stack.isEmpty()) {
			CompoundTag itemTag = new CompoundTag();
			itemTag.putByte("Slot", (byte) slot);
			itemTag = (CompoundTag) stack.save(registries, itemTag);
			list.add(itemTag);
		}
		net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, bagStack, bn -> bn.put("Items", list));
	}

}
