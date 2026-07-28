package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class ParticleUtils {
	private ParticleUtils() {
	}

	// 广播半径：默认 512 格（技能主特效/一次性爆发，远距 + 最小粒子设置下可见）；
	// 充能/引导态「每 tick 持续」的高频粒子用 64 格（见 spawnParticlesNearby），减少远处玩家的高频网络包。
	private static final double BROADCAST_RANGE_SQ = 262144.0; // 512^2
	private static final double NEARBY_RANGE_SQ = 4096.0;      // 64^2

	/**
	 * 强制生成粒子效果，无视客户端粒子设置（最小/减少）。默认 512 格广播。
	 */
	public static <T extends ParticleOptions> void spawnParticles(ServerLevel world, T particle, Vec3 pos, int count, double offsetX, double offsetY, double offsetZ, double speed) {
		spawnParticles(world, particle, pos.x, pos.y, pos.z, count, offsetX, offsetY, offsetZ, speed);
	}

	/**
	 * 强制生成粒子效果，无视客户端粒子设置（最小/减少）。默认 512 格广播。
	 */
	public static <T extends ParticleOptions> void spawnParticles(ServerLevel world, T particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double speed) {
		spawnParticlesRanged(world, particle, x, y, z, count, offsetX, offsetY, offsetZ, speed, BROADCAST_RANGE_SQ);
	}

	/**
	 * 强制生成粒子，但只广播给 64 格内玩家——用于充能/引导态「每 tick 持续」的高频粒子，
	 * 减少远处玩家收到的高频网络包（技能主特效/一次性爆发仍用 512 格 spawnParticles 保证远距可见）。
	 */
	public static <T extends ParticleOptions> void spawnParticlesNearby(ServerLevel world, T particle, Vec3 pos, int count, double offsetX, double offsetY, double offsetZ, double speed) {
		spawnParticlesRanged(world, particle, pos.x, pos.y, pos.z, count, offsetX, offsetY, offsetZ, speed, NEARBY_RANGE_SQ);
	}

	/**
	 * 强制生成粒子，但只广播给 64 格内玩家（xyz 重载）。
	 */
	public static <T extends ParticleOptions> void spawnParticlesNearby(ServerLevel world, T particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double speed) {
		spawnParticlesRanged(world, particle, x, y, z, count, offsetX, offsetY, offsetZ, speed, NEARBY_RANGE_SQ);
	}

	private static <T extends ParticleOptions> void spawnParticlesRanged(ServerLevel world, T particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double speed, double maxDistSq) {
		if (world == null) return;
		try {
			// 使用 force=true 的 ParticleS2CPacket，使粒子在"最小"设置下仍然可见
			ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(particle, true, x, y, z,
					(float) offsetX, (float) offsetY, (float) offsetZ, (float) speed, count);
			for (ServerPlayer player : world.players()) {
				if (player.distanceToSqr(x, y, z) <= maxDistSq) {
					player.connection.send(packet);
				}
			}
		} catch (Exception e) {
		}
	}

	public static void spawnSnowflakeParticles(ServerLevel world, Vec3 pos) {
		spawnParticles(world, net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, pos, 5, 0.2, 0.2, 0.2, 0.02);
	}

	public static void spawnSnowflakeParticles(ServerLevel world, Vec3 pos, int count) {
		spawnParticles(world, net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, pos, count, 0.3, 0.3, 0.3, 0.1);
	}

	public static void spawnHitParticles(ServerLevel world, Vec3 pos) {
		spawnParticles(world, net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, pos, 20, 0.5, 0.5, 0.5, 0.1);
		spawnParticles(world, net.minecraft.core.particles.ParticleTypes.CLOUD, pos, 10, 0.3, 0.3, 0.3, 0.05);
	}

	public static void spawnTeleportParticles(ServerLevel world, Vec3 pos) {
		spawnParticles(world, net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, pos, 20, 0.3, 0.5, 0.3, 0.05);
	}

	public static void spawnSweepAttackParticles(ServerLevel world, Vec3 pos) {
		spawnParticles(world, net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, pos, 1, 0, 0, 0, 0);
	}

	// ===== 水花爆开（仿 RC-4 药水破碎）：水矛落地 / 涡流释放共用 =====
	private static final net.minecraft.core.particles.DustParticleOptions WATER_CYAN_DUST =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.20f, 0.62f, 0.92f), 1.5f);
	private static final net.minecraft.core.particles.BlockParticleOption WATER_PRISMARINE =
			new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
					net.minecraft.world.level.block.Blocks.PRISMARINE.defaultBlockState());

	/**
	 * 仿 RC-4 药水破碎的水花爆开特效：中心水花/泡泡/青色尘埃扩散云 + 向外抛射的水滴与青色碎块（带重力 → 抛物线）。
	 * 全部 force=true，最小粒子设置下也可见；scale 控制规模。
	 */
	public static void spawnWaterBurst(ServerLevel world, double x, double y, double z, double scale) {
		if (world == null) return;
		net.minecraft.util.RandomSource rnd = world.getRandom();
		spawnParticles(world, net.minecraft.core.particles.ParticleTypes.SPLASH, x, y, z, (int) (40 * scale), 0.6 * scale, 0.3, 0.6 * scale, 0.2);
		spawnParticles(world, net.minecraft.core.particles.ParticleTypes.BUBBLE, x, y, z, (int) (20 * scale), 0.5 * scale, 0.3, 0.5 * scale, 0.05);
		spawnParticles(world, WATER_CYAN_DUST, x, y + 0.3, z, (int) (18 * scale), 0.5 * scale, 0.35, 0.5 * scale, 0.02);
		int n = (int) (24 * scale);
		for (int i = 0; i < n; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double horiz = (0.3 + rnd.nextDouble() * 0.5) * scale;
			double vx = Math.cos(ang) * horiz;
			double vz = Math.sin(ang) * horiz;
			double vy = 0.35 + rnd.nextDouble() * 0.45;
			// count=0 → offset 作为速度向量；白色水花与青色海晶石碎块都带重力 → 抛物线
			spawnParticles(world, (i & 1) == 0 ? net.minecraft.core.particles.ParticleTypes.SPLASH : WATER_PRISMARINE,
					x, y + 0.2, z, 0, vx, vy, vz, 1.0);
		}
	}
}