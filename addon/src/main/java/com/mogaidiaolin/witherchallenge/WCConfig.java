package com.mogaidiaolin.witherchallenge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 配置项（需求 S6 / R8）。
 *
 * <p>注意：corpse 免伤 与 凋零腐败 按用户要求**不做开关**，所以这里没有这两项。</p>
 */
public final class WCConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue PVP_WEAPON_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue PETS_CAN_BE_INFECTED;
    public static final ForgeConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue SUMMON_TAG_CAP;
    public static final ForgeConfigSpec.IntValue SUMMON_TOTAL_CAP;
    public static final ForgeConfigSpec.BooleanValue BEAM_INFECTION;
    public static final ForgeConfigSpec.BooleanValue ENHANCED_CORPSE_SPREAD;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("凋亡回响 —— 可调项").push("witherchallenge");

        PVP_WEAPON_EFFECTS = b.comment("终结武器的特殊效果是否对玩家（PVP）生效，默认 false")
                .define("pvp_weapon_effects", false);
        PETS_CAN_BE_INFECTED = b.comment("队友的宠物是否会被感染之箭转化，默认 true")
                .define("pets_can_be_infected", true);
        BOSS_HEALTH_MULTIPLIER = b.comment("多人时 Boss 血量倍率上限（按 1+0.5(n-1)，最多 3 倍）")
                .defineInRange("boss_health_multiplier", 3.0D, 1.0D, 10.0D);
        SUMMON_TAG_CAP = b.comment("凋暴召唤的、带 wither_storm_spawned 标签的生物上限")
                .defineInRange("summon_tag_cap", 60, 1, 1000);
        SUMMON_TOTAL_CAP = b.comment("全服 W.A.R. 生物总数上限")
                .defineInRange("summon_total_cap", 120, 1, 2000);
        BEAM_INFECTION = b.comment("凋暴牵引光束是否会感染方块")
                .define("beam_infection", true);
        ENHANCED_CORPSE_SPREAD = b.comment("加强版时空间隙是否会加速感染周围方块")
                .define("enhanced_corpse_spread", true);

        b.pop();
        SPEC = b.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "witherchallenge-common.toml");
    }

    private WCConfig() {
    }
}
