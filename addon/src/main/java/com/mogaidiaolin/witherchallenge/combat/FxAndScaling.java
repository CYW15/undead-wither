package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WCConfig;
import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 音效与 Boss 血量缩放。
 *
 * <p>音效遵循需求 S5：**不新增任何音频文件**，全部用原版音效改音调。</p>
 * <ul>
 *   <li>合成核心 / 箭 → 铁砧敲击（低音）</li>
 *   <li>铁砧升级终结武器 → 铁砧使用（高音）</li>
 *   <li>激活终结书 → 信标激活</li>
 *   <li>凋暴召唤 → 雷声</li>
 *   <li>重击时空间隙 → 击杀音效（低沉）</li>
 *   <li>通关 → 成就提示音</li>
 * </ul>
 *
 * <p>Boss 血量缩放（Q101）：按人数 ×(1+0.5(n-1))，上限由配置决定（默认 3 倍），
 * 只作用于凋暴与凋灵共生体，且只在新实体加入时算一次。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class FxAndScaling {

    private static final ResourceLocation STORM = new ResourceLocation("witherstormmod", "wither_storm");
    private static final ResourceLocation SYMBIONT = new ResourceLocation("witherstormmod", "withered_symbiont");
    private static final String BASE_HEALTH_TAG = "wc_base_health";

    // ------------------------------------------------------------ 音效

    public static void playCraftSound(Player player) {
        if (player != null && !player.level().isClientSide) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE,
                    SoundSource.PLAYERS, 0.6F, 1.6F);
        }
    }

    public static void playUpgradeSound(Player player) {
        if (player != null && !player.level().isClientSide) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    public static void playHeavyHit(Level level, double x, double y, double z) {
        if (!level.isClientSide) {
            level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    public static void playChallengeComplete(ServerLevel level) {
        for (Player player : level.getServer().getPlayerList().getPlayers()) {
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) {
            return;
        }
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(crafted.getItem());
        if (id == null || !WitherChallenge.MODID.equals(id.getNamespace())) {
            return;
        }
        if ("wither_end_knife".equals(id.getPath()) || "wither_end_war_axe".equals(id.getPath())) {
            playUpgradeSound(event.getEntity());
        } else {
            playCraftSound(event.getEntity());
        }
    }

    // ------------------------------------------------------------ Boss 血量缩放

    @SubscribeEvent
    public static void onBossJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation id = EntityType.getKey(event.getEntity().getType());
        if (!STORM.equals(id) && !SYMBIONT.equals(id)) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living) || living.getAttribute(Attributes.MAX_HEALTH) == null) {
            return;
        }
        int players = Math.max(1, level.getServer().getPlayerList().getPlayerCount());
        if (players <= 1) {
            return;
        }
        double cap = WCConfig.BOSS_HEALTH_MULTIPLIER.get();
        double multiplier = Math.min(cap, 1.0D + 0.5D * (players - 1));
        if (multiplier <= 1.0D) {
            return;
        }
        var data = living.getPersistentData();
        if (!data.contains(BASE_HEALTH_TAG)) {
            data.putDouble(BASE_HEALTH_TAG, living.getMaxHealth());
        }
        double base = data.getDouble(BASE_HEALTH_TAG);
        living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(base * multiplier);
        living.setHealth((float) Math.min(living.getMaxHealth(), base * multiplier));
        WitherChallenge.LOGGER.info("[凋亡回响] {} 血量 ×{}（在线 {} 人）",
                id.getPath(), String.format("%.2f", multiplier), players);
    }

    private FxAndScaling() {
    }
}
