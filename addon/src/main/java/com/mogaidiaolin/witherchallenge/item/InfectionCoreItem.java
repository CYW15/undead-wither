package com.mogaidiaolin.witherchallenge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 感染核心：the Mangled 被玩家 / 驯服狼击杀时按概率掉落，只用于感染箭与凋灵终结书的配方。 */
public class InfectionCoreItem extends Item {
    public InfectionCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.witherchallenge.infection_core.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.witherchallenge.infection_core.tooltip.2")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
