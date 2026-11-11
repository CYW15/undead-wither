package com.mogaidiaolin.witherchallenge.world;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 凋灵的两条硬规则（用户 2026-11-11 要求）。
 *
 * <ol>
 *   <li><b>只受玩家伤害</b>；</li>
 *   <li><b>永不回血</b>。</li>
 * </ol>
 *
 * <p><b>为什么不能只靠 {@link LivingAttackEvent}：</b>
 * Wither Reincarnated 在 {@code WitherBoss#hurt} 的 HEAD 注入并 {@code callback.cancel()}，
 * 原版方法体根本不会执行，而 Forge 的 {@code LivingAttackEvent} 是在
 * {@code LivingEntity#hurt} 里触发的 —— 所以那个事件对凋灵**不会触发**。</p>
 *
 * <p>因此这里改成「血量账本」策略：
 * 每 tick 记录每只凋灵的允许血量 {@code allowed}；</p>
 * <ul>
 *   <li>血量升高（任何回血）→ 立刻拉回 {@code allowed}；</li>
 *   <li>血量降低，但最近 2 秒内没有玩家攻击过它 → 判定为「非玩家伤害」→ 立刻回填到 {@code allowed}；</li>
 *   <li>血量降低且最近 2 秒内有玩家攻击 → 认可这次扣血，更新 {@code allowed}。</li>
 * </ul>
 *
 * <p>玩家攻击的识别：{@link AttackEntityEvent}（近战）与
 * {@link ProjectileImpactEvent}（玩家射出的弹射物）。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class WitherDamageRules {

    /** 允许血量（只会单调下降，除了玩家造成的那部分）。 */
    private static final Map<UUID, Float> ALLOWED = new HashMap<>();
    /** 最近一次玩家攻击的 tick。 */
    private static final Map<UUID, Long> LAST_PLAYER_HIT = new HashMap<>();
    /** 玩家伤害的认可窗口（tick）。2 秒足够覆盖玩家一刀 + 击退后的连锁伤害。 */
    private static final long PLAYER_WINDOW = 40L;

    // ------------------------------------------------------------- 玩家攻击标记

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        markPlayerHit(event.getTarget());
    }

    @SubscribeEvent
    public static void onProjectileHit(ProjectileImpactEvent event) {
        Projectile projectile = (Projectile) event.getProjectile();
        if (!(projectile.getOwner() instanceof Player)) {
            return;
        }
        Entity hit = event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult er
                ? er.getEntity() : null;
        markPlayerHit(hit);
    }

    private static void markPlayerHit(Entity target) {
        if (target instanceof WitherBoss wither) {
            LAST_PLAYER_HIT.put(wither.getUUID(),
                    wither.level().getGameTime());
        }
    }

    // ------------------------------------------------------------- 兜底：能拦就拦

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof WitherBoss)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
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
        if (attacker instanceof net.minecraft.world.entity.TamableAnimal pet
                && pet.getOwner() instanceof Player) {
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
        return false;
    }

    // ------------------------------------------------------------- 血量账本

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof WitherBoss wither) || !wither.isAlive()) {
                    continue;
                }
                UUID id = wither.getUUID();
                float health = wither.getHealth();
                Float allowed = ALLOWED.get(id);
                if (allowed == null) {
                    ALLOWED.put(id, health);
                    continue;
                }
                if (health > allowed) {
                    // 回血 → 拉回
                    wither.setHealth(allowed);
                } else if (health < allowed) {
                    long last = LAST_PLAYER_HIT.getOrDefault(id, Long.MIN_VALUE / 2);
                    if (now - last > PLAYER_WINDOW) {
                        // 最近 2 秒没有玩家攻击 → 认为是非玩家伤害，回填
                        wither.setHealth(allowed);
                    } else {
                        ALLOWED.put(id, health);
                    }
                }
            }
        }
        // 清理已消失的凋灵
        ALLOWED.keySet().removeIf(id -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    return false;
                }
            }
            LAST_PLAYER_HIT.remove(id);
            return true;
        });
    }

    /** 调试用：忘掉一只凋灵的账本。 */
    public static void forget(UUID id) {
        ALLOWED.remove(id);
        LAST_PLAYER_HIT.remove(id);
    }

    private WitherDamageRules() {
    }
}
