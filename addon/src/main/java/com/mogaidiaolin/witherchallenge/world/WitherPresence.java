package com.mogaidiaolin.witherchallenge.world;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 凋灵（原版）的「凋暴式」存在感（用户 2026-11-08 要求，0.0.19 测试版）。
 *
 * <ul>
 *   <li><b>强制刷新</b>：只要凋灵活着，就把它周围 5×5 区块强制加载（离开视野也不卸载）；</li>
 *   <li><b>锁黑夜</b>：所在维度有活着的凋灵时，时间被钉在夜晚（18000），
 *       无论玩家离它多远；没有凋灵后自然恢复昼夜循环；</li>
 *   <li><b>不强制追击</b>：我们不改它的 AI / 目标，玩家跑远它不会跟着传送；</li>
 *   <li><b>命令护身符定位</b>：手持 CWS 的护身符时，动作栏分别显示
 *       「最近的凋灵」与「最近的凋暴」——两个独立功能，互不影响。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class WitherPresence {

    private static final int FORCE_RADIUS = 2;          // 5×5 区块
    private static final long NIGHT_TIME = 18000L;
    /** 每 5 分钟给每只凋灵刷 4 只凋零骷髅（用户 2026-11-08 定）。 */
    private static final int SPAWN_PERIOD = 20 * 60 * 5;
    private static final int SPAWN_COUNT = 4;
    private static final int SKELETON_CAP = 24;
    private static final double AMULET_RANGE = 4096.0D;

    /** 每个维度当前被我们强制加载的区块（用于回收）。 */
    private static final Map<ServerLevel, Set<Long>> FORCED = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> withers = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e.isAlive() && e.getType() == EntityType.WITHER) {
                    withers.add(e);
                }
            }
            boolean night = !withers.isEmpty();
            updateForcedChunks(level, withers);
            if (night && server.getTickCount() % SPAWN_PERIOD == 0) {
                for (Entity w : withers) {
                    spawnWitherSkeletons(level, w);
                }
            }
            if (night) {
                level.setDayTime(NIGHT_TIME);
            }
        }
        if (server.getTickCount() % 10 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncNearestWither(player);
            }
        }
    }

    private static void spawnWitherSkeletons(ServerLevel level, Entity wither) {
        int nearby = 0;
        for (Entity e : level.getAllEntities()) {
            if (e.isAlive() && e.getType() == EntityType.WITHER_SKELETON
                    && e.blockPosition().closerThan(wither.blockPosition(), 32)) {
                nearby++;
            }
        }
        var random = level.getRandom();
        for (int i = 0; i < SPAWN_COUNT && nearby + i < SKELETON_CAP; i++) {
            double ox = (random.nextDouble() - 0.5D) * 10.0D;
            double oz = (random.nextDouble() - 0.5D) * 10.0D;
            BlockPos pos = BlockPos.containing(wither.getX() + ox, wither.getY() + 1, wither.getZ() + oz);
            EntityType.WITHER_SKELETON.spawn(level, pos, net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED);
        }
    }

    // ------------------------------------------------------------- 强制加载

    private static void updateForcedChunks(ServerLevel level, List<Entity> withers) {
        Set<Long> desired = new HashSet<>();
        for (Entity w : withers) {
            BlockPos pos = w.blockPosition();
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            for (int dx = -FORCE_RADIUS; dx <= FORCE_RADIUS; dx++) {
                for (int dz = -FORCE_RADIUS; dz <= FORCE_RADIUS; dz++) {
                    desired.add(pack(cx + dx, cz + dz));
                }
            }
        }
        Set<Long> current = FORCED.computeIfAbsent(level, k -> new HashSet<>());
        for (Long key : desired) {
            if (current.add(key)) {
                level.setChunkForced((int) (key >> 32), (int) (long) key, true);
            }
        }
        List<Long> stale = new ArrayList<>();
        for (Long key : current) {
            if (!desired.contains(key)) {
                stale.add(key);
            }
        }
        for (Long key : stale) {
            current.remove(key);
            level.setChunkForced((int) (key >> 32), (int) (long) key, false);
        }
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------- 同步给客户端（护身符 tooltip）

    private static void syncNearestWither(ServerPlayer player) {
        Entity wither = nearest(player, EntityType.WITHER);
        var msg = wither == null
                ? new com.mogaidiaolin.witherchallenge.net.WitherPosMessage(false, 0, 0, 0, 0, "", "")
                : new com.mogaidiaolin.witherchallenge.net.WitherPosMessage(true,
                        wither.getX(), wither.getY(), wither.getZ(),
                        (int) Math.sqrt(player.distanceToSqr(wither)),
                        compass(player, wither),
                        player.level().dimension().location().toString());
        com.mogaidiaolin.witherchallenge.net.WCPackets.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** 八个方向，按玩家朝向换算成「前/后/左/右」式方位更直观，这里给绝对方位。 */
    public static String compass(ServerPlayer player, Entity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));   // 东=0，南=90
        String[] dirs = {"东", "东南", "南", "西南", "西", "西北", "北", "东北"};
        int idx = (int) Math.round(((angle + 360) % 360) / 45.0) % 8;
        return dirs[idx];
    }

    public static Entity nearest(ServerPlayer player, EntityType<?> type) {
        Entity best = null;
        double bestSq = Double.MAX_VALUE;
        for (Entity e : ((ServerLevel) player.level()).getAllEntities()) {
            if (!e.isAlive() || e.getType() != type) {
                continue;
            }
            double d = player.distanceToSqr(e);
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    public static Entity nearestByType(ServerPlayer player, String namespace, String path) {
        Entity best = null;
        double bestSq = Double.MAX_VALUE;
        for (Entity e : ((ServerLevel) player.level()).getAllEntities()) {
            if (!e.isAlive()) {
                continue;
            }
            var id = EntityType.getKey(e.getType());
            if (id == null || !namespace.equals(id.getNamespace()) || !path.equals(id.getPath())) {
                continue;
            }
            double d = player.distanceToSqr(e);
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    private WitherPresence() {
    }
}
