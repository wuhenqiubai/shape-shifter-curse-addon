package net.onixary.shapeShifterCurseFabric.ssc_addon.action;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpDeathDomain;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AnubisWolfSpSummonWolves;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormWitherSand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormErosionBrand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormCounterBurst;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormDetonate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpFrostStorm;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpMeleeAbility;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.SnowFoxSpTeleportAttack;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPPortableBeacon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FrostBallEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SscIgnitedEntityAccessor;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.SkillBlocker;

import java.util.UUID;

public class SscAddonActions {

	private SscAddonActions() {
		// This utility class should not be instantiated
	}

	/**
	 * 已迁移至 Apoli 资源系统，保留为接口兼容
	 */
	public static void clearPlayer(UUID uuid) {
	}

	/**
	 * 已迁移至 Apoli 资源系统，保留为接口兼容
	 */
	public static void clearAll() {
	}

	public static void register() {
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "fallen_allay_scream"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer player) {
						net.minecraft.server.level.ServerLevel world = (net.minecraft.server.level.ServerLevel) player.level();

						// Particle and sound
						world.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.2f);
						net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(world, net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.5, 0.5, 0.5, 0.1);

						AABB box = player.getBoundingBox().inflate(25.0);

						// Glow non-whitelisted
						java.util.List<LivingEntity> entities = world.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());
						for (LivingEntity e : entities) {
							// 始终跳过：自己的驯服动物、自己的恕魔、劫掠阵营
							if (e instanceof net.minecraft.world.entity.TamableAnimal tameable && player.getUUID().equals(tameable.getOwnerUUID())) {
								continue;
							}
							if (e instanceof net.minecraft.world.entity.monster.Vex vex && vex.getTags().contains("owner:" + player.getStringUUID())) {
								continue;
							}
							if (e instanceof net.minecraft.world.entity.raid.Raider) {
								continue;
							}
							// 统一白名单判定：受服务端总开关控制
							if (WhitelistUtils.isProtected(player, e)) continue;
							e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0)); // 8s
						}

						// Kill projectiles
						java.util.List<Entity> projectiles = world.getEntities(player, box, e -> e instanceof net.minecraft.world.entity.projectile.Projectile);
						for (Entity p : projectiles) {
							net.onixary.shapeShifterCurseFabric.ssc_addon.util.ParticleUtils.spawnParticles(world, net.minecraft.core.particles.ParticleTypes.SMOKE, p.getX(), p.getY(), p.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
							p.discard();
						}
					}
				}
		));

		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "summon_fallen_allay_vex"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer player) {
						net.minecraft.server.level.ServerLevel world = (net.minecraft.server.level.ServerLevel) player.level();

						// Check if player already has vex
						boolean hasVex = false;
						for (Entity e : world.getEntitiesOfClass(net.minecraft.world.entity.monster.Vex.class, player.getBoundingBox().inflate(128.0), v -> true)) {
							if (e.getTags().contains("owner:" + player.getStringUUID()) && e.getTags().contains("ssc_fallen_allay_vex")) {
								hasVex = true;
								break;
							}
						}

						if (!hasVex) {
							for (int i = 0; i < 2; i++) {
								net.minecraft.world.entity.monster.Vex vex = net.minecraft.world.entity.EntityType.VEX.create(world);
								if (vex != null) {
									vex.moveTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
									// Spawn two vexes 180° apart with small velocity so they don't overlap
									double angle = i * Math.PI;
									vex.setDeltaMovement(Math.cos(angle) * 0.3, 0.3, Math.sin(angle) * 0.3);
									vex.addTag("ssc_fallen_allay_vex");
									vex.addTag("owner:" + player.getStringUUID());
									vex.setLimitedLife(700); // 35s * 20 ticks
									world.addFreshEntity(vex);
								}
							}
						}
					}
				}
		));
		registerEntity(PhantomBellTeleportAction.getFactory());

		// SP Allay Portable Beacon toggle
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "allay_sp_beacon_toggle"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer player) {
						AllaySPPortableBeacon.toggleBeacon(player);
					}
				}));

		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "item_cooldown"),
				new SerializableData()
						.add("item", SerializableDataTypes.ITEM)
						.add("duration", SerializableDataTypes.INT),
				(data, entity) -> {
					if (entity instanceof Player player) {
						player.getCooldowns().addCooldown(data.get("item"), data.getInt("duration"));
					}
				}));

		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "fire_breath"),
				new SerializableData()
						.add("distance", SerializableDataTypes.FLOAT)
						.add("damage", SerializableDataTypes.FLOAT)
						.add("duration", SerializableDataTypes.INT, 100),
				(data, entity) -> {
					if (!(entity instanceof LivingEntity living)) return;

					float distance = data.getFloat("distance");
					float damageAmount = data.getFloat("damage");
					int duration = data.getInt("duration");

					Vec3 eyePos = living.getEyePosition();
					Vec3 lookVec = living.getViewVector(1.0F);
					AABB box = living.getBoundingBox().inflate(distance).expandTowards(lookVec.scale(distance));
					living.level().getEntitiesOfClass(LivingEntity.class, box, target -> target != living).forEach(target -> {
						if (living instanceof ServerPlayer sPlayer && WhitelistUtils.isProtected(sPlayer, target))
							return;
						Vec3 targetVec = target.position().add(0, target.getBbHeight() / 2, 0).subtract(eyePos).normalize();
						double dot = lookVec.dot(targetVec);
						double distSq = living.distanceToSqr(target);

						if (dot > 0.8 && distSq < distance * distance) {
							Vec3 oldVelocity = target.getDeltaMovement();
							ResourceKey<DamageType> magicKey = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "magic"));
							if (target.hurt(target.damageSources().source(magicKey, living, living), damageAmount)) {
								target.setDeltaMovement(oldVelocity);
							}

							target.addEffect(new MobEffectInstance(SscAddon.FOX_FIRE_BURN_ENTRY, duration, 0)); // Duration from data

							if (living instanceof Player player && target instanceof SscIgnitedEntityAccessor accessor) {
								accessor.sscAddon$setIgniterUuid(player.getUUID());
							}
						}
					});

					// 火焰吐息路径上的水有15%概率变为冰霜行者冰
					if (!living.level().isClientSide() && living.level() instanceof ServerLevel serverWorld) {
						freezeWaterInCone(serverWorld, eyePos, lookVec, distance, 0.15f);
					}
				}));

		registerBiEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "set_on_fire_attributed"),
				new SerializableData()
						.add("duration", SerializableDataTypes.INT),
				(data, pair) -> {
					Entity actor = pair.getA();
					Entity target = pair.getB();
					if (actor == null || target == null) return;
					if (target.level().isClientSide()) return;

					int duration = data.getInt("duration");
					// target.setOnFireFor(duration); // Replaced with custom effect

					if (target instanceof LivingEntity livingTarget) {
						livingTarget.addEffect(new MobEffectInstance(SscAddon.FOX_FIRE_BURN_ENTRY, duration * 20, 0));
					}

					if (actor instanceof Player player && target instanceof SscIgnitedEntityAccessor accessor) {
						accessor.sscAddon$setIgniterUuid(player.getUUID());
					}
				}));

		registerBiEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "damage_target_from_actor"),
				new SerializableData()
						.add("amount", SerializableDataTypes.FLOAT)
						.add("damage_type", SerializableDataTypes.IDENTIFIER),
				(data, pair) -> {
					Entity actor = pair.getA();
					Entity target = pair.getB();
					if (actor == null || target == null) return;

					float amount = data.getFloat("amount");
					ResourceLocation damageTypeId = data.getId("damage_type");

					if (target instanceof LivingEntity) {
						ResourceKey<DamageType> damageTypeKey = ResourceKey.create(Registries.DAMAGE_TYPE, damageTypeId);
						Vec3 oldVelocity = target.getDeltaMovement();
						if (target.hurt(target.damageSources().source(damageTypeKey, null, actor), amount)) {
							target.setDeltaMovement(oldVelocity);
						}
					}
				}));

		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "force_pose"),
				new SerializableData()
						.add("pose", SerializableDataTypes.STRING),
				(data, entity) -> {
					String poseName = data.getString("pose");
					try {
						Pose pose = Pose.valueOf(poseName.toUpperCase());
						entity.setPose(pose);
						if (pose == Pose.SWIMMING) {
							entity.setSwimming(true);
						}
					} catch (IllegalArgumentException ignored) {
						// 忽略无效的pose
					}
				}));

		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "adaptive_water_jump"),
				new SerializableData()
						.add("base_y", SerializableDataTypes.FLOAT, 0.4f)
						.add("horizontal_momentum", SerializableDataTypes.FLOAT, 1.2f)
						.add("vertical_conversion", SerializableDataTypes.FLOAT, 0.5f),
				(data, entity) -> {
					if (entity instanceof LivingEntity living) {
						float baseY = data.getFloat("base_y");
						float hMom = data.getFloat("horizontal_momentum");
						float vConv = data.getFloat("vertical_conversion");

						Vec3 velocity = living.getDeltaMovement();
						double hSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

						// New Y: Base jump + portion of horizontal speed converted to lift + existing vertical velocity
						double newY = baseY + (hSpeed * vConv) + (velocity.y > 0 ? velocity.y : 0);

						// New Horizontal: maintain and boost momentum
						double newX = velocity.x * hMom;
						double newZ = velocity.z * hMom;

						living.setDeltaMovement(newX, newY, newZ);
						living.hurtMarked = true;
					}
				}));

		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "clear_aggro"),
				new SerializableData()
						.add("radius", SerializableDataTypes.DOUBLE, 64.0),
				(data, entity) -> {

					double radius = data.getDouble("radius");
					AABB box = entity.getBoundingBox().inflate(radius);
					entity.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, mob -> mob.getTarget() == entity).forEach(mob -> {
						mob.setTarget(null);
						mob.setLastHurtByMob(null);
					});
				}));

		// SP雪狐 - 雪刺冲刺技能
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "snow_fox_sp_dash"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer player) {
						if (SkillBlocker.isSkillBlocked(player, "snow_fox", "melee_primary")) {
							return;
						}
						SnowFoxSpMeleeAbility.execute(player);
					}
				}));

		// SP雪狐 - 瞬移攻击技能
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "snow_fox_sp_teleport_attack"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer player) {
						if (SkillBlocker.isSkillBlocked(player, "snow_fox", "melee_secondary")) {
							return;
						}
						SnowFoxSpTeleportAttack.execute(player);
					}
				}));

		// SP雪狐 - 法术冰球技能
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "snow_fox_sp_frost_ball"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer player) {
						if (SkillBlocker.isSkillBlocked(player, "snow_fox", "ranged_primary")) {
							return;
						}
						// 检查CD资源是否还在冷却中
						int currentCd = PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD);
						if (currentCd > 0) {
							return;
						}

						// 检查并消耗霜寒值
						int currentMana = PowerUtils.getResourceValue(player, FormIdentifiers.SNOW_FOX_RESOURCE);
						int manaCost = 15;
						if (currentMana < manaCost) {
							player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.0f);
							return;
						}
						PowerUtils.changeResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RESOURCE, -manaCost);
						// 设置回复冷却（5秒）
						PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_REGEN_COOLDOWN, 100);
						// 设置CD显示资源（5秒 = 100tick）
						PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SNOW_FOX_RANGED_PRIMARY_CD, 100);

						// 创建并发射冰球
						FrostBallEntity frostBall = new FrostBallEntity(player.level(), player);
						Vec3 lookVec = player.getViewVector(1.0F);
						frostBall.setDirection(lookVec);
						player.level().addFreshEntity(frostBall);

						// 播放发射音效
						player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
								SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0f, 0.8f);
					}
				}));

		// SP雪狐 - 冰风暴技能（点按开始蓄力）
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "snow_fox_sp_frost_storm"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer player) {
						if (SkillBlocker.isSkillBlocked(player, "snow_fox", "ranged_secondary")) {
							return;
						}
						SnowFoxSpFrostStorm.startCharging(player);
					}
				}));

		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "trigger_play_dead"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof LivingEntity living) {
						// 1. Effects
						// Duration 6s = 120 ticks
						int duration = 120;

						// 项链黄心改由 PlayingDeadEffect 每10tick累积，不再用 Absorption 效果

						// visible=false to hide icon（回血改由 PlayingDeadEffect 每10tick结算）
						living.addEffect(new MobEffectInstance(SscAddon.PLAYING_DEAD_ENTRY, duration, 0, false, false, false));
						living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, false));
						living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 10, false, false));

						// 2. Clear Aggro
						double radius = 64.0;
						AABB box = living.getBoundingBox().inflate(radius);
						living.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, mob -> mob.getTarget() == living).forEach(mob -> {
							mob.setTarget(null);
							mob.setLastHurtByMob(null);
						});

						// 3. Force Pose
						living.setPose(Pose.SLEEPING);

						// 4. 设置CD显示资源
						if (living instanceof ServerPlayer sp) {
							PowerUtils.setResourceValueAndSync(sp, FormIdentifiers.SP_SECONDARY_CD, 620);
						}

					}
				}));

		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "swim_jump_out"),
				new SerializableData().add("multiplier", SerializableDataTypes.FLOAT, 2.0F),
				(data, entity) -> {
					// Must be in swimming pose (sprinting in water) to trigger
					if (entity.isSwimming()) {
						Vec3 velocity = entity.getDeltaMovement();
						double vy = velocity.y;

						// Only boost if moving upwards
						// AND looking up (Pitch < -20) for the special "Jump Out" mechanics
						if (vy > 0) {
							double newVy = vy;
							double newVx = velocity.x;
							double newVz = velocity.z;

							// Special Jump Boost: Only when looking up (Pitch < -20)
							// Triggers explosive jump out of water
							if (entity.getXRot() < -20.0f) {
								newVy = vy * data.getFloat("multiplier");

								// Height limit constraints (Considering Air Resistance 0.98 and Gravity 0.08)
								// Min: 7 Blocks Height -> requires ~1.1 velocity
								// Max: 12 Blocks Height -> requires ~1.5 velocity
								double minVy = 1.1;
								double maxVy = 1.7;

								if (newVy < minVy) newVy = minVy;
								if (newVy > maxVy) newVy = maxVy;

								// Maintain Horizontal Acceleration (Fix "stutter/stop" when looking up)
							} else {
								// Normal Swimming Leap (Flat/Looking Down):
								// Vertical speed (Rising/Sinking) 1.5x boost
								newVy = vy * 1.5;
								// Horizontal boost (1.5x) to create composite vector acceleration
							}
							newVx = velocity.x * 1.5;
							newVz = velocity.z * 1.5;

							// Always apply velocity to preserve momentum against water exit drag
							// This ensures smooth transition for both cases
							entity.setDeltaMovement(newVx, newVy, newVz);
							entity.hurtMarked = true;
						}
					}
				}));

		// ==== SP阿努比斯之狼 - 死亡领域 ====
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "anubis_wolf_sp_death_domain"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer sp) {
						if (SkillBlocker.isSkillBlocked(sp, "anubis_wolf", "death_domain")) {
							return;
						}
						AnubisWolfSpDeathDomain.execute(sp);
					}
				}));

		// ==== SP阿努比斯之狼 - 冥狼裁庭 ====
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "anubis_wolf_sp_summon_wolves"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer sp) {
						if (SkillBlocker.isSkillBlocked(sp, "anubis_wolf", "summon_wolves")) {
							return;
						}
						AnubisWolfSpSummonWolves.execute(sp);
					}
				}));

		// ==== 金沙岚SP - 侵蚀烙印命中处理 ====
		registerBiEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "golden_sandstorm_erosion_brand_hit"),
				new SerializableData(),
				(data, pair) -> {
					Entity actor = pair.getA();
					Entity target = pair.getB();
					if (actor instanceof ServerPlayer sp && target instanceof LivingEntity living) {
						GoldenSandstormErosionBrand.onPlayerAttack(sp, living);
					}
				}));

		// ==== 金沙岚SP - 凋零金沙 ====
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "golden_sandstorm_wither_sand"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer sp) {
						GoldenSandstormWitherSand.execute(sp);
					}
				}));

		// ==== 金沙岚SP - 引爆烙印 ====
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "golden_sandstorm_detonate"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer sp) {
						GoldenSandstormDetonate.execute(sp);
					}
				}));

		// ==== 金沙岚SP - 反噬冲击（被动） ====
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("ssc_addon", "golden_sandstorm_counter_burst"),
				new SerializableData(),
				(data, entity) -> {
					if (entity instanceof ServerPlayer sp) {
						GoldenSandstormCounterBurst.execute(sp);
					}
				}));

		// ==== 旋转圆环粒子（附属专用，仅 red 火环外圈使用，不影响主模组 spawn_particles_in_circle） ====
		registerEntity(SpawnRotatingCircleAction.getFactory());

		// ==== 向前喷射粒子（附属专用，仅 red 吐火使用，不影响共享的 fire_breath） ====
		registerEntity(SpawnForwardBurstAction.getFactory());

		// ==== red 狐火火球：发射火球投射物 + 近身 60°×4格 锥形霰击（5 魔法伤害，附属专用，只作用 red） ====
		registerEntity(new ActionFactory<>(ResourceLocation.fromNamespaceAndPath("my_addon", "fox_fireball"),
				new SerializableData(),
				(data, entity) -> {
					if (!(entity instanceof ServerPlayer player)) return;
					if (!(player.level() instanceof ServerLevel world)) return;
					Vec3 look = player.getViewVector(1.0F);
					// 发射火球投射物
					net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FoxFireballEntity ball =
							new net.onixary.shapeShifterCurseFabric.ssc_addon.entity.FoxFireballEntity(world, player);
					ball.setDirection(look);
					world.addFreshEntity(ball);
					// 近身 60°、4 格锥形霰击：5 点魔法伤害，跳过白名单
					Vec3 eye = player.getEyePosition();
					ResourceKey<DamageType> magicKey = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "magic"));
					AABB box = player.getBoundingBox().inflate(4.0);
					world.getEntitiesOfClass(LivingEntity.class, box,
							e -> e != player && e.isAlive() && !e.isSpectator()).forEach(t -> {
						if (WhitelistUtils.isProtected(player, t)) return;
						Vec3 toT = t.position().add(0, t.getBbHeight() / 2.0, 0).subtract(eye).normalize();
						double dot = look.dot(toT);
						if (dot > 0.5 && player.distanceToSqr(t) < 16.0) {
							t.hurt(t.damageSources().source(magicKey, player, player), 5.0f);
						}
					});
				}));
	}

	private static void registerBiEntity(ActionFactory<Tuple<Entity, Entity>> actionFactory) {
		if (!ApoliRegistries.BIENTITY_ACTION.containsKey(actionFactory.getSerializerId())) {
			Registry.register(ApoliRegistries.BIENTITY_ACTION, actionFactory.getSerializerId(), actionFactory);
		}
	}

	private static void registerEntity(ActionFactory<Entity> actionFactory) {
		if (!ApoliRegistries.ENTITY_ACTION.containsKey(actionFactory.getSerializerId())) {
			Registry.register(ApoliRegistries.ENTITY_ACTION, actionFactory.getSerializerId(), actionFactory);
		}
	}

	/**
	 * 将锥形范围内的水源方块以概率转为冰霜行者冰（Frosted Ice）
	 *
	 * @param world     服务器世界
	 * @param origin    起始位置（玩家眼睛位置）
	 * @param direction 方向向量
	 * @param distance  最大距离
	 * @param chance    每个水方块被冻结的概率
	 */
	private static void freezeWaterInCone(ServerLevel world, Vec3 origin, Vec3 direction, float distance, float chance) {
		// 沿视线方向每格取样，锥形扩散与吐息粒子范围一致
		for (float d = 1.0f; d <= distance; d += 1.0f) {
			Vec3 center = origin.add(direction.scale(d));
			// 锥形扩散半径：与吐息粒子效果一致（最远处约3格宽）
			int radius = Math.max(1, (int) (d * 0.375f));

			int cx = Mth.floor(center.x);
			int cy = Mth.floor(center.y);
			int cz = Mth.floor(center.z);

			for (int x = -radius; x <= radius; x++) {
				for (int y = -1; y <= 1; y++) {
					for (int z = -radius; z <= radius; z++) {
						if (x * x + z * z > radius * radius) continue;

						BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
						if (world.getBlockState(pos).is(Blocks.WATER)
								&& world.getFluidState(pos).isSource()
								&& world.getBlockState(pos.above()).isAir()) {

							if (world.getRandom().nextFloat() < chance) {
								world.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
								world.scheduleTick(
										pos, Blocks.FROSTED_ICE,
										Mth.nextInt(world.getRandom(), 60, 120));
							}
						}
					}
				}
			}
		}
	}
}