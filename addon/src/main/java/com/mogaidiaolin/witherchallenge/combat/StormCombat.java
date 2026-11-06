package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import com.mogaidiaolin.witherchallenge.world.BlockInfection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import nonamecrackers2.witherstormmod.common.entity.WitherStormEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 凋暴强化（需求文档第 3 条的 (1)(2)(4)(5)(7)）。
 *
 * <ul>
 *   <li><b>1–3 阶段 8 连发</b>：拦掉 CWS 自己的单发凋零之首，替换成「黑黑黑蓝黑黑黑蓝」，
 *       发与发之间 4 tick。</li>
 *   <li><b>4 阶段起本体停火</b>：拦掉本体的凋零之首。</li>
 *   <li><b>Head 机炮</b>：每 0.2 秒 1 发蓝色头颅，持续 5 秒；全局唯一冷却 8 秒。</li>
 *   <li><b>牵引光束感染</b>：光束落点半径 3 格按表感染方块。</li>
 *   <li><b>召唤 W.A.R. 生物</b>：每 5 秒 1–2 只；带 tag 的上限 60、全服 W.A.R. 生物上限 120；
 *       距离凋暴超过 200 格的带 tag 生物直接移除。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class StormCombat {

    private static final ResourceLocation STORM = new ResourceLocation("witherstormmod", "wither_storm");
    private static final ResourceLocation SKULL = new ResourceLocation("witherstormmod", "flaming_wither_skull");
    private static final ResourceLocation BLUE_SKULL =
            new ResourceLocation("witherstormmod", "blue_flaming_wither_skull");

    private static final int BURST_SHOTS = 8;
    private static final int BURST_INTERVAL = 4;
    private static final int CANNON_SHOTS = 25;          // 0.2 秒一发 × 5 秒
    private static final int CANNON_INTERVAL = 4;
    private static final int CANNON_COOLDOWN = 160;      // 8 秒（全局）
    private static final int BEAM_INTERVAL = 20;
    private static final int SUMMON_INTERVAL = 100;      // 5 秒
    private static final int TAG_CAP = 60;
    private static final int WAR_CAP = 120;
    private static final double REMOVE_DISTANCE = 200.0D;
    public static final String SPAWNED_TAG = "wither_storm_spawned";

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static long cannonReadyAt = 0L;
    private static int cannonTicksLeft = 0;
    private static int cannonShot = 0;
    private static WitherStormEntity cannonStorm;
    private static Vec3 cannonTarget;
    private static boolean spawningOwnSkull;

    private static final class Burst {
        final WitherStormEntity storm;
        final Vec3 target;
        int shot;
        int ticksLeft;

        Burst(WitherStormEntity storm, Vec3 target) {
            this.storm = storm;
            this.target = target;
            this.shot = 0;
            this.ticksLeft = 0;
        }
    }

    @SubscribeEvent
    public static void onSkullJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || spawningOwnSkull) {
            return;
        }
        ResourceLocation id = EntityType.getKey(event.getEntity().getType());
        if (!SKULL.equals(id) && !BLUE_SKULL.equals(id)) {
            return;
        }
        if (!(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile)) {
            return;
        }
        if (!(projectile.getOwner() instanceof WitherStormEntity storm) || !(storm.level() instanceof ServerLevel level)) {
            return;
        }
        // 本体射出的凋零之首一律拦下，由这里接管节奏
        event.setCanceled(true);
        if (storm.getPhase() > 2) {
            return;   // 4 阶段起本体停火，只靠 Head 机炮
        }
        LivingEntity target = firstTarget(storm);
        Vec3 aim = target != null
                ? new Vec3(target.getX(), target.getY() + target.getEyeHeight() * 0.5D, target.getZ())
                : event.getEntity().position().add(event.getEntity().getDeltaMovement().normalize().scale(16.0D));
        synchronized (BURSTS) {
            BURSTS.add(new Burst(storm, aim));
        }
        if (level.getGameTime() % 200 == 0) {
            WitherChallenge.LOGGER.debug("[凋亡回响] 接管凋暴齐射（phase={}）", storm.getPhase());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.currentTimeMillis();
        tickBursts();
        tickCannon(now);
    }

    private static void tickBursts() {
        synchronized (BURSTS) {
            Iterator<Burst> it = BURSTS.iterator();
            while (it.hasNext()) {
                Burst burst = it.next();
                if (!burst.storm.isAlive()) {
                    it.remove();
                    continue;
                }
                if (burst.ticksLeft-- > 0) {
                    continue;
                }
                if (burst.shot >= BURST_SHOTS) {
                    it.remove();
                    continue;
                }
                int head = burst.shot % Math.max(1, headCount(burst.storm));
                boolean blue = burst.shot == 3 || burst.shot == 7;
                fireSkull(burst.storm, head, burst.target, blue);
                burst.shot++;
                burst.ticksLeft = BURST_INTERVAL;
            }
        }
    }

    private static void tickCannon(long nowMs) {
        if (cannonTicksLeft > 0) {
            if (!cannonStorm.isAlive() || cannonTarget == null) {
                cannonTicksLeft = 0;
                cannonReadyAt = nowMs + CANNON_COOLDOWN * 50L;
                return;
            }
            if (--cannonTicksLeft % CANNON_INTERVAL == 0) {
                int head = cannonShot % Math.max(1, headCount(cannonStorm));
                fireSkull(cannonStorm, head, cannonTarget, true);
                cannonShot++;
            }
            if (cannonTicksLeft <= 0) {
                cannonReadyAt = nowMs + CANNON_COOLDOWN * 50L;   // 全局 8 秒冷却
            }
            return;
        }
        if (nowMs < cannonReadyAt) {
            return;
        }
        for (ServerLevel level : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof WitherStormEntity storm && storm.isAlive() && storm.getPhase() >= 3) {
                    LivingEntity target = firstTarget(storm);
                    if (target == null) {
                        continue;
                    }
                    cannonStorm = storm;
                    cannonTarget = new Vec3(target.getX(), target.getY() + target.getEyeHeight() * 0.5D, target.getZ());
                    cannonShot = 0;
                    cannonTicksLeft = CANNON_SHOTS * CANNON_INTERVAL;
                    return;
                }
            }
        }
    }

    private static void fireSkull(WitherStormEntity storm, int head, Vec3 target, boolean blue) {
        spawningOwnSkull = true;
        try {
            if (blue) {
                storm.spawnBlueFlamingWitherSkull(head, target.x, target.y, target.z);
            } else {
                storm.spawnFlamingWitherSkull(head, target.x, target.y, target.z);
            }
        } catch (Throwable t) {
            WitherChallenge.LOGGER.warn("[凋亡回响] 发射头颅失败：{}", t.toString());
        } finally {
            spawningOwnSkull = false;
        }
    }

    private static LivingEntity firstTarget(WitherStormEntity storm) {
        for (int i = 0; i < headCount(storm); i++) {
            LivingEntity t = storm.getTarget(i);
            if (t != null && t.isAlive()) {
                return t;
            }
        }
        if (storm.level() instanceof ServerLevel level) {
            var player = level.getNearestPlayer(storm, 256.0D);
            if (player != null) {
                return player;
            }
        }
        return null;
    }

    private static int headCount(WitherStormEntity storm) {
        try {
            return Math.max(1, storm.getHeadManager().getHeads().size());
        } catch (Throwable t) {
            return 1;
        }
    }

    // ------------------------------------------------------------------ 光束感染 + 召唤

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long time = level.getGameTime();
        if (time % BEAM_INTERVAL != 0) {
            return;
        }
        for (Entity entity : new ArrayList<>(iterableToList(level))) {
            if (!(entity instanceof WitherStormEntity storm) || !storm.isAlive()) {
                continue;
            }
            if (com.mogaidiaolin.witherchallenge.WCConfig.BEAM_INFECTION.get()) {
                infectFromBeams(level, storm);
            }
            if (time % SUMMON_INTERVAL == 0) {
                summonWarMobs(level, storm);
                purgeFarAway(level, storm);
            }
        }
    }

    private static List<Entity> iterableToList(ServerLevel level) {
        List<Entity> list = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            list.add(e);
        }
        return list;
    }

    private static void infectFromBeams(ServerLevel level, WitherStormEntity storm) {
        int heads = headCount(storm);
        var random = level.getRandom();
        for (int head = 0; head < heads; head++) {
            boolean active;
            try {
                active = storm.tractorBeamActive(head);
            } catch (Throwable t) {
                continue;
            }
            if (!active) {
                continue;
            }
            Vec3 from = storm.getHeadPos(head);
            double cutoff = 48.0D;
            try {
                double cfg = storm.getTractorBeamCutoffDistance(head);
                if (cfg > 0) {
                    cutoff = cfg;
                }
            } catch (Throwable ignored) {
            }
            Vec3 dir = Vec3.directionFromRotation(storm.getHeadXRot(head), storm.getHeadYRot(head)).scale(cutoff);
            BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, storm));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 impact = hit.getLocation();
            BlockInfection.infectAround(level, impact.x, impact.y, impact.z, 3.0D, 24, random);
        }
    }

    private static void summonWarMobs(ServerLevel level, WitherStormEntity storm) {
        int tagged = 0;
        int totalWar = 0;
        for (ServerLevel l : level.getServer().getAllLevels()) {
            for (Entity e : l.getAllEntities()) {
                ResourceLocation id = EntityType.getKey(e.getType());
                if (id == null || !"withering_away_reborn".equals(id.getNamespace())) {
                    continue;
                }
                totalWar++;
                if (e.getTags().contains(SPAWNED_TAG)) {
                    tagged++;
                }
            }
        }
        if (tagged >= com.mogaidiaolin.witherchallenge.WCConfig.SUMMON_TAG_CAP.get()
                || totalWar >= com.mogaidiaolin.witherchallenge.WCConfig.SUMMON_TOTAL_CAP.get()) {
            return;
        }
        int amount = 1 + level.getRandom().nextInt(2);
        String[] pool = {"withering_zombie", "withering_spider", "withering_enderman",
                "new_withering_creeper", "withering_pig"};
        for (int i = 0; i < amount; i++) {
            EntityType<?> type = EntityType.byString(
                    "withering_away_reborn:" + pool[level.getRandom().nextInt(pool.length)]).orElse(null);
            if (type == null) {
                continue;
            }
            Entity spawned = type.create(level);
            if (spawned == null) {
                continue;
            }
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double dist = 8.0D + level.getRandom().nextDouble() * 16.0D;
            double x = storm.getX() + Math.cos(angle) * dist;
            double z = storm.getZ() + Math.sin(angle) * dist;
            spawned.moveTo(x, storm.getY() + 1.0D, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            spawned.addTag(SPAWNED_TAG);
            level.addFreshEntity(spawned);
        }
    }

    private static void purgeFarAway(ServerLevel level, WitherStormEntity storm) {
        List<Entity> remove = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob && e.getTags().contains(SPAWNED_TAG)
                    && e.distanceToSqr(storm) > REMOVE_DISTANCE * REMOVE_DISTANCE) {
                remove.add(e);
            }
        }
        for (Entity e : remove) {
            e.discard();
        }
    }

    private StormCombat() {
    }
}
