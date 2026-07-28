package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;

import java.util.EnumSet;

public class IronGolemOfferFlowerToAllayGoal extends Goal {
	private static final int OFFER_FLOWER_CHANCE = 8000;
	private static final int OFFER_FLOWER_TICKS = 400;
	private static final TargetingConditions CLOSE_ALLAY_PREDICATE = TargetingConditions.forNonCombat()
			.range(6.0D)
			.selector(entity -> entity instanceof Player player
					&& FormUtils.isAllaySP(player)
					&& !player.isSpectator());

	private final IronGolem golem;
	private Player targetAllay;
	private int lookCountdown;

	public IronGolemOfferFlowerToAllayGoal(IronGolem golem) {
		this.golem = golem;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.golem.level().isDay()) return false;
		if (this.golem.getRandom().nextInt(OFFER_FLOWER_CHANCE) != 0) return false;

		AABB searchBox = this.golem.getBoundingBox().inflate(6.0D, 2.0D, 6.0D);
		this.targetAllay = this.golem.level().getNearestEntity(
				Player.class,
				CLOSE_ALLAY_PREDICATE,
				this.golem,
				this.golem.getX(),
				this.golem.getY(),
				this.golem.getZ(),
				searchBox
		);
		return this.targetAllay != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.lookCountdown > 0
				&& this.targetAllay != null
				&& this.targetAllay.isAlive()
				&& FormUtils.isAllaySP(this.targetAllay);
	}

	@Override
	public void start() {
		this.lookCountdown = this.adjustedTickDelay(OFFER_FLOWER_TICKS);
		this.golem.offerFlower(true);
	}

	@Override
	public void stop() {
		this.golem.offerFlower(false);
		this.targetAllay = null;
	}

	@Override
	public void tick() {
		if (this.targetAllay != null) {
			this.golem.getLookControl().setLookAt(this.targetAllay, 30.0F, 30.0F);
		}
		--this.lookCountdown;
	}
}
