package com.mogaidiaolin.witherchallenge.entity;

import com.mogaidiaolin.witherchallenge.combat.ConversionTable;
import com.mogaidiaolin.witherchallenge.combat.CorpseRules;
import com.mogaidiaolin.witherchallenge.combat.WeaponCombatEvents;
import com.mogaidiaolin.witherchallenge.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 感染力量之箭的实体形态。
 *
 * <p>基础伤害恒为 0；命中后：</p>
 * <ul>
 *   <li>命中未加强的时空间隙 → 转成加强版 + 召唤凋暴 + 锁 rage</li>
 *   <li>命中已加强的时空间隙 → 无效果</li>
 *   <li>命中玩家 → 凋零 II 1 小时 + 凋零腐败</li>
 *   <li>命中普通生物 → 按转化表替换成 W.A.R. 生物；表里没有 → 凋零 II 1 小时</li>
 *   <li>命中方块 / 没命中任何实体 → 箭退回射手背包，不消耗</li>
 * </ul>
 */
public class InfectionArrowEntity extends Arrow {

    private boolean refunded;

    public InfectionArrowEntity(EntityType<? extends InfectionArrowEntity> type, Level level) {
        super(type, level);
    }

    public InfectionArrowEntity(Level level, LivingEntity shooter) {
        super(level, shooter);
    }

    public InfectionArrowEntity(Level level, double x, double y, double z) {
        super(level, x, y, z);
    }

    /** 基础伤害恒为 0。 */
    @Override
    public void setBaseDamage(double damage) {
        super.setBaseDamage(0.0D);
    }

    /** 不可拾回（落空时走 {@link #refund()} 直接还给射手）。 */
    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void playerTouch(Player player) {
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = this.getOwner();

        // 1) 时空间隙
        if (WeaponCombatEvents.isCorpse(target)) {
            this.playSound(SoundEvents.WITHER_SPAWN, 2.0F, 0.8F);
            if (!CorpseRules.isEnhanced(target) && target instanceof LivingEntity corpse
                    && this.level() instanceof ServerLevel serverLevel) {
                CorpseRules.enhance(corpse, serverLevel);
                CorpseRules.summonStorm(serverLevel, new Vec3(target.getX(), target.getY(), target.getZ()),
                        owner instanceof Player p ? p : null);
                notifyOwner("message.witherchallenge.arrow.storm", ChatFormatting.DARK_PURPLE);
            } else {
                notifyOwner("message.witherchallenge.arrow.immune", ChatFormatting.GRAY);
            }
            this.discard();
            return;
        }

        // 2) 玩家
        if (target instanceof Player player) {
            CorpseRules.infectPlayer(player);
            player.displayClientMessage(Component.translatable("message.witherchallenge.arrow.infected")
                    .withStyle(ChatFormatting.DARK_RED), true);
            this.discard();
            return;
        }

        // 3) 明确免疫的目标
        if (ConversionTable.isExcluded(target)) {
            this.discard();
            return;
        }

        // 4) 普通生物：转化 / 凋零
        if (target instanceof LivingEntity living && this.level() instanceof ServerLevel serverLevel) {
            boolean converted = ConversionTable.convert(living, serverLevel);
            if (!converted) {
                living.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 60 * 60, 1), owner);
            }
            this.playSound(SoundEvents.WITHER_HURT, 1.0F, 1.4F);
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level().isClientSide) {
            return;
        }
        // 没命中实体：箭退回背包，不消耗（需求 T12）
        refund();
        this.discard();
    }

    /** 落空时把箭还给射手；射手不在就掉在原地。 */
    private void refund() {
        if (refunded) {
            return;
        }
        refunded = true;
        Entity owner = this.getOwner();
        ItemStack stack = new ItemStack(ModItems.INFECTION_ARROW.get());
        if (owner instanceof Player player) {
            if (!player.getAbilities().instabuild && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.displayClientMessage(Component.translatable("message.witherchallenge.arrow.refund")
                    .withStyle(ChatFormatting.GRAY), true);
        } else {
            this.spawnAtLocation(stack);
        }
    }

    private void notifyOwner(String key, ChatFormatting color) {
        if (this.getOwner() instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(color), true);
        }
    }

    /** 调试用：判断某个实体是不是感染箭。 */
    public static boolean isInfectionArrow(@Nullable Entity entity) {
        return entity instanceof InfectionArrowEntity;
    }
}
