package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mogaidiaolin.witherchallenge.registry.ModItems;

/**
 * 感染核心掉落：the Mangled 被玩家 / 已驯服狼击杀时，
 * 基础 25%，抢夺每级 +5%（需求 Q19 改：原本 10%，用户 2026-11-06 改为 25%）。
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class LootEvents {

    private static final ResourceLocation MANGLED_ID =
            new ResourceLocation("withering_away_reborn", "the_mangled");
    private static final float BASE_CHANCE = 0.25F;
    private static final float PER_LOOTING = 0.05F;

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) {
            return;
        }
        if (!MANGLED_ID.equals(EntityType.getKey(dead.getType()))) {
            return;
        }
        LivingEntity killer = resolveKiller(event.getSource());
        if (killer == null) {
            return;
        }
        int looting = EnchantmentHelper.getMobLooting(killer);
        float chance = BASE_CHANCE + PER_LOOTING * looting;
        if (dead.getRandom().nextFloat() >= chance) {
            return;
        }
        ItemStack drop = new ItemStack(ModItems.INFECTION_CORE.get());
        ItemEntity item = new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5D, dead.getZ(), drop);
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
    }

    /** 只认玩家本人、玩家射出的箭、以及已驯服（有主人）的狼。 */
    private static LivingEntity resolveKiller(net.minecraft.world.damagesource.DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity owner = source.getEntity();
        if (direct instanceof AbstractArrow arrow && arrow.getOwner() instanceof Player player) {
            return player;
        }
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }
        if (owner instanceof Player player) {
            return player;
        }
        if (owner instanceof Wolf wolf && wolf.isTame()) {
            return wolf;
        }
        if (direct instanceof Wolf wolf && wolf.isTame()) {
            return wolf;
        }
        return null;
    }

    private LootEvents() {
    }
}
