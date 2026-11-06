package com.mogaidiaolin.witherchallenge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 凋灵终结刀 / 凋灵终结战斧。
 *
 * <p>本批做出来的部分：正确的攻击力 / 攻速 / 附魔能力 / 无限耐久。
 * 第三批补：对「时空间隙」的轻击/重击判定、对凋暴体内命令方块的 4 次 25% 伤害、
 * 刀（6s 凋零 + 自己 6s 再生）与斧（3 秒后失去 75% 当前生命）的特效。</p>
 */
public class WitherEndWeaponItem extends SwordItem {
    private final boolean knife;

    public WitherEndWeaponItem(Tier tier, int attackDamageModifier, float attackSpeedModifier,
                               boolean knife, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
        this.knife = knife;
    }

    public boolean isKnife() {
        return knife;
    }

    /**
     * 命中非时空间隙目标时的特效：
     * 刀 → 目标 6 秒凋零 + 自己 6 秒再生；斧 → 3 秒后扣除目标 75% 当前生命。
     * （时空间隙走 {@code WeaponCombatEvents} 的专用分支，不会进这里）
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target,
                             net.minecraft.world.entity.LivingEntity attacker) {
        if (!attacker.level().isClientSide && !com.mogaidiaolin.witherchallenge.combat.WeaponCombatEvents.isCorpse(target)
                && !com.mogaidiaolin.witherchallenge.combat.WeaponCombatEvents.isStormCommandBlock(target)) {
            if (knife) {
                com.mogaidiaolin.witherchallenge.combat.WeaponCombatEvents.applyKnifeEffects(target, attacker);
            } else if (attacker instanceof net.minecraft.world.entity.player.Player player) {
                com.mogaidiaolin.witherchallenge.combat.WeaponCombatEvents.scheduleDelayed(target, player);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /** 无限耐久：始终带 Unbreakable 标签（免疫耐久消耗）。 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && !stack.getOrCreateTag().getBoolean("Unbreakable")) {
            stack.getOrCreateTag().putBoolean("Unbreakable", true);
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, net.minecraft.world.entity.player.Player player) {
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        super.onCraftedBy(stack, level, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String base = "item.witherchallenge." + (knife ? "wither_end_knife" : "wither_end_war_axe");
        tooltip.add(Component.translatable(base + ".tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(base + ".tooltip.2").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.witherchallenge.wither_end_weapon.unbreakable")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
