package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import com.mogaidiaolin.witherchallenge.world.InfectionControl;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import nonamecrackers2.witherstormmod.common.capability.WitherSicknessTracker;
import nonamecrackers2.witherstormmod.common.init.WitherStormModCapabilities;
import nonamecrackers2.witherstormmod.common.init.WitherStormModEffects;

import com.mogaidiaolin.witherchallenge.registry.ModEffects;

/**
 * 凋零腐败与「凋零残响」。
 *
 * <p><b>规则（用户 2026-11-07 最终确认）：</b></p>
 * <ol>
 *   <li>凋零腐败与残响**永不共存**，有腐败就没有残响；</li>
 *   <li>**只要世界里有凋暴**（不用管时空间隙），玩家被治好、腐败消失的瞬间就获得残响
 *       （限时模式；永久模式则不获得）；</li>
 *   <li>残响期间若再次获得腐败（箭 / 凋暴 / 任何来源）→ **立刻取消残响**；</li>
 *   <li>残响结束 → 再次感染；</li>
 *   <li>世界里没有凋暴 → 治愈即永久康复，不给残响；</li>
 *   <li>死亡不治愈：死亡时身上有腐败就重生后继续感染，处于残响期则保留残响计时。</li>
 * </ol>
 *
 * <p>判断依据一律是玩家身上**实际存在的效果**，不看 CWS tracker 的内部字段，
 * 避免治愈延迟造成「腐败和残响同时出现」。</p>
 *
 * <p>这里的逻辑对**所有感染来源**生效（凋暴感染、箭感染、其它 mod 挂的腐败）。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class SicknessControl {

    private static final String FLAG_HAD_SICKNESS = "wc_had_sickness";
    private static final String FLAG_ECHO_UNTIL = "wc_echo_until";

    // ---------------------------------------------------------------- 对外

    /** 箭命中玩家：正常感染 + 1 小时凋零 II。 */
    public static void infect(ServerPlayer player) {
        cancelEcho(player);
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 60 * 60, 1));
        player.getPersistentData().putBoolean(FLAG_HAD_SICKNESS, true);
        player.getCapability(WitherStormModCapabilities.WITHER_SICKNESS_TRACKER)
                .ifPresent(WitherSicknessTracker::infect);
    }

    public static void cancelEcho(ServerPlayer player) {
        if (ModEffects.WITHER_ECHO.isPresent()) {
            MobEffect echo = ModEffects.WITHER_ECHO.get();
            if (player.hasEffect(echo)) {
                player.removeEffect(echo);
            }
        }
        player.getPersistentData().putLong(FLAG_ECHO_UNTIL, 0L);
    }

    public static void setPermanent(ServerLevel level, boolean permanent) {
        var state = InfectionControl.state(level);
        state.curePermanent = permanent;
        state.setDirty();
    }

    public static void setMinutes(ServerLevel level, int minutes) {
        var state = InfectionControl.state(level);
        state.sicknessMinutes = Math.max(1, minutes);
        state.setDirty();
    }

    // ---------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 10 != 0) {
            return;
        }
        boolean stormAlive = anyStormAlive(server);
        var state = InfectionControl.state(server.overworld());
        boolean echoAvailable = ModEffects.WITHER_ECHO.isPresent();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var data = player.getPersistentData();
            long now = player.level().getGameTime();
            boolean hasSickness = hasSickness(player);
            boolean hasEcho = echoAvailable && player.hasEffect(ModEffects.WITHER_ECHO.get());
            long echoUntil = data.getLong(FLAG_ECHO_UNTIL);

            // 1) 有腐败：绝不共存，撤掉残响；并保证身上有凋零（血条变灰）
            if (hasSickness) {
                ensureWitherEffect(player);
                data.putBoolean(FLAG_HAD_SICKNESS, true);
                if (hasEcho || echoUntil > 0L) {
                    cancelEcho(player);
                }
                continue;
            }

            // 2) 残响计时中
            if (echoUntil > 0L) {
                if (now < echoUntil) {
                    if (echoAvailable && !hasEcho) {   // 死亡等原因清掉效果 → 按剩余时间补回
                        player.addEffect(new MobEffectInstance(ModEffects.WITHER_ECHO.get(),
                                (int) Math.max(1, echoUntil - now), 0, false, true));
                    }
                    continue;
                }
                // 到期 → 再次感染
                cancelEcho(player);
                player.getCapability(WitherStormModCapabilities.WITHER_SICKNESS_TRACKER)
                        .ifPresent(WitherSicknessTracker::infect);
                data.putBoolean(FLAG_HAD_SICKNESS, true);
                player.displayClientMessage(
                        Component.translatable("message.witherchallenge.sickness.again"), true);
                continue;
            }

            // 3) 没有腐败也没有残响：刚刚被治好（上一 tick 还见过腐败）
            if (!data.getBoolean(FLAG_HAD_SICKNESS)) {
                continue;
            }
            data.putBoolean(FLAG_HAD_SICKNESS, false);
            if (!stormAlive || state.curePermanent || !echoAvailable) {
                continue;   // 没凋暴 / 永久模式 → 永久康复
            }
            long minutes = Math.max(1, state.sicknessMinutes);
            data.putLong(FLAG_ECHO_UNTIL, now + minutes * 1200L);
            player.addEffect(new MobEffectInstance(ModEffects.WITHER_ECHO.get(),
                    (int) (minutes * 1200L), 0, false, true));
            player.displayClientMessage(
                    Component.translatable("message.witherchallenge.sickness.echo", minutes), true);
        }
    }

    /** 只要身上有凋零腐败，就必须同时有凋零效果（血条才会变灰）。 */
    private static void ensureWitherEffect(ServerPlayer player) {
        if (!player.hasEffect(MobEffects.WITHER)) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 60 * 60, 1, false, true));
        }
    }

    private static boolean hasSickness(net.minecraft.world.entity.player.Player player) {
        try {
            return player.hasEffect(WitherStormModEffects.WITHER_SICKNESS.get());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 兜底：任何来源要挂凋零腐败时，先撤掉残响（腐败优先）。 */
    @SubscribeEvent
    public static void onEffectApplicable(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffect effect = event.getEffectInstance().getEffect();
        boolean isSickness;
        try {
            isSickness = effect == WitherStormModEffects.WITHER_SICKNESS.get();
        } catch (Throwable t) {
            return;
        }
        if (isSickness) {
            cancelEcho(player);
            player.getPersistentData().putBoolean(FLAG_HAD_SICKNESS, true);
        }
    }

    private static boolean anyStormAlive(net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                var id = net.minecraft.world.entity.EntityType.getKey(entity.getType());
                if (id != null && "witherstormmod".equals(id.getNamespace())
                        && "wither_storm".equals(id.getPath()) && entity.isAlive()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 死亡不治愈：死亡时有腐败才续感染，残响期则保留计时。 */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer resurrected)) {
            return;
        }
        var old = event.getOriginal();
        var oldData = old.getPersistentData();
        boolean hadSickness = hasSickness(old);
        resurrected.getPersistentData().putBoolean(FLAG_HAD_SICKNESS,
                hadSickness || oldData.getBoolean(FLAG_HAD_SICKNESS));
        resurrected.getPersistentData().putLong(FLAG_ECHO_UNTIL, oldData.getLong(FLAG_ECHO_UNTIL));
        if (hadSickness) {
            resurrected.getCapability(WitherStormModCapabilities.WITHER_SICKNESS_TRACKER)
                    .ifPresent(WitherSicknessTracker::infect);
        }
    }

    private SicknessControl() {
    }
}
