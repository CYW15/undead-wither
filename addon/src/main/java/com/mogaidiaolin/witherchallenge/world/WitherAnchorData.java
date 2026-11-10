package com.mogaidiaolin.witherchallenge.world;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 「凋灵锚点」：记录每只活凋灵的位置，让它的区块**永远保持加载**，
 * 不依赖「当前有没有被玩家加载到」。
 *
 * <p>0.0.27 之前只靠每 20 tick 扫描已经加载的实体来强制加载区块。
 * 玩家离开维度后一旦那只凋灵所在的区块被卸掉，我们就再也看不到它，
 * 于是停止加载 —— 凋灵就此「消失」（护身符也找不到）。</p>
 *
 * <p>现在改为：凋灵一生成就登记锚点（UUID + 坐标），此后每秒都按**记录的坐标**
 * 强制加载 9×9 区块；只有确认凋灵死亡/被移除才注销锚点。坐标每 5 秒用真实实体刷新一次。</p>
 */
public class WitherAnchorData extends SavedData {

    public static final int RADIUS = 4;   // 9×9 区块 = 144×144 格

    /** level -> (uuid -> 记录坐标) */
    private final Map<String, Map<UUID, BlockPos>> anchors = new HashMap<>();

    public static WitherAnchorData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                WitherAnchorData::load, WitherAnchorData::new, "witherchallenge_wither_anchors");
    }

    public static WitherAnchorData load(CompoundTag tag) {
        WitherAnchorData data = new WitherAnchorData();
        ListTag dims = tag.getList("Dims", 10);
        for (int i = 0; i < dims.size(); i++) {
            CompoundTag d = dims.getCompound(i);
            String dim = d.getString("Dim");
            ListTag list = d.getList("Anchors", 10);
            Map<UUID, BlockPos> map = new HashMap<>();
            for (int j = 0; j < list.size(); j++) {
                CompoundTag a = list.getCompound(j);
                map.put(a.getUUID("Id"), new BlockPos(a.getInt("X"), a.getInt("Y"), a.getInt("Z")));
            }
            data.anchors.put(dim, map);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag dims = new ListTag();
        anchors.forEach((dim, map) -> {
            if (map.isEmpty()) {
                return;
            }
            CompoundTag d = new CompoundTag();
            d.putString("Dim", dim);
            ListTag list = new ListTag();
            map.forEach((id, pos) -> {
                CompoundTag a = new CompoundTag();
                a.putUUID("Id", id);
                a.putInt("X", pos.getX());
                a.putInt("Y", pos.getY());
                a.putInt("Z", pos.getZ());
                list.add(a);
            });
            d.put("Anchors", list);
            dims.add(d);
        });
        tag.put("Dims", dims);
        return tag;
    }

    // ---------------------------------------------------------------- 接口

    public Map<UUID, BlockPos> forLevel(ServerLevel level) {
        return anchors.computeIfAbsent(level.dimension().location().toString(), k -> new HashMap<>());
    }

    public void add(ServerLevel level, UUID id, BlockPos pos) {
        forLevel(level).put(id, pos);
        setDirty();
    }

    public void remove(ServerLevel level, UUID id) {
        if (forLevel(level).remove(id) != null) {
            setDirty();
        }
    }

    public void updatePos(ServerLevel level, UUID id, BlockPos pos) {
        Map<UUID, BlockPos> map = forLevel(level);
        if (map.containsKey(id) && !map.get(id).equals(pos)) {
            map.put(id, pos);
            setDirty();
        }
    }

    // ---------------------------------------------------------------- 强制加载

    /** 每秒调用：按锚点加载区块、清理多余区块、顺便清理已死亡的锚点。 */
    public static void tickLevel(ServerLevel level) {
        WitherAnchorData data = get(level);
        Map<UUID, BlockPos> map = data.forLevel(level);
        if (map.isEmpty()) {
            releaseAll(level);
            return;
        }
        Set<Long> desired = new HashSet<>();
        List<UUID> dead = new ArrayList<>();
        for (Map.Entry<UUID, BlockPos> e : map.entrySet()) {
            Entity entity = level.getEntity(e.getKey());
            if (entity != null) {
                if (!entity.isAlive() || entity.getType() != EntityType.WITHER) {
                    dead.add(e.getKey());
                    continue;
                }
                BlockPos pos = entity.blockPosition();
                data.updatePos(level, e.getKey(), pos);
                forceAround(level, pos, desired);
            } else {
                // 没加载到不等于死了：按最后记录的坐标继续加载，让它重新被加载
                forceAround(level, e.getValue(), desired);
            }
        }
        for (UUID id : dead) {
            data.remove(level, id);
        }
        applyForced(level, desired);
    }

    private static void forceAround(ServerLevel level, BlockPos pos, Set<Long> out) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                out.add((((long) (cx + dx)) << 32) | ((cz + dz) & 0xFFFFFFFFL));
            }
        }
    }

    private static final Map<String, Set<Long>> FORCED = new HashMap<>();

    private static void applyForced(ServerLevel level, Set<Long> desired) {
        String key = level.dimension().location().toString();
        Set<Long> current = FORCED.computeIfAbsent(key, k -> new HashSet<>());
        for (Long c : desired) {
            if (current.add(c)) {
                level.setChunkForced((int) (c >> 32), (int) (long) c, true);
            }
        }
        List<Long> stale = new ArrayList<>();
        for (Long c : current) {
            if (!desired.contains(c)) {
                stale.add(c);
            }
        }
        for (Long c : stale) {
            current.remove(c);
            level.setChunkForced((int) (c >> 32), (int) (long) c, false);
        }
    }

    private static void releaseAll(ServerLevel level) {
        String key = level.dimension().location().toString();
        Set<Long> current = FORCED.remove(key);
        if (current == null) {
            return;
        }
        for (Long c : current) {
            level.setChunkForced((int) (c >> 32), (int) (long) c, false);
        }
    }
}
