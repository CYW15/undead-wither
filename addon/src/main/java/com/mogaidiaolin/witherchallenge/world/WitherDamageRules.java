package com.mogaidiaolin.witherchallenge.world;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 凋灵的两条硬规则（用户 2026-11-11 要求，配合 Wither Reincarnated）。
 *
 * <ol>
 *   <li><b>只受玩家伤害</b>：非玩家来源（凋暴、W.A.R. 生物、其它怪物、火焰、TNT 等）打上去直接取消；
 *       玩家本人、玩家射出的弹射物、玩家驯服的宠物、玩家点燃的 TNT 都算「玩家伤害」。
 *       {@code /kill}、虚空这类 {@code BYPASSES_INVULNERABILITY} 的来源例外放行（便于调试）。</li>
 *   <li><b>永不回血</b>：记录每只凋灵「见过的最低血量」，任何让它变高的行为（WIR 的阶段回血、
 *       再生药水、治疗）都会被拉回最低值。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class WitherDamageRules {

    /** 每只凋灵见过的最低血量。 */
    private static final Map<UUID, Float> LOWEST = new HashMap<>();

    // ------------------------------------------------------------- 只受玩家伤害

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof WitherBoss wither)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;   // /kill、虚空
        }
        if (isPlayerDamage(source)) {
            return;
        }
        event.setCanceled(true);
    }

    private static boolean isPlayerDamage(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player) {
            return true;
        }
        if (attacker instanceof TamableAnimal pet && pet.getOwner() instanceof Player) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof Player) {
            return true;
        }
        if (direct instanceof net.minecraft.world.entity.item.PrimedTnt tnt
                && tnt.getOwner() instanceof Player) {
            return true;
        }
        // 玩家点燃的 TNT 爆炸：owner 在 getEntity() 上
        return attacker != null && attacker.getType() == net.minecraft.world.entity.EntityType.PLAYER;
    }

    // ------------------------------------------------------------- 永不回血

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 10 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof WitherBoss wither) || !wither.isAlive()) {
                    continue;
                }
                UUID id = wither.getUUID();
                float health = wither.getHealth();
                Float lowest = LOWEST.get(id);
                if (lowest == null || health < lowest) {
                    LOWEST.put(id, health);
                } else if (health > lowest) {
                    wither.setHealth(lowest);
                }
            }
        }
        LOWEST.keySet().removeIf(id -> server.getAllLevels().iterator().next().getEntity(id) == null);
    }

    /** 供调试命令查询：清除记录（例如手动治疗时）。 */
    public static void forget(UUID id) {
        LOWEST.remove(id);
    }

    private WitherDamageRules() {
    }
}
