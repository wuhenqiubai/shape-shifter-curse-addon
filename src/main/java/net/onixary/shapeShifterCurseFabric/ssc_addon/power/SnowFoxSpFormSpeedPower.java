package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SnowFoxSpFormSpeedPower extends Power {

	private static final ResourceLocation MELEE_SPEED_UUID = ResourceLocation.parse("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
	private static final ResourceLocation RANGED_SPEED_UUID = ResourceLocation.parse("b2c3d4e5-f6a7-8901-bcde-f12345678901");

	private final ResourceLocation resourceId;
	private final double meleeSpeedBonus;
	private final double rangedSpeedPenalty;

	private int lastSwitchState = -1;

	public SnowFoxSpFormSpeedPower(PowerType<?> type, LivingEntity entity, ResourceLocation resourceId, double meleeSpeedBonus, double rangedSpeedPenalty) {
		super(type, entity);
		this.resourceId = resourceId;
		this.meleeSpeedBonus = meleeSpeedBonus;
		this.rangedSpeedPenalty = rangedSpeedPenalty;
		this.setTicking(true);
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(
				ResourceLocation.fromNamespaceAndPath("ssc_addon", "snow_fox_sp_form_speed"),
				new SerializableData()
						.add("resource", SerializableDataTypes.IDENTIFIER)
						.add("melee_speed_bonus", SerializableDataTypes.DOUBLE, 0.1)
						.add("ranged_speed_penalty", SerializableDataTypes.DOUBLE, -0.1),
				data -> (type, entity) -> new SnowFoxSpFormSpeedPower(
						type,
						entity,
						data.getId("resource"),
						data.getDouble("melee_speed_bonus"),
						data.getDouble("ranged_speed_penalty")
				)
		).allowCondition();
	}

	@Override
	public void tick() {
		if (entity.level().isClientSide()) return;

		int currentState = getSwitchState();

		if (currentState != lastSwitchState) {
			updateSpeedModifier(currentState);
			lastSwitchState = currentState;
		}
	}

	private int getSwitchState() {
		try {
			PowerHolderComponent powerHolder = PowerHolderComponent.KEY.get(entity);
			PowerType<?> powerType = PowerTypeRegistry.get(resourceId);
			Power power = powerHolder.getPower(powerType);
			if (power instanceof VariableIntPower variablePower) {
				return variablePower.getValue();
			}
		} catch (Exception e) {
			// Resource not found, default to melee state
		}
		return 0;
	}

	private void updateSpeedModifier(int state) {
		AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speedAttr == null) return;

		// Remove existing modifiers
		speedAttr.removeModifier(MELEE_SPEED_UUID);
		speedAttr.removeModifier(RANGED_SPEED_UUID);

		if (state == 0) {
			// Melee state: +10% speed
			AttributeModifier meleeModifier = new AttributeModifier(
					MELEE_SPEED_UUID,
					meleeSpeedBonus,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
			);
			speedAttr.addTransientModifier(meleeModifier);
		} else if (state == 1) {
			// Ranged state: -10% speed
			AttributeModifier rangedModifier = new AttributeModifier(
					RANGED_SPEED_UUID,
					rangedSpeedPenalty,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
			);
			speedAttr.addTransientModifier(rangedModifier);
		}
	}

	@Override
	public void onRemoved() {
		super.onRemoved();
		// Clean up modifiers when power is removed
		AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speedAttr != null) {
			speedAttr.removeModifier(MELEE_SPEED_UUID);
			speedAttr.removeModifier(RANGED_SPEED_UUID);
		}
	}
}