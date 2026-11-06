package com.mogaidiaolin.witherchallenge.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 挑战状态：目前只记「谁最后打了某只时空间隙」，供后续的击杀判定 / 成就使用。
 *
 * <p>因为打时空间隙用的是不带实体的伤害源（绕过 W.A.R. 对玩家来源的免疫），
 * 原版的击杀归属就丢了，只能自己记。</p>
 */
public final class ChallengeTracker {

    private static final Map<UUID, UUID> CORPSE_LAST_HITTER = new HashMap<>();

    public static void recordCorpseHit(Entity corpse, Player player) {
        CORPSE_LAST_HITTER.put(corpse.getUUID(), player.getUUID());
    }

    public static UUID lastHitterOf(Entity corpse) {
        return CORPSE_LAST_HITTER.get(corpse.getUUID());
    }

    public static void forget(Entity corpse) {
        CORPSE_LAST_HITTER.remove(corpse.getUUID());
    }

    private ChallengeTracker() {
    }
}
