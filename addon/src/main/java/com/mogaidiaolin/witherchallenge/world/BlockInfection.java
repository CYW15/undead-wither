package com.mogaidiaolin.witherchallenge.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 方块感染（凋暴光束感染对应表）。
 *
 * <p>0.0.3 先给「加强版时空间隙」用，0.0.4 的凋暴光束感染复用同一张表。</p>
 */
public final class BlockInfection {

    private static final ResourceLocation WITHERING_MASS =
            new ResourceLocation("withering_away_reborn", "withering_mass");
    private static final ResourceLocation WITHERED_ASH =
            new ResourceLocation("withering_away_reborn", "withered_ash");
    private static final ResourceLocation SMALL_WITHER_STEM =
            new ResourceLocation("withering_away_reborn", "small_wither_stem");
    private static final ResourceLocation HANGING_WITHER_PLANT =
            new ResourceLocation("withering_away_reborn", "hanging_wither_plant");
    private static final ResourceLocation TALL_WITHER_PLANT =
            new ResourceLocation("withering_away_reborn", "tall_wither_plant");

    private static Block block(ResourceLocation id) {
        Block b = ForgeRegistries.BLOCKS.getValue(id);
        return b == null ? Blocks.AIR : b;
    }

    /** 已经是感染方块就跳过。 */
    public static boolean isInfected(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && "withering_away_reborn".equals(id.getNamespace());
    }

    /**
     * 按表尝试感染一个方块。
     *
     * @return 是否真的替换了方块
     */
    public static boolean tryInfect(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || isInfected(state)) {
            return false;
        }
        Block target = null;
        float chance = 0.0F;
        Block block = state.getBlock();

        if (block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL) {
            target = block(WITHERING_MASS);
            chance = 1.00F;
        } else if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT) {
            target = block(WITHERED_ASH);
            chance = 0.60F;
        } else if (block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.GRAVEL) {
            target = block(WITHERED_ASH);
            chance = 0.40F;
        } else if (state.is(BlockTags.LOGS)) {
            target = block(SMALL_WITHER_STEM);
            chance = 0.50F;
        } else if (state.is(BlockTags.LEAVES)) {
            target = block(HANGING_WITHER_PLANT);
            chance = 0.30F;
        } else if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || block == Blocks.GRASS || block == Blocks.TALL_GRASS || block == Blocks.FERN
                || block == Blocks.LARGE_FERN || block == Blocks.DEAD_BUSH || block == Blocks.SUGAR_CANE
                || block == Blocks.CACTUS) {
            target = block(TALL_WITHER_PLANT);
            chance = 0.30F;
        } else if (state.is(BlockTags.STONE_BRICKS) || block == Blocks.STONE || block == Blocks.DEEPSLATE
                || block == Blocks.ANDESITE || block == Blocks.GRANITE || block == Blocks.DIORITE
                || block == Blocks.TUFF || block == Blocks.CALCITE || block == Blocks.BLACKSTONE
                || block == Blocks.BASALT || block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE) {
            target = block(WITHERING_MASS);
            chance = 0.25F;
        }
        if (target == null || target == Blocks.AIR) {
            return false;
        }
        if (random.nextFloat() > chance) {
            return false;
        }
        level.setBlockAndUpdate(pos, target.defaultBlockState());
        return true;
    }

    /** 在落点周围做一圈采样（光束 / 加强 corpse 用）。 */
    public static int infectAround(ServerLevel level, double x, double y, double z,
                                   double radius, int samples, RandomSource random) {
        int changed = 0;
        for (int i = 0; i < samples; i++) {
            double dx = (random.nextDouble() * 2.0D - 1.0D) * radius;
            double dz = (random.nextDouble() * 2.0D - 1.0D) * radius;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            BlockPos pos = BlockPos.containing(x + dx, y, z + dz);
            if (tryInfect(level, pos, random)) {
                changed++;
            }
            // 顺带往下垫一层，避免只感染空气表面
            if (changed > 0 && random.nextFloat() < 0.25F) {
                tryInfect(level, pos.below(), random);
            }
        }
        return changed;
    }

    private BlockInfection() {
    }
}
