package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
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
 * 有时空间隙在场时，原版亡灵**只要注意到** W.A.R. 生物就转化（不需要接触）。
 *
 * <p>用户 2026-11-07 定：不再要求碰撞箱接触，也不驱动寻路——
 * 只要「看得见」就转化：距离在该亡灵自己的侦测范围（{@code FOLLOW_RANGE}）内，
 * 且有视线（{@code hasLineOfSight}）。</p>
 *
 * <ul>
 *   <li>触发条件：同维度存在活着的时空间隙；</li>
 *   <li>每 10 tick 扫一次，只做距离 + 视线判定，**不碰 AI/寻路**；</li>
 *   <li>转化走 {@link ConversionTable}；表里没有的亡灵挂凋零 II。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class UndeadConversion {

    /** 侦测范围取不到时的兜底值。 */
    private static final double FALLBACK_RANGE = 16.0D;

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
        boolean corpseNearby = false;
        for (Entity e : all) {
            if (WeaponCombatEvents.isCorpse(e) && e.isAlive()) {
                corpseNearby = true;
                break;
            }
        }
        if (!corpseNearby) {
            return;
        }

        List<Mob> warMobs = new ArrayList<>();
        List<Mob> undead = new ArrayList<>();
        for (Entity e : all) {
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            if (FactionRules.isVanillaUndead(mob)) {
                undead.add(mob);
            } else {
                var id = EntityType.getKey(mob.getType());
                if (id != null && "withering_away_reborn".equals(id.getNamespace())) {
                    warMobs.add(mob);
                }
            }
        }
        if (undead.isEmpty() || warMobs.isEmpty()) {
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
            for (Mob war : warMobs) {
                if (!war.isAlive() || watcher.distanceToSqr(war) > rangeSq) {
                    continue;
                }
                if (!watcher.hasLineOfSight(war)) {
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
