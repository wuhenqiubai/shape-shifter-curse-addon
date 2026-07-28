package net.onixary.shapeShifterCurseFabric.ssc_addon.effect;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 定身（ROOTED）效果：荧光幼灵法阵激光蓄力/释放期使用。
 *
 * <p>与 STUN 的区别：ROOTED <b>只锁移动</b>（-100% 移速 + 由 {@code StunnedKeyBindingMixin}
 * 屏蔽移动/跳跃键），<b>不锁视角</b>（不触发 STUN 的相机/鼠标锁）。释放期的视角限速由
 * 专门的 {@code ViewRateLimitMixin} 依 laser_state 资源单独处理。
 */
public class RootedEffect extends MobEffect {
	/** 固定 UUID，供孤儿清理按 UUID 精确移除。 */
	public static final UUID SPEED_MODIFIER_UUID = UUID.fromString("3F2A6C11-9D74-4B8E-A1C3-77E5D2B0A9F1");

	public RootedEffect() {
		super(MobEffectCategory.NEUTRAL, 0x33CCFF);
		this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
				ResourceLocation.parse(SPEED_MODIFIER_UUID.toString()), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return false;
	}
}