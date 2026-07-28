package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Vex.class)
public abstract class FallenAllayVexMixin extends Mob {

	// Persistent target lock (vex UUID -> target entity UUID)
	@Unique
	private static final ConcurrentHashMap<UUID, UUID> VEX_TARGET = new ConcurrentHashMap<>();
	// Idle wander destination when no target (vex UUID -> [x, y, z])
	@Unique
	private static final ConcurrentHashMap<UUID, double[]> VEX_WANDER_DEST = new ConcurrentHashMap<>();
	// Ticks until a new wander point is picked
	@Unique
	private static final ConcurrentHashMap<UUID, Integer> VEX_WANDER_TIMER = new ConcurrentHashMap<>();

	protected FallenAllayVexMixin(EntityType<? extends Mob> entityType, Level world) {
		super(entityType, world);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void ssc_addon$onVexTick(CallbackInfo ci) {
		if (this.level().isClientSide()) return;

		Set<String> tags = this.getTags();
		String ownerUuidStr = null;
		boolean isFallenVex = false;

		for (String tag : tags) {
			if (tag.equals("ssc_fallen_allay_vex")) {
				isFallenVex = true;
			} else if (tag.startsWith("owner:")) {
				ownerUuidStr = tag.substring("owner:".length());
			}
		}

		if (!isFallenVex || ownerUuidStr == null) return;

		ServerLevel serverWorld = (ServerLevel) this.level();
		Player owner = serverWorld.getServer().getPlayerList().getPlayer(UUID.fromString(ownerUuidStr));

		// On death: clean up all maps, start CD if last vex
		if (this.isDeadOrDying() || this.getHealth() <= 0 || !this.isAlive()) {
			VEX_TARGET.remove(this.getUUID());
			VEX_WANDER_DEST.remove(this.getUUID());
			VEX_WANDER_TIMER.remove(this.getUUID());
			if (owner != null) {
				applyCooldownIfLast(owner, ownerUuidStr, serverWorld);
			}
			return;
		}

        if (owner == null) return;

        pinVexCd(owner);

        // Validate stored target; clear if dead/gone
        LivingEntity currentTarget = resolveTarget(serverWorld);

		// After a kill (or on first spawn), search around the VEX ITSELF for a new target
		if (currentTarget == null) {
			currentTarget = findBestTarget(owner, ownerUuidStr, serverWorld);
			if (currentTarget != null) {
				VEX_TARGET.put(this.getUUID(), currentTarget.getUUID());
			}
		}

		if (currentTarget != null) {
			// Has a target: hand off to vanilla ChargeTargetGoal — no position restriction
			this.setTarget(currentTarget);
		} else {
			// No target: manually wander within 10-block sphere around owner
			this.setTarget(null);
			wanderNearOwner(owner);
		}
	}

	/**
	 * Wander within a 10-block sphere around owner.
	 * Picks a new random destination every 40 ticks (or when the current one is reached).
	 * If already beyond 10 blocks, fly back toward owner instead.
	 */
	@Unique
	private void wanderNearOwner(Player owner) {
		UUID id = this.getUUID();
		double dist2ToOwner = this.distanceToSqr(owner);

		// If outside the sphere, fly back toward owner
		if (dist2ToOwner > 100.0) {
			net.minecraft.world.phys.Vec3 toOwner = owner.position().add(0, 1.0, 0).subtract(this.position());
			this.setDeltaMovement(toOwner.normalize().scale(Math.min(0.35, 0.1 + dist2ToOwner * 0.003)));
			VEX_WANDER_DEST.remove(id);
			VEX_WANDER_TIMER.put(id, 0);
			return;
		}

		// Count down to next destination pick
		int timer = VEX_WANDER_TIMER.getOrDefault(id, 0) - 1;
		VEX_WANDER_TIMER.put(id, timer);

		double[] dest = VEX_WANDER_DEST.get(id);
		boolean needNew = dest == null || timer <= 0
				|| this.distanceToSqr(dest[0], dest[1], dest[2]) < 1.0;

		if (needNew) {
			// Pick a random point inside the 10-block sphere around owner
			java.util.Random rand = new java.util.Random();
			double ox, oy, oz;
			do {
				ox = (rand.nextDouble() * 2 - 1) * 10.0;
				oy = (rand.nextDouble() * 2 - 1) * 10.0;
				oz = (rand.nextDouble() * 2 - 1) * 10.0;
			} while (ox * ox + oy * oy + oz * oz > 100.0); // reject points outside sphere
			dest = new double[]{owner.getX() + ox, owner.getY() + oy, owner.getZ() + oz};
			VEX_WANDER_DEST.put(id, dest);
			VEX_WANDER_TIMER.put(id, 40 + rand.nextInt(20));
		}

		// Fly toward the chosen wander point at casual speed
		net.minecraft.world.phys.Vec3 delta = new net.minecraft.world.phys.Vec3(
				dest[0] - this.getX(), dest[1] - this.getY(), dest[2] - this.getZ());
		double d2 = delta.lengthSqr();
		if (d2 > 0.25) {
			this.setDeltaMovement(delta.normalize().scale(Math.min(0.3, 0.1 + d2 * 0.008)));
		}
	}

	/**
	 * Resolve the stored target; clear slot if dead or absent.
	 */
	@Unique
	private LivingEntity resolveTarget(ServerLevel serverWorld) {
		UUID targetId = VEX_TARGET.get(this.getUUID());
		if (targetId == null) return null;
		Entity e = serverWorld.getEntity(targetId);
		if (e instanceof LivingEntity le && le.isAlive()) return le;
		VEX_TARGET.remove(this.getUUID());
		return null;
	}

	/**
	 * Find the best target centered on the VEX ITSELF (radius 16).
	 * Priority: marked (glowing) > player > hostile > other
	 */
	@Unique
	private LivingEntity findBestTarget(Player owner, String ownerUuidStr,
	                                    ServerLevel serverWorld) {
		AABB searchBox = this.getBoundingBox().inflate(16.0);
		List<LivingEntity> candidates = serverWorld.getEntitiesOfClass(LivingEntity.class, searchBox,
				e -> e != owner && e != this && e.isAlive()
						&& !(e instanceof Vex)
						&& !(e instanceof Raider));

		LivingEntity markedTarget = null;
		LivingEntity playerTarget = null;
		LivingEntity hostileTarget = null;
		LivingEntity otherTarget = null;

		boolean ownerIsServerPlayer = owner instanceof net.minecraft.server.level.ServerPlayer;
		net.minecraft.server.level.ServerPlayer serverOwner = ownerIsServerPlayer
				? (net.minecraft.server.level.ServerPlayer) owner : null;

		for (LivingEntity e : candidates) {
			// 始终跳过自己的驯服动物
			if (e instanceof net.minecraft.world.entity.TamableAnimal tameable
					&& owner.getUUID().equals(tameable.getOwnerUUID())) {
				continue;
			}
			// 始终跳过自己的恕魔（候选列表已过滤 VexEntity，但保留保险）
			if (e instanceof Vex vex
					&& vex.getTags().contains("owner:" + ownerUuidStr)) {
				continue;
			}
			// 统一白名单判定：受服务端总开关控制
			if (serverOwner != null
					&& net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils.isProtected(serverOwner, e)) {
				continue;
			}

			if (e.hasEffect(MobEffects.GLOWING)) {
				if (markedTarget == null) markedTarget = e;
			} else if (e instanceof Player) {
				if (playerTarget == null) playerTarget = e;
			} else if (e instanceof Monster) {
				if (hostileTarget == null) hostileTarget = e;
			} else {
				if (otherTarget == null) otherTarget = e;
			}
		}

		return markedTarget != null ? markedTarget :
				playerTarget != null ? playerTarget :
						hostileTarget != null ? hostileTarget :
								otherTarget;
	}

	/**
	 * While at least one vex is alive, keep vex_cd pinned at 400 so the skill can't be recast.
	 */
    @Unique
    private void pinVexCd(Player owner) {
        if (!(owner instanceof ServerPlayer serverOwner)) return;
        int currentCd = PowerUtils.getResourceValue(serverOwner, FormIdentifiers.FALLEN_ALLAY_VEX_CD);
        if (currentCd < 400) {
            PowerUtils.setResourceValueAndSync(serverOwner, FormIdentifiers.FALLEN_ALLAY_VEX_CD, 400);
        }
    }

    @Unique
    private void applyCooldownIfLast(Player owner, String ownerUuidStr, ServerLevel serverWorld) {
        boolean hasOtherVex = false;
        for (Entity v : serverWorld.getEntitiesOfClass(Vex.class, owner.getBoundingBox().inflate(128.0),
                e -> e != (Object) this && e.isAlive())) {
            if (v.getTags().contains("owner:" + ownerUuidStr) && v.getTags().contains("ssc_fallen_allay_vex")) {
                hasOtherVex = true;
                break;
            }
        }
        if (!hasOtherVex && owner instanceof ServerPlayer serverOwner) {
            PowerUtils.setResourceValueAndSync(serverOwner, FormIdentifiers.FALLEN_ALLAY_VEX_CD, 400);
        }
    }
}

