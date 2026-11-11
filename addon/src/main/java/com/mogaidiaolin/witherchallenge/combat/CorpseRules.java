package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import com.mogaidiaolin.witherchallenge.world.BlockInfection;
import com.mogaidiaolin.witherchallenge.world.InfectionControl;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import nonamecrackers2.witherstormmod.common.entity.WitherStormEntity;

/**
 * 时空间隙的加强版规则（需求 Q60–Q62、T7、T10、Q56–Q59）。
 *
 * <ul>
 *   <li>加强：最大生命 150、贴 {@code wc_enhanced} 标签、锁 rage、方块感染加速、W.A.R. 生物生成加速</li>
 *   <li>免疫一切状态效果、不能被载具搬运、永不 despawn</li>
 *   <li>所有时空间隙死亡后直接结束感染（{@link InfectionControl}）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class CorpseRules {

    public static final String ENHANCED_TAG = "wc_enhanced";
    public static final float ENHANCED_HEALTH = 150.0F;
    private static final int INFECT_INTERVAL = 20;      // 每秒一次（约为未加强版的 10 倍）
    private static final int SPAWN_INTERVAL = 100;      // 每 5 秒 1 只
    private static final int SPAWN_CAP = 40;

    public static boolean isEnhanced(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(ENHANCED_TAG);
    }

    /** 把一只时空间隙变成加强版。 */
    public static void enhance(LivingEntity corpse, ServerLevel level) {
        if (isEnhanced(corpse)) {
            return;
        }
        corpse.getPersistentData().putBoolean(ENHANCED_TAG, true);
        if (corpse.getAttribute(Attributes.MAX_HEALTH) != null) {
            corpse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ENHANCED_HEALTH);
        }
        corpse.setHealth(ENHANCED_HEALTH);
        if (corpse instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        InfectionControl.state(level).enhancedCorpses++;
        InfectionControl.state(level).setDirty();
        InfectionControl.registerChallengeCorpse(level, corpse.getUUID());
        lockRage(level);
        level.playSound(null, corpse.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                corpse.getX(), corpse.getY() + 1.0D, corpse.getZ(), 60, 1.2D, 1.6D, 1.2D, 0.02D);
    }

    /** 锁 rage（W.A.R. 的世界变量）；本流程内不允许再关掉。 */
    public static void lockRage(ServerLevel level) {
        try {
            net.mcreator.witheringawayreborn.network.WitheringAwayRebornModVariables.WorldVariables vars =
                    net.mcreator.witheringawayreborn.network.WitheringAwayRebornModVariables.WorldVariables.get(level);
            vars.rage = true;
            vars.syncData(level);
        } catch (Throwable t) {
            WitherChallenge.LOGGER.warn("[凋亡回响] 锁定 rage 失败：{}", t.toString());
        }
    }

    /** 在指定位置召唤凋暴（phase 0）。 */
    public static boolean summonStorm(ServerLevel level, Vec3 pos, Player by) {
        EntityType<?> type = EntityType.byString("witherstormmod:wither_storm").orElse(null);
        if (type == null) {
            return false;
        }
        Entity storm = type.create(level);
        if (storm == null) {
            return false;
        }
        storm.moveTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(storm);
        if (storm instanceof WitherStormEntity witherStorm) {
            witherStorm.setPhase(0);
        }
        InfectionControl.recordStormSummoned(level);
        if (by instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.mogaidiaolin.witherchallenge.registry.ModCriteria.trigger(
                    com.mogaidiaolin.witherchallenge.registry.ModCriteria.AWAKEN_STORM, sp);
        }
        level.playSound(null, storm.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, 100.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0D, pos.z, 4, 1.0D, 1.0D, 1.0D, 0.0D);
        if (by != null) {
            by.displayClientMessage(Component.translatable("message.witherchallenge.storm.summoned")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        }
        return true;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long time = level.getGameTime();
        if (time % INFECT_INTERVAL != 0) {
            return;
        }
        RandomSource random = level.getRandom();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity corpse) || !WeaponCombatEvents.isCorpse(corpse) || !corpse.isAlive()) {
                continue;
            }
            if (!isEnhanced(corpse)) {
                continue;
            }
            lockRage(level);
            InfectionControl.markCorpseSeen(level);
            // 10 倍速的方块感染：加强版自己周围一圈一圈地啃
            if (com.mogaidiaolin.witherchallenge.WCConfig.ENHANCED_CORPSE_SPREAD.get()) {
                BlockInfection.infectAround(level, corpse.getX(), corpse.getY(), corpse.getZ(), 6.0D, 24, random);
            }
            if (time % SPAWN_INTERVAL == 0) {
                spawnWarMobNear(level, corpse, random);
            }
        }
    }

    private static void spawnWarMobNear(ServerLevel level, LivingEntity corpse, RandomSource random) {
        int nearby = 0;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob && e.blockPosition().closerThan(corpse.blockPosition(), 48)) {
                ResourceLocation id = EntityType.getKey(e.getType());
                if (id != null && "withering_away_reborn".equals(id.getNamespace())) {
                    nearby++;
                }
            }
        }
        if (nearby >= SPAWN_CAP) {
            return;
        }
        String[] candidates = {"withering_zombie", "withering_spider", "withering_enderman", "new_withering_creeper"};
        EntityType<?> type = EntityType.byString(
                "withering_away_reborn:" + candidates[random.nextInt(candidates.length)]).orElse(null);
        if (type == null) {
            return;
        }
        Entity spawned = type.create(level);
        if (spawned == null) {
            return;
        }
        double dx = (random.nextDouble() - 0.5D) * 16.0D;
        double dz = (random.nextDouble() - 0.5D) * 16.0D;
        spawned.moveTo(corpse.getX() + dx, corpse.getY() + 1.0D, corpse.getZ() + dz,
                random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(spawned);
    }

    /** 时空间隙免疫一切状态效果。 */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (WeaponCombatEvents.isCorpse(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** 不能被船/矿车/坐骑搬运（Q59）。 */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (WeaponCombatEvents.isCorpse(event.getEntity()) || WeaponCombatEvents.isCorpse(event.getEntityBeingMounted())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!WeaponCombatEvents.isCorpse(entity)) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        // 击杀归属：终结武器走的是不带实体的伤害源，所以先看 ChallengeTracker；
        // 其余情况按原版判断（玩家 / 玩家射出的箭 / 已驯服的狼）。
        java.util.UUID hitter = ChallengeTracker.lastHitterOf(entity);
        Entity killer = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        boolean legit = hitter != null
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                || killer instanceof Player
                || (killer instanceof net.minecraft.world.entity.animal.Wolf wolf && wolf.isTame())
                || (direct instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                    && arrow.getOwner() instanceof Player);
        InfectionControl.onChallengeCorpseKilled(level, entity.getUUID(), legit, level.getServer());
        ChallengeTracker.forget(entity);
        InfectionControl.onCorpseDied(level);
        level.playSound(null, entity.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    /** 凋暴死亡 1 秒后，在死亡点释放一只加强版时空间隙（需求 Q62）。 */
    @SubscribeEvent
    public static void onStormDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        ResourceLocation id = EntityType.getKey(entity.getType());
        if (id == null || !"witherstormmod".equals(id.getNamespace()) || !"wither_storm".equals(id.getPath())) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!HANDLED_STORMS.add(entity.getUUID())) {
            return;
        }
        synchronized (PENDING_CORPSES) {
            PENDING_CORPSES.add(new PendingCorpse(level, new Vec3(entity.getX(), entity.getY(), entity.getZ()), 20));
        }
        WitherChallenge.LOGGER.info("[凋亡回响] 凋暴死亡，1 秒后在 ({}, {}, {}) 生成加强版时空间隙",
                (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
    }

    private record PendingCorpse(ServerLevel level, Vec3 pos, int ticksLeft) {
    }

    private record TrackedStorm(ServerLevel level, Vec3 pos) {
    }

    private static final java.util.List<PendingCorpse> PENDING_CORPSES = new java.util.ArrayList<>();
    /** 在场凋暴的 UUID → 最后位置；凋暴消失（死亡或被移除）后就补一只加强版 corpse。 */
    private static final java.util.Map<java.util.UUID, TrackedStorm> TRACKED_STORMS = new java.util.HashMap<>();
    /** 已经为哪只凋暴补过 corpse，避免「死亡事件 + 看门狗」重复生成导致看起来像复活。 */
    private static final java.util.Set<java.util.UUID> HANDLED_STORMS = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onStormJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation id = EntityType.getKey(event.getEntity().getType());
        if (id == null || !"witherstormmod".equals(id.getNamespace()) || !"wither_storm".equals(id.getPath())) {
            return;
        }
        TRACKED_STORMS.put(event.getEntity().getUUID(),
                new TrackedStorm(level, event.getEntity().position()));
    }

    /** 看门狗：不管凋暴是「正常死亡」还是被 /kill、被移除，消失后都补一只加强版 corpse。 */
    @SubscribeEvent
    public static void onStormWatchdog(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED_STORMS.isEmpty()) {
            return;
        }
        java.util.List<java.util.UUID> gone = new java.util.ArrayList<>();
        for (java.util.Map.Entry<java.util.UUID, TrackedStorm> entry : TRACKED_STORMS.entrySet()) {
            TrackedStorm tracked = entry.getValue();
            Entity storm = tracked.level().getEntity(entry.getKey());
            if (storm == null || !storm.isAlive()) {
                gone.add(entry.getKey());
                if (HANDLED_STORMS.add(entry.getKey())) {
                    synchronized (PENDING_CORPSES) {
                        PENDING_CORPSES.add(new PendingCorpse(tracked.level(), tracked.pos(), 20));
                    }
                } else {
                    continue;
                }
                WitherChallenge.LOGGER.info("[凋亡回响] 凋暴已消失，1 秒后在 ({}, {}, {}) 补一只加强版时空间隙",
                        (int) tracked.pos().x, (int) tracked.pos().y, (int) tracked.pos().z);
            } else {
                TRACKED_STORMS.put(entry.getKey(), new TrackedStorm(tracked.level(), storm.position()));
            }
        }
        for (java.util.UUID id : gone) {
            TRACKED_STORMS.remove(id);
        }
    }

    @SubscribeEvent
    public static void onPendingTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_CORPSES.isEmpty()) {
            return;
        }
        synchronized (PENDING_CORPSES) {
            java.util.Iterator<PendingCorpse> it = PENDING_CORPSES.iterator();
            java.util.List<PendingCorpse> fire = new java.util.ArrayList<>();
            while (it.hasNext()) {
                PendingCorpse p = it.next();
                if (p.ticksLeft() <= 0) {
                    it.remove();
                    fire.add(p);
                } else {
                    it.remove();
                    PENDING_CORPSES.add(new PendingCorpse(p.level(), p.pos(), p.ticksLeft() - 1));
                    break;
                }
            }
            for (PendingCorpse p : fire) {
                spawnCorpse(p);
            }
        }
    }

    private static void spawnCorpse(PendingCorpse p) {
        EntityType<?> type = EntityType.byString("withering_away_reborn:the_corpse_of_the_wither").orElse(null);
        if (type == null) {
            return;
        }
        Entity corpse = type.create(p.level());
        if (!(corpse instanceof LivingEntity living)) {
            return;
        }
        living.moveTo(p.pos().x, p.pos().y, p.pos().z, p.level().getRandom().nextFloat() * 360.0F, 0.0F);
        p.level().addFreshEntity(living);
        enhance(living, p.level());
    }

    /** 给玩家挂上「凋零 II 1 小时 + 凋零腐败」（腐败的压制计时交给 SicknessControl）。 */
    public static void infectPlayer(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * 60 * 60, 1));
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            SicknessControl.infect(serverPlayer);
        }
    }

    private CorpseRules() {
    }
}
