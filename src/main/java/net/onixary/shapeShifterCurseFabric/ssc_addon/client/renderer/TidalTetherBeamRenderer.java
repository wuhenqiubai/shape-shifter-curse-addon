package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 荧光幼灵「潮汐束缚（拴人）」守卫者激光渲染器（纯客户端）。
 *
 * <p>服务端每 10 tick 通过 {@code PACKET_TIDAL_TETHER} 把「水球 entityId + 被拴目标 entityId 列表」
 * 同步过来，存进 {@link #ACTIVE}；这里在 {@code WorldRenderEvents.AFTER_ENTITIES} 逐帧从水球向每个
 * 被拴目标画一条守卫者风格光束。条目在超过 expireTime 未刷新（或水球消失）后自动清除。
 */
@Environment(EnvType.CLIENT)
public final class TidalTetherBeamRenderer {

    /** 原版守卫者激光贴图。 */
    private static final ResourceLocation BEAM_TEXTURE = ResourceLocation.parse("textures/entity/guardian_beam.png");

    /** key = 水球 entityId，value = 被拴目标列表 + 过期时刻。 */
    private static final Map<Integer, Entry> ACTIVE = new ConcurrentHashMap<>();

    private TidalTetherBeamRenderer() {
    }

    private static final class Entry {
        final int[] targetIds;
        final long expireTime;

        Entry(int[] targetIds, long expireTime) {
            this.targetIds = targetIds;
            this.expireTime = expireTime;
        }
    }

    /** 收到服务端同步：更新某水球的被拴目标列表与过期时刻。 */
    public static void update(int orbId, int[] targetIds, long expireTime) {
        ACTIVE.put(orbId, new Entry(targetIds, expireTime));
    }

    /** 断线 / 切世界时清空，避免残留。 */
    public static void clear() {
        ACTIVE.clear();
    }

    /** {@code WorldRenderEvents.AFTER_ENTITIES} 回调：逐帧画所有活跃的拴人光束。 */
    public static void render(WorldRenderContext ctx) {
        if (ACTIVE.isEmpty()) return;
        MultiBufferSource vcp = ctx.consumers();
        if (vcp == null) return;
        Level world = Minecraft.getInstance().level;
        if (world == null) return;

        long now = world.getGameTime();
        float tickDelta = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        Camera cam = ctx.camera();
        Vec3 camPos = cam.getPosition();
        PoseStack ms = ctx.matrixStack();
        VertexConsumer vc = vcp.getBuffer(RenderType.entityCutoutNoCull(BEAM_TEXTURE));
        float age = (float) now + tickDelta;

        Iterator<Map.Entry<Integer, Entry>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> me = it.next();
            Entry en = me.getValue();
            if (now > en.expireTime) {
                it.remove();   // 过期未刷新 → 清除
                continue;
            }
            Entity orb = world.getEntity(me.getKey());
            if (orb == null) continue;   // 水球已消失 → 本帧不画（下帧过期清除）
            Vec3 op = orb.getPosition(tickDelta).add(0.0, orb.getBbHeight() * 0.5, 0.0);
            for (int tid : en.targetIds) {
                Entity tgt = world.getEntity(tid);
                if (tgt == null || !tgt.isAlive()) continue;
                Vec3 tp = tgt.getPosition(tickDelta).add(0.0, tgt.getBbHeight() * 0.5, 0.0);
                ms.pushPose();
                ms.translate(op.x - camPos.x, op.y - camPos.y, op.z - camPos.z);
                drawBeam(ms, vc, tp.x - op.x, tp.y - op.y, tp.z - op.z, age);
                ms.popPose();
            }
        }
    }

    /** 从本地原点（水球）沿 (relX,relY,relZ) 到目标画一条守卫者风格光束。 */
    private static void drawBeam(PoseStack ms, VertexConsumer vc, double relX, double relY, double relZ, float age) {
        double full = Math.sqrt(relX * relX + relY * relY + relZ * relZ);
        if (full < 1.0e-3) return;
        double inv = 1.0 / full;
        double dnx = relX * inv, dny = relY * inv, dnz = relZ * inv;
        // 原版守卫者朝向：绕 Y 转 (PI/2 - yaw)，再绕 X 转 pitch，使本地 +Y 指向目标
        float pitch = (float) Math.acos(Mth.clamp(dny, -1.0, 1.0));
        float yaw = (float) Math.atan2(dnz, dnx);

        ms.pushPose();
        ms.mulPose(Axis.YP.rotation(1.5707964f - yaw));
        ms.mulPose(Axis.XP.rotation(pitch));

        PoseStack.Pose e = ms.last();
        Matrix4f pose = e.pose();
        Matrix3f nrm = e.normal();

        float len = (float) full;
        float halfW = 0.14f;
        float scroll = -(age * 0.06f);       // 贴图沿光束滚动，形成流动感
        float v0 = scroll;
        float v1 = len * 0.7f + scroll;
        // 守卫者风格：紫 ↔ 青 脉动
        float pulse = 0.5f + 0.5f * Mth.sin(age * 0.35f);
        float cr = 0.45f + 0.40f * pulse;
        float cg = 0.75f;
        float cb = 1.00f;
        float ca = 0.85f;

        // 两片交叉四边形（getEntityCutoutNoCull 天然双面，单片即两面可见）
        for (int q = 0; q < 2; q++) {
            float ox = (q == 0) ? halfW : 0f;
            float oz = (q == 0) ? 0f : halfW;
            vtx(vc, pose, nrm, -ox, 0f, -oz, 0f, v1, cr, cg, cb, ca);
            vtx(vc, pose, nrm, -ox, len, -oz, 0f, v0, cr, cg, cb, ca);
            vtx(vc, pose, nrm, ox, len, oz, 1f, v0, cr, cg, cb, ca);
            vtx(vc, pose, nrm, ox, 0f, oz, 1f, v1, cr, cg, cb, ca);
        }
        ms.popPose();
    }

    private static void vtx(VertexConsumer vc, Matrix4f pose, Matrix3f nrm,
                            float x, float y, float z, float u, float v,
                            float r, float g, float b, float a) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0f, 1f, 0f);
    }
}