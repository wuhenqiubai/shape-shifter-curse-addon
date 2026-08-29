package net.jackcooper.shapeShifterCurseAddon.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

// 通用：附属形态切换成就触发器。条件可选指定 form_id，匹配则触发
public class OnTransformAddonForm extends AbstractCriterion<OnTransformAddonForm.Condition> {
	public static final Identifier ID = Identifier.of("my_addon", "on_transform_addon_form");

	@Override
	public Codec<Condition> getConditionsCodec() {
		return Condition.CODEC;
	}

	public void trigger(ServerPlayerEntity player, Identifier formId) {
		trigger(player, condition -> condition.matches(formId));
	}

	public record Condition(Optional<LootContextPredicate> player, Optional<Identifier> formId) implements AbstractCriterion.Conditions {
		public static final Codec<Condition> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
				LootContextPredicate.CODEC.optionalFieldOf("player").forGetter(Condition::player),
				Identifier.CODEC.optionalFieldOf("form_id").forGetter(Condition::formId)
			).apply(instance, Condition::new)
		);

		public boolean matches(Identifier triggered) {
			return formId.isEmpty() || formId.get().equals(triggered);
		}
	}
}
