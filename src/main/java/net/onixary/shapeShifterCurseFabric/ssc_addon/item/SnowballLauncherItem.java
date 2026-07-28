package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

import java.util.List;

public class SnowballLauncherItem extends Item {
	public static final int MAX_AMMO = 20;

	public SnowballLauncherItem(Properties settings) {
		super(settings);
	}

	public static int getAmmo(ItemStack stack) {
		return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).getUnsafe().getInt("Ammo");
	}

	public static void setAmmo(ItemStack stack, int ammo) {
		net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, nbt -> nbt.putInt("Ammo", Math.min(ammo, MAX_AMMO)));
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		// Start using (holding down)
		user.startUsingItem(hand);
		return InteractionResultHolder.consume(user.getItemInHand(hand));
	}

	@Override
	public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		int usedTicks = getUseDuration(stack, user) - remainingUseTicks;
		// Fire every 7 ticks (approx 1.15x speed of Bottled Blizzard which is 8 ticks)
		if (usedTicks % 7 == 0) {
			fire(world, user, stack);
		}
	}

	private void fire(Level world, LivingEntity entity, ItemStack stack) {
		int ammo = getAmmo(stack);
		boolean isCreative = false;

		if (entity instanceof Player player) {
			isCreative = player.getAbilities().instabuild;
		}

		if (ammo <= 0 && !isCreative) {
			if (entity instanceof Player player) {
				player.playSound(SoundEvents.DISPENSER_FAIL, 1.0F, 1.2F);
			}
			entity.releaseUsingItem();
			return;
		}

		if (!world.isClientSide) {
			Snowball snowball = new Snowball(world, entity);
			snowball.setItem(new ItemStack(net.minecraft.world.item.Items.SNOWBALL));
			// 2.25F velocity
			snowball.shootFromRotation(entity, entity.getXRot(), entity.getYRot(), 0.0F, 2.25F, 1.0F);
			world.addFreshEntity(snowball);

			world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F / (world.getRandom().nextFloat() * 0.2F + 0.9F));

			// Play Amethyst sound if Portable Fridge is equipped
			TrinketsApi.getTrinketComponent(entity).ifPresent(component -> {
				if (component.isEquipped(SscAddon.PORTABLE_FRIDGE)) {
					world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_FALL, SoundSource.PLAYERS, 1.0F, 1.0F);
				}
			});

			if (!isCreative) {
				setAmmo(stack, ammo - 1);
			}
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.min(13, Math.round((float) getAmmo(stack) / (float) MAX_AMMO * 13.0F));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xA0E0FF;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
		int ammo = getAmmo(stack);
		tooltip.add(Component.translatable("tooltip.ssc_addon.launcher.ammo", ammo, MAX_AMMO).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.ssc_addon.launcher.usage").withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, tooltip, type);
	}
}