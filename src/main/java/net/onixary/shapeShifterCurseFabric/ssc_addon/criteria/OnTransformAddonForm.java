package net.onixary.shapeShifterCurseFabric.ssc_addon.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

// 通用：附属形态切换成就触发器。条件可选指定 form_id，匹配则触发
public class OnTransformAddonForm extends SimpleCriterionTrigger<OnTransformAddonForm.Condition> {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("my_addon", "on_transform_addon_form");

	@Override
	public Codec<Condition> codec() {
		return Condition.CODEC;
	}

	public void trigger(ServerPlayer player, ResourceLocation formId) {
		trigger(player, condition -> condition.matches(formId));
	}

	public record Condition(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> formId) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<Condition> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
				ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(Condition::player),
				ResourceLocation.CODEC.optionalFieldOf("form_id").forGetter(Condition::formId)
			).apply(instance, Condition::new)
		);

		public boolean matches(ResourceLocation triggered) {
			return formId.isEmpty() || formId.get().equals(triggered);
		}
	}
}
