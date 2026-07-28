package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.List;

public class WaterSpearItem extends TridentItem {
	private static final int TICKS_PER_DURABILITY = 20;

	public WaterSpearItem(Item.Properties settings) {
		super(settings);
	}

	@Override
	public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof Player playerEntity) {
			int i = this.getUseDuration(stack, user) - remainingUseTicks;
			if (i >= 10) {
				float f = playerEntity.getYRot();
				float g = playerEntity.getXRot();
				float h = -Mth.sin(f * ((float) Math.PI / 180)) * Mth.cos(g * ((float) Math.PI / 180));
				float j = -Mth.sin(g * ((float) Math.PI / 180));
				float k = Mth.cos(f * ((float) Math.PI / 180)) * Mth.cos(g * ((float) Math.PI / 180));
				float l = Mth.sqrt(h * h + j * j + k * k);
				float m = 2.5F;
				h *= m / l;
				j *= m / l;
				k *= m / l;

				if (!world.isClientSide) {
					WaterSpearEntity waterSpear = new WaterSpearEntity(world, playerEntity, stack);
					waterSpear.shootFromRotation(playerEntity, playerEntity.getXRot(), playerEntity.getYRot(), 0.0F, m, 1.0F);
					stack.shrink(1);
					world.addFreshEntity(waterSpear);
					world.playSound(null, playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
					world.playSound(null, playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.5F, 1.2F);
				}

				playerEntity.awardStat(Stats.ITEM_USED.get(this));
			}
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.SPEAR;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		ItemStack itemStack = user.getItemInHand(hand);

		if (FormUtils.isAxolotlSP(user)) {
			user.startUsingItem(hand);
			return InteractionResultHolder.success(itemStack);
		}

		return InteractionResultHolder.fail(itemStack);
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));

		if (!target.level().isClientSide) {
			Level world = target.level();
			double x = target.getX();
			double y = target.getY() + target.getBbHeight() / 2;
			double z = target.getZ();

			List<Entity> nearbyEntities = world.getEntities(attacker, new AABB(x - 1.5, y - 1.5, z - 1.5, x + 1.5, y + 1.5, z + 1.5));
			for (Entity entity : nearbyEntities) {
				if (entity instanceof LivingEntity living && entity != attacker && entity != target) {
					living.hurt(world.damageSources().mobAttack(attacker), 4.0f);
				}
			}

			world.playSound(null, x, y, z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0F, 0.8F);
		}

		stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
		return true;
	}

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);

		if (!world.isClientSide) {
			if (entity instanceof Player player) {
				boolean isSpAxolotl = player.isCreative() || FormUtils.isAxolotlSP(player);

				if (!isSpAxolotl) {
					stack.setCount(0);
					return;
				}

			} else {
				stack.setCount(0);
				return;
			}
		}

		if (!world.isClientSide && entity instanceof LivingEntity livingEntity && world.getGameTime() % TICKS_PER_DURABILITY == 0) {
			stack.hurtAndBreak(1, livingEntity, EquipmentSlot.MAINHAND);
		}
	}

	@Override
	public int getEnchantmentValue() {
		return 0;
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
		return false;
	}

	@Override
	public ItemAttributeModifiers getDefaultAttributeModifiers() {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		builder.add(Attributes.ATTACK_DAMAGE,
				new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 8.0, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND);
		builder.add(Attributes.ATTACK_SPEED,
				new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND);
		return builder.build();
	}
}
