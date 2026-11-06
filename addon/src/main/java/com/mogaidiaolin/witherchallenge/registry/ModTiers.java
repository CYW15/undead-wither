package com.mogaidiaolin.witherchallenge.registry;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 两把终结武器的「材质」。
 *
 * <p>攻击力/攻速按需求确认清单 Q49/Q53 固定：刀 5 攻 4.5 速、斧 13 攻 1.5 速，
 * 附魔能力都是 15，无修复材料。耐久走 Unbreakable 标签（见 WitherEndWeaponItem）。</p>
 */
public final class ModTiers {

    public static final Tier KNIFE = new SimpleTier(2031, 4.0F, 0.0F, 15);
    public static final Tier WAR_AXE = new SimpleTier(2031, 4.0F, 0.0F, 15);

    private record SimpleTier(int uses, float speed, float attackDamageBonus, int enchantmentValue)
            implements Tier {
        @Override
        public int getUses() {
            return uses;
        }

        @Override
        public float getSpeed() {
            return speed;
        }

        @Override
        public float getAttackDamageBonus() {
            return attackDamageBonus;
        }

        @Override
        public int getLevel() {
            return Tiers.DIAMOND.getLevel();
        }

        @Override
        public int getEnchantmentValue() {
            return enchantmentValue;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    }

    private ModTiers() {
    }
}
