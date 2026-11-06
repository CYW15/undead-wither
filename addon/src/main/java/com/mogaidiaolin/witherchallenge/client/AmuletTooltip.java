package com.mogaidiaolin.witherchallenge.client;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 给 CWS 的护身符（{@code witherstormmod:amulet}）的 tooltip 追加第 6 行：最近的凋灵。
 *
 * <p>CWS 原版在物品栏里悬停护身符时会列出「4 个已绑定实体 + 最近凋暴」共 5 行，
 * 我们在这后面再加一行「最近的凋灵」，和凋暴那行互相独立。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID, value = Dist.CLIENT)
public final class AmuletTooltip {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!isAmulet(stack)) {
            return;
        }
        var info = WitherPosClient.latest();
        if (info == null) {
            return;
        }
        if (!info.present()) {
            event.getToolTip().add(Component.literal("凋灵：这个维度里没有")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        event.getToolTip().add(Component.literal(
                        String.format("凋灵：%d 格 %s（%s）", info.distance(), info.direction(),
                                shortDim(info.dimension())))
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static String shortDim(String dim) {
        if (dim == null) {
            return "";
        }
        int i = dim.indexOf(':');
        return i >= 0 ? dim.substring(i + 1) : dim;
    }

    private static boolean isAmulet(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "witherstormmod".equals(id.getNamespace()) && "amulet".equals(id.getPath());
    }

    private AmuletTooltip() {
    }
}
