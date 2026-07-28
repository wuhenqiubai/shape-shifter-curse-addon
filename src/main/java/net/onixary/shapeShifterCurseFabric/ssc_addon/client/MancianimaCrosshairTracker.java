package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * 客户端：契灵准星目标缓存。
 * 每客户端 tick 重新做一次 32 格射线检测，命中第一个非自身/非旁观 LivingEntity，
 * 用于绿色高亮 entity_glow（仅本地玩家可见）。
 */
@Environment(EnvType.CLIENT)
public final class MancianimaCrosshairTracker {
	private MancianimaCrosshairTracker() {}

	private static volatile UUID currentTarget = null;
	private static final double MAX_DIST = 32.0;

	public static UUID getCurrentTarget() { return currentTarget; }

	public static boolean isCurrent(UUID uuid) {
		UUID cur = currentTarget;
		return cur != null && uuid != null && cur.equals(uuid);
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MancianimaCrosshairTracker::onTick);
	}

	private static void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null) { currentTarget = null; return; }
		Player player = mc.player;
		if (!isMancianima(player)) { currentTarget = null; return; }

		Vec3 eye = player.getEyePosition(1.0f);
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(MAX_DIST));

		// 先做方块遮挡判定，命中方块则限制最远可标记距离
		BlockHitResult blockHit = mc.level.clip(new ClipContext(eye, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double maxDistSq = MAX_DIST * MAX_DIST;
		if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
			maxDistSq = eye.distanceToSqr(blockHit.getLocation());
		}

		AABB box = player.getBoundingBox().expandTowards(look.scale(MAX_DIST)).inflate(1.0);
		// 过滤：跳过自身、玩家、已驯服宠物（与服务端默认白名单行为对齐）
		Predicate<Entity> filter = e -> e != player && e.isAlive() && e instanceof LivingEntity
				&& !(e instanceof Player)
				&& !(e instanceof TamableAnimal tame && tame.getOwnerUUID() != null);
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, box, filter, maxDistSq);
		if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
			currentTarget = hit.getEntity().getUUID();
		} else {
			currentTarget = null;
		}
	}

	private static boolean isMancianima(Player player) {
		try {
			IForm form = player.getComponent(RegPlayerFormComponent.PLAYER_FORM).nowForm;
			return form != null && FormIdentifiers.FAMILIAR_FOX_MANCIANIMA.equals(form.getFormID());
		} catch (Exception e) { return false; }
	}
}
