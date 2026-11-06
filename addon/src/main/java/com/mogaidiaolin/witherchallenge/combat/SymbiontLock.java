package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 凋灵共生体（witherstormmod:withered_symbiont）的强制锁血。
 *
 * <p>锁点 3/4、1/2、1/4（需求 Q79），状态存 NBT；每打掉一个 Wither Storm Head 解锁一档（Q80）。
 * 锁血期间完全免伤（无音效、无击退），但 /kill 与虚空仍然有效。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class SymbiontLock {

    private static final ResourceLocation SYMBIONT =
            new ResourceLocation("witherstormmod", "withered_symbiont");
    private static final ResourceLocation HEAD =
            new ResourceLocation("witherstormmod", "wither_storm_head");
    private static final float[] LOCKS = {0.75F, 0.50F, 0.25F};
    private static final String TAG = "wc_symbiont_unlocked_";

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (!SYMBIONT.equals(EntityType.getKey(entity.getType()))) {
            return;
        }
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;   // /kill、虚空这类照常生效
        }
        float max = entity.getMaxHealth();
        float health = entity.getHealth();
        for (int i = 0; i < LOCKS.length; i++) {
            if (entity.getPersistentData().getBoolean(TAG + i)) {
                continue;
            }
            float lockHealth = max * LOCKS[i];
            if (health - event.getAmount() < lockHealth) {
                float allowed = Math.max(0.0F, health - lockHealth);
                if (allowed <= 0.0F) {
                    event.setCanceled(true);   // 已经贴着锁点：连受击反馈都不给
                } else {
                    event.setAmount(allowed);
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onHeadDeath(LivingDeathEvent event) {
        Entity dead = event.getEntity();
        if (!HEAD.equals(EntityType.getKey(dead.getType()))) {
            return;
        }
        if (dead.level().isClientSide) {
            return;
        }
        if (!(dead.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        List<Entity> all = new java.util.ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            all.add(e);
        }
        for (Entity e : all) {
            if (!(e instanceof LivingEntity living) || !SYMBIONT.equals(EntityType.getKey(e.getType()))) {
                continue;
            }
            for (int i = 0; i < LOCKS.length; i++) {
                if (!living.getPersistentData().getBoolean(TAG + i)) {
                    living.getPersistentData().putBoolean(TAG + i, true);
                    WitherChallenge.LOGGER.info("[凋亡回响] 共生体解锁锁血档位 {}", i);
                    break;
                }
            }
        }
    }

    private SymbiontLock() {
    }
}
