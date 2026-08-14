package net.jackcooper.shapeShifterCurseAddon.entity;

import net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.onixary.shapeShifterCurseFabric.entity.projectile.WebBullet;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.RegManaComponent;

/**
 * 月织蛛「蛛丝荡漾」次技能 - 蛛丝飞弹（抛物线投射物，复用主技能蛛丝弹外观）。
 *
 * <p>按次键从玩家眼前发射，带轻微下坠抛物线飞向准星方向。飞行中按已飞长度扣 mana（每 2 格 1 点）。
 * <ul>
 *   <li><b>命中方块</b> → 钩住进入摆荡。</li>
 *   <li><b>命中生物</b> → 连接进入 tether 拖拽。</li>
 *   <li><b>飞到最大绳长（32 格）仍未命中 / mana 耗尽</b> → 球与丝线<b>同时立即消失</b>（miss，5 秒 CD）。</li>
 * </ul>
 * 客户端渲染「玩家→飞弹」的蛛丝：弹存在即画，弹消失（命中/miss）丝线随之消失。
 */
public class SpiderSwingBullet extends WebBullet {

	private double traveled = 0.0;
	private double lastManaCharge = 0.0;
	private int life = 0;

	public SpiderSwingBullet(EntityType<? extends SpiderSwingBullet> type, Level world) {
		super(type, world);
	}

	public SpiderSwingBullet(LivingEntity owner) {
		super(RegAddonEntities.SPIDER_SWING_BULLET, owner.level());
		this.owner = owner;
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
	}

	@Override
	protected float getGravity() {
		return 0.025f; // 轻微下坠抛物线
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel sw && this.owner instanceof Player player) {
			traveled += this.getVelocity().length();
			ManaComponent m = RegManaComponent.MANA.get(player);
			while (traveled - lastManaCharge >= 2.0) {
				if (m.getMana() < 1.0) {
					fadeOut(sw); // mana 耗尽 → 球与丝线立即消失
					this.discard();
					return;
				}
				m.consumeMana(1.0);
				lastManaCharge += 2.0;
			}
			if (traveled > SpiderMoonWeaverSwingManager.MAX_ROPE_REACH) {
				fadeOut(sw); // 飞到最大绳长仍未命中 → 球与丝线同时立即消失
				this.discard();
				return;
			}
		}
		life++;
		super.tick(); // WebBullet.tick：发射音效 + 轨迹粒子 + 移动 + 碰撞检测
		if (life > 140 && !this.isRemoved()) {
			if (this.level() instanceof ServerLevel sw) fadeOut(sw);
			this.discard();
		}
	}

	@Override
	public void onBlockHit(BlockHitResult hit) {
		if (this.level() instanceof ServerLevel sw && this.owner instanceof ServerPlayer sp) {
			Vec3d anchor = hit.getPos();
			SpiderMoonWeaverSwingManager.onBulletHitBlock(sp, anchor);
			sw.playSound(null, anchor.x, anchor.y, anchor.z,
					SoundEvents.BLOCK_TRIPWIRE_ATTACH, SoundSource.PLAYERS, 0.9f, 1.4f);
		}
		this.discard();
	}

	@Override
	public void onEntityHit(EntityHitResult hit) {
		Entity e = hit.getEntity();
		if (this.level() instanceof ServerLevel sw && this.owner instanceof ServerPlayer sp
				&& e instanceof LivingEntity living && living != sp) {
			SpiderMoonWeaverSwingManager.onBulletHitEntity(sp, living);
			sw.playSound(null, living.getX(), living.getY(), living.getZ(),
					SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.9f, 1.6f); // 叮！
			this.discard();
		}
		// 命中无效目标（如自己）：不 discard，继续飞
	}

	private void fadeOut(ServerLevel sw) {
		sw.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(),
				12, 0.2, 0.2, 0.2, 0.02);
		sw.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.BLOCK_WOOL_BREAK, SoundSource.NEUTRAL, 0.5f, 1.2f);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		// 统一 miss 处理（超程/超时/液体/未命中）：onBulletMiss 幂等，仅仍在飞（未命中）时给 5 秒 CD
		if (!this.level().isClient && this.owner instanceof ServerPlayer sp) {
			SpiderMoonWeaverSwingManager.onBulletMiss(sp);
		}
		super.remove(reason);
	}
}