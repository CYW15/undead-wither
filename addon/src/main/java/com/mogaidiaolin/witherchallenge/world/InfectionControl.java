package com.mogaidiaolin.witherchallenge.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mogaidiaolin.witherchallenge.WitherChallenge;

/**
 * 感染状态机：
 *
 * <p>当世界里的「时空间隙」全部死亡后，**默认直接结束感染**：
 * 之后 W.A.R. 的感染体系（方块扩散实体、WAR 生物击杀普通生物后的转化、刷怪笼刷 WAR 生物）
 * 全部停摆——实现方式是在实体加入世界时拦掉所有 {@code withering_away_reborn:*} 实体，
 * 而 W.A.R. 的方块扩散本身也是靠「扩散实体」做的（soulspread_entity / glich_blocks_spread /
 * obamaspreadentity …），所以一并被拦掉。</p>
 *
 * <p>状态存进 {@link SavedData}，重进世界仍然有效。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class InfectionControl {

    private static final String DATA_NAME = "witherchallenge_infection";

    public static final class State extends SavedData {
        public boolean ended;
        public boolean hadCorpse;
        public int stormSummoned;
        public int enhancedCorpses;
        /** 挑战只认两只加强时空间隙（T4）：记录它们的 UUID。 */
        public final java.util.List<java.util.UUID> challengeCorpses = new java.util.ArrayList<>();
        public int challengeKilled;
        public boolean challengeFailed;
        public boolean challengeCompleted;
        /** 金苹果炖菜压制凋零腐败的时长（分钟），默认 10。 */
        public int sicknessMinutes = 10;
        /** 凋零腐败的治愈模式：true=永久治愈，false=限时治愈（到点再感染）。 */
        public boolean curePermanent = false;

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("Ended", ended);
            tag.putBoolean("HadCorpse", hadCorpse);
            tag.putInt("StormSummoned", stormSummoned);
            tag.putInt("EnhancedCorpses", enhancedCorpses);
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (java.util.UUID id : challengeCorpses) {
                list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            }
            tag.put("ChallengeCorpses", list);
            tag.putInt("ChallengeKilled", challengeKilled);
            tag.putBoolean("ChallengeFailed", challengeFailed);
            tag.putBoolean("ChallengeCompleted", challengeCompleted);
            tag.putInt("SicknessMinutes", sicknessMinutes);
            tag.putBoolean("CurePermanent", curePermanent);
            return tag;
        }

        public static State load(CompoundTag tag) {
            State s = new State();
            s.ended = tag.getBoolean("Ended");
            s.hadCorpse = tag.getBoolean("HadCorpse");
            s.stormSummoned = tag.getInt("StormSummoned");
            s.enhancedCorpses = tag.getInt("EnhancedCorpses");
            net.minecraft.nbt.ListTag list = tag.getList("ChallengeCorpses", 8);
            for (int i = 0; i < list.size(); i++) {
                try {
                    s.challengeCorpses.add(java.util.UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // 坏数据直接跳过
                }
            }
            s.challengeKilled = tag.getInt("ChallengeKilled");
            s.challengeFailed = tag.getBoolean("ChallengeFailed");
            s.challengeCompleted = tag.getBoolean("ChallengeCompleted");
            s.sicknessMinutes = tag.contains("SicknessMinutes") ? tag.getInt("SicknessMinutes") : 10;
            s.curePermanent = tag.getBoolean("CurePermanent");
            return s;
        }
    }

    public static State state(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(State::load, State::new, DATA_NAME);
    }

    public static boolean isEnded(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return state(serverLevel).ended;
    }

    /** 记录「场上曾经有过时空间隙」，用于判断「全部死亡」。 */
    public static void markCorpseSeen(ServerLevel level) {
        State s = state(level);
        if (!s.hadCorpse) {
            s.hadCorpse = true;
            s.setDirty();
        }
    }

    /** 时空间隙死亡后调用：如果一只不剩，就结束感染。 */
    public static void onCorpseDied(ServerLevel level) {
        State s = state(level);
        if (s.ended) {
            return;
        }
        boolean anyLeft = false;
        for (ServerLevel l : level.getServer().getAllLevels()) {
            for (Entity e : l.getAllEntities()) {
                ResourceLocation id = EntityType.getKey(e.getType());
                if (id != null && "withering_away_reborn".equals(id.getNamespace())
                        && "the_corpse_of_the_wither".equals(id.getPath()) && e.isAlive()) {
                    anyLeft = true;
                    break;
                }
            }
            if (anyLeft) {
                break;
            }
        }
        if (anyLeft) {
            return;
        }
        s.ended = true;
        s.setDirty();
        // 通关判定只看「世界上最后一只时空间隙是否被击败」，不看击败数量
        if (s.hadCorpse && !s.challengeCompleted) {
            s.challengeCompleted = true;
            s.setDirty();
            for (net.minecraft.server.level.ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                com.mogaidiaolin.witherchallenge.registry.ModCriteria.trigger(
                        com.mogaidiaolin.witherchallenge.registry.ModCriteria.FINAL, p);
                p.level().playSound(null, p.blockPosition(),
                        net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            WitherChallenge.LOGGER.info("[凋亡回响] 世界上已没有时空间隙：判定为挑战完成");
        }
        endInfection(level.getServer());
    }

    /** 结束感染：广播 + 清掉场上还在扩散的 W.A.R. 生物 + 锁死后续生成。 */
    public static void endInfection(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            // 先收集再 discard：边遍历 getAllEntities() 边删会抛 ConcurrentModification 的变种 NPE
            java.util.List<Entity> toRemove = new java.util.ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                ResourceLocation id = EntityType.getKey(e.getType());
                if (id == null || !"withering_away_reborn".equals(id.getNamespace())) {
                    continue;
                }
                if ("the_corpse_of_the_wither".equals(id.getPath())) {
                    continue;   // corpse 保留成雕像
                }
                if (e instanceof Mob) {
                    toRemove.add(e);
                }
            }
            for (Entity e : toRemove) {
                e.discard();
            }
            if (!toRemove.isEmpty()) {
                WitherChallenge.LOGGER.info("[凋亡回响] 感染结束：清理 {} 个 W.A.R. 生物（{}）", toRemove.size(),
                        level.dimension().location());
            }
            level.playSound(null, level.getSharedSpawnPos(), SoundEvents.WITHER_DEATH, SoundSource.AMBIENT, 0.6F, 1.4F);
        }

    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation id = EntityType.getKey(event.getEntity().getType());
        if (id == null || !"withering_away_reborn".equals(id.getNamespace())) {
            return;
        }
        // 时空间隙出现时记一笔，用来判断「全部死亡」
        if ("the_corpse_of_the_wither".equals(id.getPath())) {
            markCorpseSeen(level);
            State s = state(level);
            if (s.ended) {
                // 新的时空间隙出现 → 感染重新开始（用户 2026-11-06：只看世界上有没有 corpse）
                s.ended = false;
                s.setDirty();
            }
            return;
        }
        // 死亡演出 / 余波这类「线路上的」实体必须放行，否则凋灵死亡链会断
        if (CUTSCENE_IDS.contains(id.getPath())) {
            return;
        }
        if (state(level).ended) {
            // 感染已结束：W.A.R. 不再生成任何东西（含刷怪笼、击杀转化、扩散实体）
            event.setCanceled(true);
            return;
        }
        // 用户 2026-11-08：世界里没有「凋暴 / 凋灵 / 时空间隙」时，W.A.R. 生物一个都不许再生成，
        // 已有的会自然 despawn，于是「杀一个少一个」，很快清空。
        if (!hasInfectionSource(level)) {
            event.setCanceled(true);
        }
    }

    /** 死亡演出/余波等必须放行的实体（不在它们身上做「无来源禁止生成」判定）。 */
    private static final java.util.Set<String> CUTSCENE_IDS = java.util.Set.of(
            "wither_post_death_cutscane_entitty", "the_aftermath", "aftermath");

    /**
     * 这个维度里有没有「感染源」：活着的凋暴、活着的凋灵、或活着的时空间隙。
     * 用户 2026-11-08 定：三者都没有时，W.A.R. 生物不允许新生成。
     */
    public static boolean hasInfectionSource(ServerLevel level) {
        for (Entity e : level.getAllEntities()) {
            if (!e.isAlive()) {
                continue;
            }
            var id = EntityType.getKey(e.getType());
            if (id == null) {
                continue;
            }
            if ("witherstormmod".equals(id.getNamespace()) && "wither_storm".equals(id.getPath())) {
                return true;
            }
            if ("minecraft".equals(id.getNamespace()) && "wither".equals(id.getPath())) {
                return true;
            }
            if ("withering_away_reborn".equals(id.getNamespace())
                    && "the_corpse_of_the_wither".equals(id.getPath())) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        State s = state(event.getServer().overworld());
        if (s.ended) {
            WitherChallenge.LOGGER.info("[凋亡回响] 本存档的感染已结束（{}, 已召唤凋暴 {} 次）",
                    s.hadCorpse, s.stormSummoned);
        }
    }

    /** 记录一次凋暴召唤（用于挑战判定/调试）。 */
    public static void recordStormSummoned(ServerLevel level) {
        State s = state(level);
        s.stormSummoned++;
        s.setDirty();
    }

    /** 把一只加强时空间隙登记为「挑战目标」（最多两只）。 */
    public static void registerChallengeCorpse(ServerLevel level, java.util.UUID id) {
        State s = state(level);
        if (s.challengeCorpses.contains(id) || s.challengeCorpses.size() >= 2) {
            return;
        }
        s.challengeCorpses.add(id);
        s.setDirty();
    }

    public static boolean isChallengeCorpse(ServerLevel level, java.util.UUID id) {
        return state(level).challengeCorpses.contains(id);
    }

    /**
     * 挑战目标死亡：计入进度或判失败。
     *
     * @param legit 是否由玩家 / 驯服的狼造成
     */
    public static void onChallengeCorpseKilled(ServerLevel level, java.util.UUID id, boolean legit,
                                               net.minecraft.server.MinecraftServer server) {
        State s = state(level);
        if (!s.challengeCorpses.contains(id) || s.challengeCompleted) {
            return;
        }
        if (!legit) {
            if (!s.challengeFailed) {
                s.challengeFailed = true;
                s.setDirty();
                server.getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.witherchallenge.challenge.failed"), false);
            }
            return;
        }
        s.challengeKilled++;
        s.setDirty();
        server.getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.witherchallenge.challenge.progress", s.challengeKilled), false);
        if (s.challengeKilled >= 2) {
            s.challengeCompleted = true;
            s.setDirty();
            server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.witherchallenge.challenge.complete"), false);
        }
    }

    private InfectionControl() {
    }
}
