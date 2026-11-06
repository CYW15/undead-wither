# -*- coding: utf-8 -*-
"""0.0.5 的一批源码修改：去掉挑战播报、改成「最后一只 corpse 死亡」判定、
rage 持续锁定、CWS/WAR 互不敌对、凋零腐败的临时治疗与指令配置。"""
import io
import os
import re

AI = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # 本脚本在 AI_document/tools 下
JAVA = os.path.join(AI, "addon", "src", "main", "java", "com", "mogaidiaolin", "witherchallenge")


def read(p):
    return open(p, encoding="utf-8").read()


def write(p, t):
    open(p, "w", encoding="utf-8", newline="").write(t)


# ---------------------------------------------------------------- 1. InfectionControl
p = os.path.join(JAVA, "world", "InfectionControl.java")
t = read(p)

# 去掉「感染重新开始」播报
t = t.replace('''                s.ended = false;
                s.setDirty();
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.witherchallenge.infection.resumed"), false);''',
'''                s.ended = false;
                s.setDirty();''')

# 去掉「感染终止」播报
t = t.replace('''        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.witherchallenge.infection.ended"), false);''', '')

# 挑战完成判定：改成「最后一只 corpse 死亡」，去掉所有播报
t = t.replace('''        if (!legit) {
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
            for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
                com.mogaidiaolin.witherchallenge.registry.ModCriteria.trigger(
                        com.mogaidiaolin.witherchallenge.registry.ModCriteria.FINAL, p);
            }
        }''',
'''        if (!legit) {
            s.challengeFailed = true;
            s.setDirty();
            return;
        }
        s.challengeKilled++;
        s.setDirty();''')

write(p, t)
print("InfectionControl patched")

# ---------------------------------------------------------------- 2. CorpseRules
p = os.path.join(JAVA, "combat", "CorpseRules.java")
t = read(p)

# 2a. 同一只凋暴只补一只 corpse（死亡事件 + 看门狗 会重复）
t = t.replace('''    private static final java.util.Map<java.util.UUID, TrackedStorm> TRACKED_STORMS = new java.util.HashMap<>();''',
'''    private static final java.util.Map<java.util.UUID, TrackedStorm> TRACKED_STORMS = new java.util.HashMap<>();
    /** 已经为哪只凋暴补过 corpse，避免「死亡事件 + 看门狗」重复生成导致看起来像复活。 */
    private static final java.util.Set<java.util.UUID> HANDLED_STORMS = new java.util.HashSet<>();''')

t = t.replace('''        synchronized (PENDING_CORPSES) {
            PENDING_CORPSES.add(new PendingCorpse(level, new Vec3(entity.getX(), entity.getY(), entity.getZ()), 20));
        }''',
'''        if (!HANDLED_STORMS.add(entity.getUUID())) {
            return;
        }
        synchronized (PENDING_CORPSES) {
            PENDING_CORPSES.add(new PendingCorpse(level, new Vec3(entity.getX(), entity.getY(), entity.getZ()), 20));
        }''')

t = t.replace('''            if (storm == null || !storm.isAlive()) {
                gone.add(entry.getKey());
                synchronized (PENDING_CORPSES) {
                    PENDING_CORPSES.add(new PendingCorpse(tracked.level(), tracked.pos(), 20));
                }''',
'''            if (storm == null || !storm.isAlive()) {
                gone.add(entry.getKey());
                if (HANDLED_STORMS.add(entry.getKey())) {
                    synchronized (PENDING_CORPSES) {
                        PENDING_CORPSES.add(new PendingCorpse(tracked.level(), tracked.pos(), 20));
                    }
                } else {
                    continue;
                }''')

# 2b. 只要有加强 corpse 存在就锁 rage（每 20 tick 检查一次）
t = t.replace('''            if (!isEnhanced(corpse)) {
                continue;
            }''',
'''            if (!isEnhanced(corpse)) {
                continue;
            }
            lockRage(level);''')

write(p, t)
print("CorpseRules patched")
