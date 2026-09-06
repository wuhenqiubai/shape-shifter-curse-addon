package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import net.jackcooper.shapeShifterCurseAddon.entity.SpellFrostSpikeEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.Spell;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellRarity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 冰锥（白色基底，jackcooper）。朝准星射出一枚冰锥，命中造成魔法伤害。
 *
 * <p>装书内基准：6 伤 / cd 3 秒 / 无施法时间 / 耗书法力 15。
 * 单独使用：默认固定裸用惩罚（伤害 ×0.5、冷却 ×2、施法时间 ×2）。</p>
 *
 * <p><b>等级（1-5，固定只能开箱获得，不可升级）＝品质色</b>（相对基础值累加，不叠乘）：</p>
 * <ul>
 *   <li>L1 白色：基础效果（6 伤 / 0.75 速 / cd 3s / 雪球渲染，单独可用 8 次）；</li>
 *   <li>L2 绿色：伤害与飞行速度 +25%（单独可用 6 次）；</li>
 *   <li>L3 蓝色：再 cd -25%（2.25s）（单独可用 4 次）；</li>
 *   <li>L4 紫色：再伤害与速度 +25%（合计 +50%），投射物换寒棘狐同款 3D 冰锥模型与大小（单独可用 2 次）；</li>
 *   <li>L5 橙色：再 cd -25%（合计 -50%，1.5s）（单独可用 1 次）。</li>
 * </ul>
 */
public class FrostSpikeSpell extends Spell {

	public FrostSpikeSpell() {
		super(Identifier.of("ssc_addon", "frost_spike"), SpellRarity.WHITE);
	}

	@Override
	public float getBaseDamage() {
		return 6.0f;
	}

	@Override
	public int getBaseCooldownTicks() {
		return 60; // 3 秒
	}

	@Override
	public int getBaseCastTimeTicks() {
		return 0; // 无施法时间
	}

	@Override
	public int getManaCost() {
		return 15;
	}

	/** 伤害倍率：L2 +25%、L4 再 +25%（合计 +50%）；其余等级 1.0。 */
	@Override
	public float getDamageMultiplier(int level) {
		float m = 1.0f;
		if (level >= 2) m += 0.25f;
		if (level >= 4) m += 0.25f;
		return m;
	}

	/** 冷却倍率：L3 -25%、L5 再 -25%（合计 -50%）；其余等级 1.0。 */
	@Override
	public float getCooldownMultiplier(int level) {
		float m = 1.0f;
		if (level >= 3) m -= 0.25f;
		if (level >= 5) m -= 0.25f;
		return m;
	}

	/** 飞行速度倍率：与伤害同档（L2 +25%、L4 再 +25%）。 */
	@Override
	public float getSpeedMultiplier(int level) {
		return getDamageMultiplier(level);
	}

	/** 冰系魔法：卷轴物品外观用冰锥卷轴贴图。 */
	@Override
	public boolean isIceSpell() {
		return true;
	}

	/** 等级即品质：1白/2绿/3蓝/4紫/5橙（决定单独使用次数、名称与 HUD 品质色）。 */
	@Override
	public SpellRarity getRarity(int level) {
		return switch (level) {
			case 2 -> SpellRarity.GREEN;
			case 3 -> SpellRarity.BLUE;
			case 4 -> SpellRarity.PURPLE;
			case 5 -> SpellRarity.ORANGE;
			default -> SpellRarity.WHITE;
		};
	}

	@Override
	public void cast(ServerPlayerEntity caster, float power, boolean solo) {
		cast(caster, power, solo, 1);
	}

	/** 带等级施法：速度与投射物外观（L4+ 换 3D 冰锥模型）按卷轴等级缩放。 */
	public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
		SpellFrostSpikeEntity spike = new SpellFrostSpikeEntity(caster.getWorld(), caster);
		spike.setDamage(power);
		spike.setLevel(level);
		Vec3d look = caster.getRotationVec(1.0F);
		spike.setDirection(look, getSpeedMultiplier(level));
		caster.getWorld().spawnEntity(spike);
		caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
				SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 1.0f, 0.8f);
	}
}
