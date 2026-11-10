package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 让「滑翔中受伤」不再打断飞行（用户 2026-11-10 要求）。
 *
 * <p>原版规则：玩家用鞘翅滑翔时受到伤害，{@code LivingEntity#hurt} 会调用
 * {@code stopFallFlying()}，于是人被从空中打下来；同时鞘翅图标会出现冷却/暗淡状态。
 * 在凋暴这种「空中被头颅追着打」的场景里，这条规则会让远程飞行几乎没法玩。</p>
 *
 * <p>这里的处理：受伤的那一 tick 结束后，如果玩家还在空中，就重新进入滑翔状态，
 * 并清掉鞘翅的物品冷却。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class ElytraFlightFix {

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        boolean wasFlying = player.isFallFlying();
        boolean hasElytra = player.isFallFlying()
                || player.getInventory().armor.get(2).is(Items.ELYTRA)
                || player.getInventory().offhand.stream().anyMatch(s -> s.is(Items.ELYTRA));
        if (!wasFlying && !hasElytra) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        // hurt 内部会 stopFallFlying()，等这一 tick 的伤害处理结束再恢复
        server.execute(() -> {
            try {
                player.getCooldowns().removeCooldown(Items.ELYTRA);
                if (wasFlying && !player.onGround() && !player.isFallFlying()) {
                    player.startFallFlying();
                }
            } catch (Throwable t) {
                WitherChallenge.LOGGER.debug("[凋亡回响] 恢复滑翔失败：{}", t.toString());
            }
        });
    }

    private ElytraFlightFix() {
    }
}
