package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 给**没有 WIR 的版本**（0.0.31）的凋灵加「凋暴式」技能组（用户 2026-11-11 要求）。
 *
 * <p>装了 Wither Reincarnated 的版本（0.0.32）不启用，避免和 WIR 自带的
 * 灵魂激光 / 弹幕 / 夺舍 叠在一起。</p>
 *
 * <p>按血量分四段（借鉴凋暴 phase 0–3 的思路）：</p>
 * <table>
 *   <tr><td>P0 ＞75%</td><td>每 5 秒 3 发凋零之首</td></tr>
 *   <tr><td>P1 50–75%</td><td>每 5 秒 8 发齐射 + 每 30 秒召唤 2 只凋零骷髅</td></tr>
 *   <tr><td>P2 25–50%</td><td>每 10 秒一轮「天降火雨」：12 发头颅从头顶落下</td></tr>
 *   <tr><td>P3 ＜25%</td><td>每 10 秒一轮「机炮」：25 发蓝色头颅，0.2 秒一发</td></tr>
 * </table>
 *
 * <p>所有技能都要求凋灵有目标（原版 AI 选择的目标或 48 格内最近玩家），
 * 不会隔墙追踪玩家。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class WitherSkills {

    private static final int P0_INTERVAL = 100;
    private static final int P1_INTERVAL = 100;
    private static final int P1_SUMMON_INTERVAL = 600;
    private static final int P2_INTERVAL = 200;
    private static final int P3_INTERVAL = 200;

    private static final Map<UUID, Ability> ABILITIES = new HashMap<>();

    private static final class Ability {
        int ticksLeft;
        int shot;
        int total;
        int interval;
        boolean blue;
        boolean barrage;
        Vec3 aim;
        long lastBurst = Long.MIN_VALUE / 2;
        long lastSummon = Long.MIN_VALUE / 2;
        long lastBarrage = Long.MIN_VALUE / 2;
        long lastCannon = Long.MIN_VALUE / 2;
    }

    private static boolean active() {
        return !ModList.get().isLoaded("witherreincarnated");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) {
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
                Ability ab = ABILITIES.computeIfAbsent(wither.getUUID(), k -> new Ability());
                tickAbility(level, wither, ab, now);
            }
        }
        ABILITIES.keySet().removeIf(id -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) != null) {
                    return false;
                }
            }
            return true;
        });
    }

    private static void tickAbility(ServerLevel level, WitherBoss wither, Ability ab, long now) {
        // 正在进行的弹幕 / 机炮
        if (ab.ticksLeft > 0) {
            if (--ab.ticksLeft % Math.max(1, ab.interval) == 0 && ab.shot < ab.total) {
                fire(level, wither, ab.aim, ab.blue, ab.barrage);
                ab.shot++;
            }
            if (ab.shot >= ab.total) {
                ab.ticksLeft = 0;
            }
            return;
        }

        LivingEntity target = wither.getTarget();
        if (target == null || !target.isAlive()) {
            target = nearestPlayer(level, wither, 48.0D);
        }
        if (target == null) {
            return;
        }
        Vec3 aim = new Vec3(target.getX(), target.getY() + target.getEyeHeight() * 0.5D, target.getZ());
        float ratio = wither.getHealth() / Math.max(1.0F, wither.getMaxHealth());

        if (ratio <= 0.25F) {
            if (now - ab.lastCannon >= P3_INTERVAL) {
                ab.lastCannon = now;
                start(ab, aim, 25, 4, true, false);
            }
            return;
        }
        if (ratio <= 0.50F) {
            if (now - ab.lastBarrage >= P2_INTERVAL) {
                ab.lastBarrage = now;
                start(ab, aim, 12, 4, false, true);
            }
            return;
        }
        if (ratio <= 0.75F) {
            if (now - ab.lastBurst >= P1_INTERVAL) {
                ab.lastBurst = now;
                start(ab, aim, 8, 6, false, false);
            }
            if (now - ab.lastSummon >= P1_SUMMON_INTERVAL) {
                ab.lastSummon = now;
                summonSkeletons(level, wither);
            }
            return;
        }
        if (now - ab.lastBurst >= P0_INTERVAL) {
            ab.lastBurst = now;
            start(ab, aim, 3, 8, false, false);
        }
    }

    private static void start(Ability ab, Vec3 aim, int total, int interval, boolean blue, boolean barrage) {
        ab.aim = aim;
        ab.total = total;
        ab.shot = 0;
        ab.interval = interval;
        ab.blue = blue;
        ab.barrage = barrage;
        ab.ticksLeft = total * interval + 1;
    }

    private static void fire(ServerLevel level, WitherBoss wither, Vec3 aim, boolean blue, boolean barrage) {
        double sx = wither.getX();
        double sy = wither.getY() + 2.2D;
        double sz = wither.getZ();
        if (barrage) {
            sx += (level.getRandom().nextDouble() - 0.5D) * 8.0D;
            sz += (level.getRandom().nextDouble() - 0.5D) * 8.0D;
            sy += 12.0D;
        }
        double dx = aim.x - sx;
        double dy = aim.y - sy;
        double dz = aim.z - sz;
        WitherSkull skull = new WitherSkull(level, wither, dx, dy, dz);
        skull.setPos(sx, sy, sz);

        level.addFreshEntity(skull);
        level.playSound(null, sx, sy, sz, net.minecraft.sounds.SoundEvents.WITHER_SHOOT,
                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private static void summonSkeletons(ServerLevel level, WitherBoss wither) {
        var random = level.getRandom();
        for (int i = 0; i < 2; i++) {
            double ox = (random.nextDouble() - 0.5D) * 8.0D;
            double oz = (random.nextDouble() - 0.5D) * 8.0D;
            WitherSkeleton skeleton = net.minecraft.world.entity.EntityType.WITHER_SKELETON
                    .create(level);
            if (skeleton == null) {
                continue;
            }
            skeleton.moveTo(wither.getX() + ox, wither.getY(), wither.getZ() + oz,
                    random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(skeleton);
        }
    }

    private static Player nearestPlayer(ServerLevel level, WitherBoss wither, double range) {
        AABB box = wither.getBoundingBox().inflate(range);
        Player best = null;
        double bestSq = Double.MAX_VALUE;
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            double d = wither.distanceToSqr(p);
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    private WitherSkills() {
    }
}
