package net.onixary.shapeShifterCurseFabric.ssc_addon.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.LaserBeamEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 荧光幼灵「法阵激光」渲染器：前方朝准星的发光法阵 + 释放期的穿墙光柱（自发光、粗彩带）。
 *
 * <p>法阵渲染在玩家眼前 {@code arrayDist} 处、法平面垂直于准星方向（本地 +Z=准星）；
 * 蓄力/释放/消退全程显示、随时间自转。光柱在释放(phase1)/消退(phase2)沿 +Z 延伸，
 * 消退期半径随进度缩小。
 */
@Environment(EnvType.CLIENT)
public class FluorescentLaserRenderer extends EntityRenderer<LaserBeamEntity> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

	private static final float[] CYAN   = {0.35f, 0.90f, 1.00f, 0.85f};
	private static final float[] BLUE   = {0.35f, 0.55f, 1.00f, 0.85f};
	private static final float[] PURPLE = {0.70f, 0.40f, 1.00f, 0.85f};
	private static final float[] GOLD   = {1.00f, 0.85f, 0.40f, 0.90f};
	private static final float[] WHITE  = {0.90f, 0.97f, 1.00f, 0.90f};
	private static final float[] BEAM   = {0.55f, 0.92f, 1.00f, 0.45f};
	private static final float[] BEAM_CORE = {0.95f, 0.99f, 1.00f, 0.85f};

	// 与 LaserBeamEntity 一致（RELEASE_TICKS 仅作文档参考，渲染逻辑不直接读取故不在此声明）
	private static final int FADE_TICKS = 30;

	// 增强斜后方三法阵偏移（与 Manager 一致）
	private static final int ENH_ARRAY_COUNT = 3;
	private static final double ENH_ARRAY_BACK = 1.6;
	private static final double ENH_ARRAY_SIDE = 1.3;
	private static final double ENH_ARRAY_UP = 1.7;

	public FluorescentLaserRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(LaserBeamEntity entity, float yaw, float tickDelta, PoseStack matrices, MultiBufferSource vcp, int light) {
		// 海晶荧光坠增强单道：不依赖 owner 准星，实体本身就在法阵起点，按存的 dir 画缩型法阵 + 24 格光柱
		if (entity.isEnhanced()) {
			renderEnhanced(entity, tickDelta, matrices, vcp);
			return;
		}
		Entity owner = entity.level().getEntity(entity.getTrackedOwnerId());
		if (owner == null) return;

		float oyaw = Mth.lerp(tickDelta, owner.yRotO, owner.getYRot());
		float opitch = Mth.lerp(tickDelta, owner.xRotO, owner.getXRot());
		double yawR = Math.toRadians(oyaw), pitchR = Math.toRadians(opitch);
		double ax = -Math.sin(yawR) * Math.cos(pitchR);
		double ay = -Math.sin(pitchR);
		double az = Math.cos(yawR) * Math.cos(pitchR);
		double d = LaserBeamEntity.arrayDist();

		double ox = Mth.lerp(tickDelta, owner.xo, owner.getX());
		double oy = Mth.lerp(tickDelta, owner.yo, owner.getY()) + owner.getEyeHeight();
		double oz = Mth.lerp(tickDelta, owner.zo, owner.getZ());
		double ex = Mth.lerp(tickDelta, entity.xo, entity.getX());
		double ey = Mth.lerp(tickDelta, entity.yo, entity.getY());
		double ez = Mth.lerp(tickDelta, entity.zo, entity.getZ());

		matrices.pushPose();
		// 平移到法阵位置（玩家眼部 + 准星 * arrayDist）
		matrices.translate((ox - ex) + ax * d, (oy - ey) + ay * d, (oz - ez) + az * d);
		// 朝向准星：旋转后本地 +Z = 准星方向
		matrices.mulPose(Axis.YP.rotationDegrees(-oyaw));
		matrices.mulPose(Axis.XP.rotationDegrees(opitch));

		int phaseId = entity.getPhaseId();
		float pt = entity.getPhaseTick() + tickDelta;
		float age = pt + phaseId * 200f;   // 连续自转时间

		// 法阵/光柱用 lightning 渲染层（天然双面、发光、无视光照，任意角度可见）
		VertexConsumer buf = vcp.getBuffer(RenderType.lightning());

		// === 法阵（XY 平面，法线 +Z=准星）===
		drawArray(buf, matrices.last().pose(), matrices.last().normal(), age);

		// === 蓄力期四条白线（客户端绘制，无粒子残留）===
		if (phaseId == 0) {
			drawChargeLines(buf, matrices.last().pose(), matrices.last().normal(), pt, entity.beamLength());
			// 法阵核心发光粒子（END_ROD）：仅在非第一人称视角下生成
			// 第一人称下法阵紧贴相机，中央粒子会遮挡视线；第三人称（含自己按 F5 切换）可见
			spawnArrayCoreParticleIfThirdPerson(ox + ax * d, oy + ay * d, oz + az * d);
		}

		// === 光柱（release / fade）沿 +Z ===
		if (phaseId >= 1) {
			float radius = 2.5f;
			if (phaseId == 2) {   // FADE：半径缩小
				radius *= Math.max(0.0f, 1.0f - pt / FADE_TICKS);
			}
			if (radius > 0.02f) {
				drawBeam(buf, matrices.last().pose(), matrices.last().normal(),
						radius, (float) entity.beamLength());
			}
		}

		matrices.popPose();
		super.render(entity, yaw, tickDelta, matrices, vcp, light);
	}

	/** 海晶荧光坠增强渲染：在玩家斜后方左/右/上三位置画缩小旋转法阵（剩余数由实体同步）；发射时从对应法阵朝命中方向画光柱。 */
	private void renderEnhanced(LaserBeamEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vcp) {
		Entity owner = entity.level().getEntity(entity.getTrackedOwnerId());
		if (owner == null) return;
		float oyaw = Mth.lerp(tickDelta, owner.yRotO, owner.getYRot());
		float opitch = Mth.lerp(tickDelta, owner.xRotO, owner.getXRot());
		double yawR = Math.toRadians(oyaw), pitchR = Math.toRadians(opitch);
		double ax = -Math.sin(yawR) * Math.cos(pitchR);
		double ay = -Math.sin(pitchR);
		double az = Math.cos(yawR) * Math.cos(pitchR);
		double ox = Mth.lerp(tickDelta, owner.xo, owner.getX());
		double oy = Mth.lerp(tickDelta, owner.yo, owner.getY()) + owner.getEyeHeight();
		double oz = Mth.lerp(tickDelta, owner.zo, owner.getZ());
		double ex = Mth.lerp(tickDelta, entity.xo, entity.getX());
		double ey = Mth.lerp(tickDelta, entity.yo, entity.getY());
		double ez = Mth.lerp(tickDelta, entity.zo, entity.getZ());
		Vec3 aimV = new Vec3(ax, ay, az);
		Vec3 rightV = aimV.cross(new Vec3(0, 1, 0));
		if (rightV.lengthSqr() < 1.0e-6) rightV = new Vec3(1, 0, 0);
		rightV = rightV.normalize();
		Vec3 base = new Vec3(ox, oy, oz).subtract(aimV.scale(ENH_ARRAY_BACK));   // 斜后方
		Vec3[] pos = new Vec3[]{
				base.subtract(rightV.scale(ENH_ARRAY_SIDE)),   // 左
				base.add(rightV.scale(ENH_ARRAY_SIDE)),        // 右
				base.add(0, ENH_ARRAY_UP, 0)                      // 上
		};
		int left = entity.getArraysLeft();
		int firingIdx = entity.isFiring() ? entity.getFiringIdx() : -1;
		float age = entity.getPhaseTick() + tickDelta;
		float scale = entity.enhArrayScale();
		VertexConsumer buf = vcp.getBuffer(RenderType.lightning());
		// 剩余待发射法阵（斜后方，缩小旋转，实时朝锁定点预瞑——未同步时回退朝准星）
		Vec3 lock = entity.getLockPoint();
		for (int i = ENH_ARRAY_COUNT - left; i < ENH_ARRAY_COUNT; i++) {
			if (i < 0 || i > 2) continue;
			Vec3 p = pos[i];
			float ryaw = -oyaw, rpitch = opitch;   // 回退：朝玩家准星
			if (lock != null) {
				Vec3 d = lock.subtract(p);
				if (d.lengthSqr() > 1.0e-6) {
					d = d.normalize();
					ryaw = -(float) Math.toDegrees(Math.atan2(-d.x, d.z));
					rpitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-d.y, -1.0, 1.0)));
				}
			}
			matrices.pushPose();
			matrices.translate(p.x - ex, p.y - ey, p.z - ez);
			matrices.mulPose(Axis.YP.rotationDegrees(ryaw));
			matrices.mulPose(Axis.XP.rotationDegrees(rpitch));
			matrices.scale(scale, scale, scale);
			drawArray(buf, matrices.last().pose(), matrices.last().normal(), age);
			matrices.popPose();
		}
		// 发射光柱：从当前法阵位置指向「发射瞬间定格的世界锁定点」——法阵随玩家移动，激光始终连法阵与固定落点（追踪锁定）
		if (firingIdx >= 0 && firingIdx <= 2) {
			Vec3 fireLock = entity.getFireLock();
			Vec3 fp = pos[firingIdx];
			Vec3 d = fireLock.subtract(fp);
			double len = d.length();
			if (len > 1.0e-4) {
				Vec3 dir = d.scale(1.0 / len);
				float fyaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
				float fpitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-dir.y, -1.0, 1.0)));
				matrices.pushPose();
				matrices.translate(fp.x - ex, fp.y - ey, fp.z - ez);
				matrices.mulPose(Axis.YP.rotationDegrees(-fyaw));
				matrices.mulPose(Axis.XP.rotationDegrees(fpitch));
				matrices.pushPose();
				matrices.scale(scale, scale, scale);
				drawArray(buf, matrices.last().pose(), matrices.last().normal(), age);
				matrices.popPose();
				drawBeam(buf, matrices.last().pose(), matrices.last().normal(),
						entity.enhBeamRadius(), (float) len);   // 光柱长度=当前法阵到固定锁定点实时距离，与伤害判定一致
				matrices.popPose();
			}
		}
	}

	/**
	 * 法阵核心发光粒子（END_ROD）：仅在非第一人称视角下生成。
	 * <p>第一人称下法阵紧贴相机，中央粒子会遮挡视线 → 不生成；
	 * 第三人称（含施法者本人按 F5 切换）→ 在法阵位置生成 END_ROD。
	 * 该粒子为纯客户端粒子，每个玩家各自按自己视角判定，互不影响。
	 */
	private void spawnArrayCoreParticleIfThirdPerson(double wx, double wy, double wz) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		// 仅第三人称（含反向第三人称）生成；第一人称跳过
		if (mc.options.getCameraType() == CameraType.FIRST_PERSON) return;
		// 每帧生成 1 个，参数与服务端原 spawnParticles(count=2,delta=0.1,speed=0.01) 大致相当
		mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,
				wx, wy, wz,
				(mc.level.random.nextDouble() - 0.5) * 0.02,
				(mc.level.random.nextDouble() - 0.5) * 0.02,
				(mc.level.random.nextDouble() - 0.5) * 0.02);
	}

	// ==================== 蓄力四线（XY 平面四角 → +Z）====================
	private static final int LINE_EXTEND_END = 50;
	private static final int LINE_ROTATE_END = 130;
	private static final float CORNER_START = 2.4f;
	private static final float CORNER_END = 0.55f;

	private void drawChargeLines(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float pt, double beamLen) {
		float length;
		float spacing;
		float rot;
		if (pt <= LINE_EXTEND_END) {
			float p = pt / LINE_EXTEND_END;
			length = (float) beamLen * p;
			spacing = CORNER_START;
			rot = 0f;
		} else if (pt <= LINE_ROTATE_END) {
			float p = (pt - LINE_EXTEND_END) / (LINE_ROTATE_END - LINE_EXTEND_END);
			length = (float) beamLen;
			spacing = CORNER_START + (CORNER_END - CORNER_START) * (p * p);   // 加速收拢
			float smooth = p * p * (3 - 2 * p);
			rot = smooth * (float) (Math.PI * 6.0);
		} else {
			length = (float) beamLen;
			spacing = CORNER_END;
			rot = (float) (Math.PI * 6.0);
		}
		float halfW = 0.05f;
		for (int i = 0; i < 4; i++) {
			float ang = rot + i * (float) (Math.PI / 2) + (float) (Math.PI / 4);
			float ox = (float) Math.cos(ang) * spacing;
			float oy = (float) Math.sin(ang) * spacing;
			// 沿 +Z 方向的四边形带（ox,oy → ox,oy,length）
			quad(buf, pose, nrm,
					ox - halfW, oy - halfW, 0f, ox - halfW, oy - halfW, length,
					ox + halfW, oy + halfW, length, ox + halfW, oy + halfW, 0f, WHITE);
		}
	}

	// ==================== 法阵（XY 平面）====================
	private void drawArray(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float age) {
		float r = 2.2f;
		float spin = age * 0.03f;
		float w = r * 0.035f;
		ringBand(buf, pose, nrm, r * 0.98f, w * 1.3f, 64, spin, CYAN);
		ringBand(buf, pose, nrm, r * 0.72f, w, 56, -spin * 1.5f, PURPLE);
		ringBand(buf, pose, nrm, r * 0.45f, w, 40, spin * 2.0f, BLUE);
		hexagram(buf, pose, nrm, r * 0.82f, spin * 0.8f, w * 0.9f, GOLD);
		for (int i = 0; i < 24; i++) {
			double a = spin + Math.PI * 2 * i / 24;
			thickRadial(buf, pose, nrm, a, r * 0.82f, r * 0.98f, w * 0.7f, WHITE);
		}
	}

	/** XY 平面圆环带。 */
	private void ringBand(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float r, float halfW, int seg, float spin, float[] c) {
		float ri = r - halfW, ro = r + halfW;
		for (int i = 0; i < seg; i++) {
			double a1 = spin + Math.PI * 2 * i / seg;
			double a2 = spin + Math.PI * 2 * (i + 1) / seg;
			float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
			quad(buf, pose, nrm,
					ri * c1, ri * s1, 0f, ro * c1, ro * s1, 0f,
					ro * c2, ro * s2, 0f, ri * c2, ri * s2, 0f, c);
		}
	}

	private void hexagram(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float r, float spin, float halfW, float[] c) {
		triThick(buf, pose, nrm, r, spin + (float) (Math.PI / 2), halfW, c);
		triThick(buf, pose, nrm, r, spin + (float) (Math.PI / 6), halfW, c);
	}

	private void triThick(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float r, float start, float halfW, float[] c) {
		for (int i = 0; i < 3; i++) {
			double a1 = start + Math.PI * 2 * i / 3;
			double a2 = start + Math.PI * 2 * (i + 1) / 3;
			thickSeg(buf, pose, nrm,
					(float) (Math.cos(a1) * r), (float) (Math.sin(a1) * r),
					(float) (Math.cos(a2) * r), (float) (Math.sin(a2) * r), halfW, c);
		}
	}

	private void thickRadial(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, double angle, float r1, float r2, float halfW, float[] c) {
		float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
		float px = -sa * halfW, py = ca * halfW;
		quad(buf, pose, nrm,
				r1 * ca - px, r1 * sa - py, 0f, r2 * ca - px, r2 * sa - py, 0f,
				r2 * ca + px, r2 * sa + py, 0f, r1 * ca + px, r1 * sa + py, 0f, c);
	}

	/** XY 平面上从 (x1,y1) 到 (x2,y2) 的粗线。 */
	private void thickSeg(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float x1, float y1, float x2, float y2, float halfW, float[] c) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1.0e-4f) len = 1f;
		float px = -dy / len * halfW, py = dx / len * halfW;
		quad(buf, pose, nrm,
				x1 - px, y1 - py, 0f, x2 - px, y2 - py, 0f,
				x2 + px, y2 + py, 0f, x1 + px, y1 + py, 0f, c);
	}

	// ==================== 光柱（沿 +Z 的圆柱）====================
	private void drawBeam(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float radius, float length) {
		int seg = 28;
		for (int layer = 0; layer < 2; layer++) {
			float rr = (layer == 0) ? radius : radius * 0.45f;   // 外壳 + 内芯
			float[] col = (layer == 0) ? BEAM : BEAM_CORE;
			for (int i = 0; i < seg; i++) {
				double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
				float x1 = (float) (Math.cos(a1) * rr), y1 = (float) (Math.sin(a1) * rr);
				float x2 = (float) (Math.cos(a2) * rr), y2 = (float) (Math.sin(a2) * rr);
				quad(buf, pose, nrm, x1, y1, 0f, x1, y1, length, x2, y2, length, x2, y2, 0f, col);
			}
		}
	}

	// ==================== 双面四边形 ====================
	private void quad(VertexConsumer buf, Matrix4f pose, Matrix3f nrm,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, float[] col) {
		v(buf, pose, nrm, ax, ay, az, col);
		v(buf, pose, nrm, bx, by, bz, col);
		v(buf, pose, nrm, cx, cy, cz, col);
		v(buf, pose, nrm, dx, dy, dz, col);
		v(buf, pose, nrm, dx, dy, dz, col);
		v(buf, pose, nrm, cx, cy, cz, col);
		v(buf, pose, nrm, bx, by, bz, col);
		v(buf, pose, nrm, ax, ay, az, col);
	}

	private void v(VertexConsumer buf, Matrix4f pose, Matrix3f nrm, float x, float y, float z, float[] c) {
		buf.addVertex(pose, x, y, z).setColor(c[0], c[1], c[2], c[3]).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0f, 0f, 1f);
	}

	@Override
	public @NotNull ResourceLocation getTextureLocation(LaserBeamEntity entity) {
		return TEXTURE;
	}
}