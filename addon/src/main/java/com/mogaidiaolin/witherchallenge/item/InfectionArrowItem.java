package com.mogaidiaolin.witherchallenge.item;

import com.mogaidiaolin.witherchallenge.entity.InfectionArrowEntity;
import com.mogaidiaolin.witherchallenge.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 感染力量之箭。
 *
 * <p>弓和弩都能发射（两者都走 {@link ArrowItem#createArrow}），本身基础伤害为 0，
 * 命中即消失、不可拾回；未命中实体时由 {@link InfectionArrowEntity} 退回背包。</p>
 */
public class InfectionArrowItem extends ArrowItem {
    public InfectionArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter) {
        return new InfectionArrowEntity(level, shooter);
    }

    @Override
    public boolean isInfinite(ItemStack stack, ItemStack bow, net.minecraft.world.entity.player.Player player) {
        // 无限附魔对感染箭不生效（需求 Q26）。
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witherchallenge.infection_arrow.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.witherchallenge.infection_arrow.tooltip.2")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.witherchallenge.infection_arrow.tooltip.3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean isInfectionArrow(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof InfectionArrowItem;
    }

    public static boolean matchesEntity(AbstractArrow arrow) {
        return arrow.getType() == ModEntities.INFECTION_ARROW.get();
    }
}
