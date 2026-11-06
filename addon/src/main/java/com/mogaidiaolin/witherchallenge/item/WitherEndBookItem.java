package com.mogaidiaolin.witherchallenge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 凋灵终结书（未激活 / 激活）。
 *
 * <p>未激活：工作台合成，只能在 IV 级凋暴信标上右击激活；
 * 激活：带附魔光效，用于铁砧上升级凋灵刀 / 凋灵战斧。</p>
 *
 * <p>第三批会接上「信标右击激活」与「铁砧升级」两段交互；本批先把物品做出来、进创造页。</p>
 */
public class WitherEndBookItem extends Item {
    private final boolean activated;

    public WitherEndBookItem(boolean activated, Properties properties) {
        super(properties);
        this.activated = activated;
    }

    public boolean isActivated() {
        return activated;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return activated;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String base = "item.witherchallenge." + (activated ? "activated_wither_end_book" : "wither_end_book");
        if (activated) {
            tooltip.add(Component.translatable(base + ".tooltip.1").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("item.witherchallenge.activated_wither_end_book.lore")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.translatable(base + ".tooltip.1").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(base + ".tooltip.2").withStyle(ChatFormatting.DARK_RED));
        }
    }
}
