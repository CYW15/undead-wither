package com.mogaidiaolin.witherchallenge.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Map;

/**
 * 感染力量之箭命中生物时的转化表（task/转化表.docx）。
 *
 * <p>命中后「移除原生物 + 生成 W.A.R. 最初级生物」；表里没有的生物不转化，
 * 改为获得 1 小时凋零 II。</p>
 */
public final class ConversionTable {

    private static final Map<String, String> MAP = Map.ofEntries(
            Map.entry("minecraft:zombie", "withering_away_reborn:new_withering_zombie"),
            Map.entry("minecraft:creeper", "withering_away_reborn:new_withering_creeper"),
            Map.entry("minecraft:spider", "withering_away_reborn:withering_spider"),
            Map.entry("minecraft:cave_spider", "withering_away_reborn:withering_spider"),
            Map.entry("minecraft:enderman", "withering_away_reborn:withering_enderman"),
            Map.entry("minecraft:cow", "withering_away_reborn:new_withering_cow"),
            Map.entry("minecraft:pig", "withering_away_reborn:withering_pig"),
            Map.entry("minecraft:chicken", "withering_away_reborn:withering_chiken"),
            Map.entry("minecraft:turtle", "withering_away_reborn:withering_turtle"),
            Map.entry("minecraft:villager", "withering_away_reborn:withering_v_illager"),
            Map.entry("minecraft:vindicator", "withering_away_reborn:withering_vindicator"),
            Map.entry("minecraft:pillager", "withering_away_reborn:withering_pillager"),
            Map.entry("minecraft:evoker", "withering_away_reborn:withering_evoker"),
            Map.entry("minecraft:ravager", "withering_away_reborn:withering_ravager"),
            Map.entry("minecraft:phantom", "withering_away_reborn:withering_phantom"),
            Map.entry("minecraft:hoglin", "withering_away_reborn:witheringhoglin"),
            Map.entry("minecraft:piglin", "withering_away_reborn:withering_piglin"),
            Map.entry("minecraft:iron_golem", "withering_away_reborn:withering_golem"),
            Map.entry("minecraft:blaze", "withering_away_reborn:wither_blaze"),
            Map.entry("minecraft:drowned", "withering_away_reborn:new_withering_zombie"),
            Map.entry("minecraft:husk", "withering_away_reborn:new_withering_zombie"),
            Map.entry("minecraft:skeleton", "minecraft:wither_skeleton"),
            Map.entry("minecraft:stray", "minecraft:wither_skeleton")
    );

    /** 明确不生效的目标：凋灵、末影龙、监守者、凋暴本体/命令方块、所有 W.A.R. 生物、玩家（另行处理）。 */
    public static boolean isExcluded(Entity entity) {
        ResourceLocation id = EntityType.getKey(entity.getType());
        if (id == null) {
            return false;
        }
        if ("withering_away_reborn".equals(id.getNamespace())) {
            return true;
        }
        if ("witherstormmod".equals(id.getNamespace())) {
            return true;
        }
        return switch (id.getPath()) {
            case "wither", "ender_dragon", "warden", "elder_guardian", "wither_storm" -> true;
            default -> false;
        };
    }

    public static boolean canConvert(Entity entity) {
        ResourceLocation id = EntityType.getKey(entity.getType());
        return id != null && MAP.containsKey(id.toString());
    }

    /**
     * 尝试把目标转化成 W.A.R. 生物。
     *
     * @return 是否转化成功
     */
    public static boolean convert(LivingEntity target, ServerLevel level) {
        ResourceLocation currentId = EntityType.getKey(target.getType());
        if (currentId == null) {
            return false;
        }
        String warId = MAP.get(currentId.toString());
        if (warId == null) {
            return false;
        }
        EntityType<?> type = EntityType.byString(warId).orElse(null);
        if (type == null) {
            return false;
        }
        Entity spawned = type.create(level);
        if (!(spawned instanceof Mob mob)) {
            return false;
        }
        mob.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        // 保留自定义名（需求 Q31：名字保留、血量回满）
        if (target.hasCustomName()) {
            mob.setCustomName(target.getCustomName());
        }
        mob.getPersistentData().putBoolean("wc_converted", true);
        if (mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(mob);
        }
        target.discard();
        return true;
    }

    private ConversionTable() {
    }
}
