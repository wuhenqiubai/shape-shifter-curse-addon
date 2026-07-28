package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;

public class BlueFireRingEffect extends MobEffect {

	// 冻结水面的概率（6%，每次火环攻击间隔触发）
	private static final float FREEZE_CHANCE = 0.06f;
	// 冻结半径（与火环area_of_effect伤害半径保持一致）：默认6格，佩戴蓝火护符时3.6格
	private static final double FREEZE_RADIUS_DEFAULT = 6.0;
	private static final double FREEZE_RADIUS_AMULET = 3.6;
	// 火环攻击间隔：effects_loop interval=4 × internal_timer阈值4 = 16tick
	private static final int ATTACK_INTERVAL = 16;

	public BlueFireRingEffect() {
		super(MobEffectCategory.BENEFICIAL, 0x3366FF);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		// 与火环攻击间隔一致，每16tick触发一次
		return duration % ATTACK_INTERVAL == 0;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		Level world = entity.level();
		if (world.isClientSide()) return false;

		BlockPos center = entity.blockPosition();
		ServerLevel serverWorld = (ServerLevel) world;

		// 冻水范围跟随火环伤害范围：佩戴蓝火护符时3.6格，否则6格（与JSON area_of_effect一致）
		double freezeRadius = hasBlueFireAmulet(entity) ? FREEZE_RADIUS_AMULET : FREEZE_RADIUS_DEFAULT;
		int radiusCeil = Mth.ceil(freezeRadius);
		double radiusSq = freezeRadius * freezeRadius;

		// 遍历周围方块，有概率将水源方块转为冰霜行者冰（范围与火环攻击范围一致）
		for (int x = -radiusCeil; x <= radiusCeil; x++) {
			for (int z = -radiusCeil; z <= radiusCeil; z++) {
				// 圆形范围检查
				if (x * x + z * z > radiusSq) continue;

				// 检查脚下及脚下一格位置的水
				for (int yOff = -1; yOff <= 0; yOff++) {
					BlockPos pos = center.offset(x, yOff, z);

					if (world.getBlockState(pos).is(Blocks.WATER)
							&& world.getFluidState(pos).isSource()
							&& world.getBlockState(pos.above()).isAir()) {

						if (world.getRandom().nextFloat() < FREEZE_CHANCE) {
							world.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
							// 调度冰块融化（60-120tick后开始）
							serverWorld.scheduleTick(
									pos, Blocks.FROSTED_ICE,
									Mth.nextInt(world.getRandom(), 60, 120));
						}
					}
				}
			}
		}
		return false;
	}

	/** 检测玩家是否佩戴蓝火护符（与 ssc_addon:has_blue_fire_amulet 条件判断一致）。 */
	private static boolean hasBlueFireAmulet(LivingEntity entity) {
		if (!(entity instanceof Player player)) return false;
		return TrinketsApi.getTrinketComponent(player)
				.map(component -> component.isEquipped(SscAddon.BLUE_FIRE_AMULET))
				.orElse(false);
	}
}