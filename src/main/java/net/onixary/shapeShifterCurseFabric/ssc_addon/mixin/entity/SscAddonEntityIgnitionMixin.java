package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SscIgnitedEntityAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class SscAddonEntityIgnitionMixin implements SscIgnitedEntityAccessor {

	@Unique
	private UUID sscAddon$igniterUuid;

	@Override
	public void sscAddon$setIgniterUuid(UUID uuid) {
		this.sscAddon$igniterUuid = uuid;
	}

	@Override
	public UUID sscAddon$getIgniterUuid() {
		return this.sscAddon$igniterUuid;
	}

	@Inject(method = "saveWithoutId", at = @At("HEAD"))
	private void injectWriteNbt(CompoundTag nbt, CallbackInfoReturnable<CompoundTag> cir) {
		if (this.sscAddon$igniterUuid != null) {
			nbt.putUUID("SscAddonIgniter", this.sscAddon$igniterUuid);
		}
	}

	@Inject(method = "load", at = @At("HEAD"))
	private void injectReadNbt(CompoundTag nbt, CallbackInfo ci) {
		if (nbt.contains("SscAddonIgniter")) {
			this.sscAddon$igniterUuid = nbt.getUUID("SscAddonIgniter");
		}
	}

	@ModifyArg(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 0)
	private DamageSource modifyFireDamageSource(DamageSource source) {
		if (source.is(DamageTypes.ON_FIRE)) {
			UUID igniter = this.sscAddon$getIgniterUuid();
			if (igniter != null) {
				Entity entity = (Entity) (Object) this;
				if (!entity.level().isClientSide) {
					Player player = entity.level().getPlayerByUUID(igniter);
					if (player != null) {
						return new DamageSource(source.typeHolder(), null, player);
					}
				}
			}
		}
		return source;
	}
}
