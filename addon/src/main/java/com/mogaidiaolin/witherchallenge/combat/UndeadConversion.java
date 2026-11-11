package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版亡灵**看到「凋灵侧」实体就转化**（用户 2026-11-11 重新确认）。
 *
 * <p><b>前提</b>：这个世界（维度）里存在「凋灵 / 凋暴 / 时空间隙」三者之一，
 * 转化机制才生效（用户 2026-11-11 确认）。没有这三样时，亡灵看到什么都不转化。</p>
 *
 * <p><b>触发对象</b>：W.A.R. 的任何生物、凋灵本体、凋暴、凋零骷髅。
 * 距离在该亡灵自己的侦测范围（{@code FOLLOW_RANGE}）内且有视线即可，不要求接触。</p>
 *
 * <ul>
 *   <li>每 10 tick 扫一次，只做距离 + 视线判定，不碰 AI/寻路；</li>
 *   <li>转化走 {@link ConversionTable}（对应关系见 task文档更新/转化表.docx）；</li>
 *   <li>表里没有的亡灵不产生任何效果。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class UndeadConversion {

    /** 侦测范围取不到时的兜底值。 */
    private static final double FALLBACK_RANGE = 16.0D;

    /** 凋灵侧实体判定。 */
    private static boolean isWitherSide(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        String ns = id.getNamespace();
        String path = id.getPath();
        if ("withering_away_reborn".equals(ns)) {
            return true;
        }
        if ("minecraft".equals(ns)) {
            return "wither".equals(path) || "wither_skeleton".equals(path);
        }
        if ("witherstormmod".equals(ns)) {
            return "wither_storm".equals(path) || path.contains("wither_skeleton");
        }
        return false;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        List<Entity> all = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            all.add(e);
        }
        boolean enabled = false;
        List<Entity> sources = new ArrayList<>();
        List<Mob> undead = new ArrayList<>();
        for (Entity e : all) {
            if (!e.isAlive()) {
                continue;
            }
            ResourceLocation id = EntityType.getKey(e.getType());
            if (isWitherSide(id)) {
                sources.add(e);
                String ns = id.getNamespace();
                String path = id.getPath();
                if ("minecraft".equals(ns) && "wither".equals(path)) {
                    enabled = true;
                } else if ("witherstormmod".equals(ns) && "wither_storm".equals(path)) {
                    enabled = true;
                } else if ("withering_away_reborn".equals(ns) && "the_corpse_of_the_wither".equals(path)) {
                    enabled = true;
                }
            } else if (e instanceof Mob mob && FactionRules.isVanillaUndead(mob)) {
                undead.add(mob);
            }
        }
        if (!enabled) {
            return;   // 没有凋灵 / 凋暴 / 时空间隙 → 不转化
        }
        if (undead.isEmpty() || sources.isEmpty()) {
            return;
        }
        for (Mob watcher : undead) {
            if (!watcher.isAlive()) {
                continue;
            }
            double range = watcher.getAttribute(Attributes.FOLLOW_RANGE) != null
                    ? watcher.getAttributeValue(Attributes.FOLLOW_RANGE)
                    : FALLBACK_RANGE;
            if (range <= 0.0D) {
                range = FALLBACK_RANGE;
            }
            double rangeSq = range * range;
            for (Entity source : sources) {
                if (source == watcher || !source.isAlive() || watcher.distanceToSqr(source) > rangeSq) {
                    continue;
                }
                if (!watcher.hasLineOfSight(source)) {
                    continue;
                }
                convert(watcher, level);
                break;
            }
        }
    }

    /** 表里没有对应目标的亡灵：不产生任何效果（用户 2026-11-07 定）。 */
    private static void convert(Mob undead, ServerLevel level) {
        if (!ConversionTable.canConvert(undead)) {
            return;
        }
        ConversionTable.convert(undead, level);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                undead.getX(), undead.getY() + 0.8D, undead.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.01D);
    }

    private UndeadConversion() {
    }
}
