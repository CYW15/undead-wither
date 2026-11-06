package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import com.mogaidiaolin.witherchallenge.item.WitherEndWeaponItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 终结武器的战斗规则。
 *
 * <p>时空间隙（W.A.R. 的 the_corpse_of_the_wither）在自己的 hurt() 里写死了
 * 「直接来源是玩家 → return false」，所以正常近战连事件都不会触发。
 * 这里在 {@link AttackEntityEvent} 阶段拦截：取消原版攻击，再用一个
 * 「没有直接实体」的伤害源（generic）把伤害打进去。</p>
 *
 * <p>轻/重击判定：攻击冷却 ≥90% 算重击（需求 Q50）；刀 1 点、斧 2 点。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class WeaponCombatEvents {

    /** 时空间隙。 */
    public static final ResourceLocation CORPSE_ID =
            new ResourceLocation("withering_away_reborn", "the_corpse_of_the_wither");
    /** 凋暴体内命令方块。 */
    public static final ResourceLocation COMMAND_BLOCK_ID =
            new ResourceLocation("witherstormmod", "command_block");

    private static final float HEAVY_ATTACK_THRESHOLD = 0.9F;
    private static final int AXE_DELAY_TICKS = 60;   // 3 秒
    private static final List<DelayedHit> DELAYED = new ArrayList<>();

    private static final class DelayedHit {
        final LivingEntity target;
        final Player attacker;
        int ticksLeft;

        DelayedHit(LivingEntity target, Player attacker, int ticksLeft) {
            this.target = target;
            this.attacker = attacker;
            this.ticksLeft = ticksLeft;
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        if (!isCorpse(target)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        boolean ours = held.getItem() instanceof WitherEndWeaponItem;

        // 不管拿什么打，原版这一刀都取消：corpse 自己就会拒掉玩家伤害
        event.setCanceled(true);
        if (player.level().isClientSide) {
            return;
        }
        if (!ours) {
            player.displayClientMessage(
                    Component.translatable("message.witherchallenge.corpse.immune").withStyle(ChatFormatting.GRAY),
                    true);
            return;
        }
        boolean heavy = player.getAttackStrengthScale(0.5F) >= HEAVY_ATTACK_THRESHOLD;
        if (!heavy) {
            player.displayClientMessage(
                    Component.translatable("message.witherchallenge.corpse.light").withStyle(ChatFormatting.RED),
                    true);
            return;
        }
        float damage = ((WitherEndWeaponItem) held.getItem()).isKnife() ? 1.0F : 2.0F;
        // generic 没有直接实体，绕过 corpse 对「玩家来源」的硬编码免疫
        INSIDE_OUR_HIT = true;
        try {
            target.hurt(target.damageSources().generic(), damage);
        } finally {
            INSIDE_OUR_HIT = false;
        }
        FxAndScaling.playHeavyHit(player.level(), target.getX(), target.getY(), target.getZ());
        ChallengeTracker.recordCorpseHit(target, player);
    }

    /** 标记「这一下是我们终结武器打出去的」，用于放行 corpse 的伤害闸门。 */
    private static boolean INSIDE_OUR_HIT = false;

    /**
     * 时空间隙只吃终结武器的伤害（用户 2026-11-08 要求）。
     *
     * <p>W.A.R. 自带的免疫只挡住了玩家近战 / 箭 / 爆炸 / 凋零之首等少数类型，
     * 凋暴的火焰头颅、其它生物的近战照样能扣血。这里统一加一道闸：
     * 除了我们标记的终结武器伤害，以及 /kill、虚空这类
     * {@code BYPASSES_INVULNERABILITY} 的来源，其余全部取消。</p>
     */
    @SubscribeEvent
    public static void onCorpseAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!isCorpse(event.getEntity()) || INSIDE_OUR_HIT) {
            return;
        }
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        event.setCanceled(true);
    }

    /** 斧：3 秒后扣除目标「当时」当前生命的 75%（魔法伤害，无视护甲，Boss 减半，PVP 默认关闭）。 */
    public static void scheduleDelayed(LivingEntity target, Player attacker) {
        DELAYED.add(new DelayedHit(target, attacker, AXE_DELAY_TICKS));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DELAYED.isEmpty()) {
            return;
        }
        List<DelayedHit> fire = new ArrayList<>();
        Iterator<DelayedHit> it = DELAYED.iterator();
        while (it.hasNext()) {
            DelayedHit hit = it.next();
            if (!hit.target.isAlive() || hit.target.level().isClientSide) {
                it.remove();
                continue;
            }
            if (--hit.ticksLeft <= 0) {
                it.remove();
                fire.add(hit);
            }
        }
        for (DelayedHit hit : fire) {
            applyAxeBurst(hit);
        }
    }

    private static void applyAxeBurst(DelayedHit hit) {
        LivingEntity target = hit.target;
        if (!target.isAlive()) {
            return;
        }
        if (target instanceof Player) {
            return;   // PVP 默认关闭（需求 Q52）
        }
        float amount = target.getHealth() * 0.75F;
        if (isBoss(target)) {
            amount *= 0.5F;
        }
        if (amount <= 0.0F) {
            return;
        }
        target.hurt(target.damageSources().magic(), amount);
    }

    /** 刀：命中非 corpse 目标时 6 秒凋零 + 自己 6 秒再生。 */
    public static void applyKnifeEffects(LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 0), attacker);
        attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0), attacker);
    }

    public static boolean isCorpse(Entity entity) {
        return entity != null && CORPSE_ID.equals(EntityType.getKey(entity.getType()));
    }

    public static boolean isStormCommandBlock(Entity entity) {
        return entity != null && COMMAND_BLOCK_ID.equals(EntityType.getKey(entity.getType()));
    }

    public static boolean isBoss(LivingEntity entity) {
        if (entity instanceof WitherBoss || entity instanceof EnderDragon) {
            return true;
        }
        ResourceLocation id = EntityType.getKey(entity.getType());
        return id != null && "witherstormmod".equals(id.getNamespace())
                && ("wither_storm".equals(id.getPath()) || "withered_symbiont".equals(id.getPath()));
    }

    private WeaponCombatEvents() {
    }
}
