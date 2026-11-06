package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * 「凋灵阵营」内部互不敌对（用户 2026-11-06 要求）。
 *
 * <p>同一阵营 = CWS 生物 + W.A.R. 生物 + 凋灵 + 原版亡灵。阵营内互相设置攻击目标时直接取消；
 * 对玩家、对非亡灵生物的仇恨不受影响。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class FactionRules {

    private static final Set<String> VANILLA_UNDEAD = Set.of(
            "zombie", "husk", "drowned", "zombie_villager", "skeleton", "stray",
            "wither_skeleton", "phantom", "zombie_horse", "skeleton_horse", "zoglin");

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (isSameFaction(event.getEntity(), event.getNewTarget())) {
            event.setCanceled(true);
        }
    }

    public static boolean isSameFaction(Entity a, Entity b) {
        return isFactionMember(a) && isFactionMember(b);
    }

    /** CWS / W.A.R. / 凋灵 / 原版亡灵 都算「凋灵阵营」。 */
    public static boolean isFactionMember(Entity entity) {
        if (entity == null) {
            return false;
        }
        ResourceLocation id = EntityType.getKey(entity.getType());
        if (id == null) {
            return false;
        }
        String ns = id.getNamespace();
        if ("witherstormmod".equals(ns) || "withering_away_reborn".equals(ns)) {
            return true;
        }
        if (!"minecraft".equals(ns)) {
            return false;
        }
        return "wither".equals(id.getPath()) || VANILLA_UNDEAD.contains(id.getPath());
    }

    /** 判断某实体是不是「原版亡灵」（不含凋灵本体）。 */
    public static boolean isVanillaUndead(Entity entity) {
        ResourceLocation id = EntityType.getKey(entity.getType());
        return id != null && "minecraft".equals(id.getNamespace()) && VANILLA_UNDEAD.contains(id.getPath());
    }

    private FactionRules() {
    }
}
