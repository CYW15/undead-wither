package com.mogaidiaolin.witherchallenge.item;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 首次进服把挑战指南书放进物品栏第一格（需求 Q96 / R9）。 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class GuideBookEvents {

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getPersistentData().getBoolean(GuideBook.GIVEN_TAG)) {
            return;
        }
        player.getPersistentData().putBoolean(GuideBook.GIVEN_TAG, true);
        ItemStack book = GuideBook.create();
        if (player.getInventory().getItem(0).isEmpty()) {
            player.getInventory().setItem(0, book);
        } else if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
    }

    private GuideBookEvents() {
    }
}
