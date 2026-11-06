package com.mogaidiaolin.witherchallenge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 调试命令（仅 OP）：/witherchallenge status|recipes|entities
 *
 * <p>需求 S7 定的 6 条命令里，先落地这三条最常用的；其余 spawn_corpse / set_phase /
 * kill_all 等随对应玩法一起加。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class DebugCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("witherchallenge")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(DebugCommands::status))
                .then(Commands.literal("find")
                        .then(Commands.literal("wither").executes(ctx -> find(ctx, true)))
                        .then(Commands.literal("storm").executes(ctx -> find(ctx, false))))
                .then(Commands.literal("recipes").executes(DebugCommands::recipes))
                .then(Commands.literal("tags").executes(DebugCommands::tags))
                .then(Commands.literal("sickness")
                        .then(Commands.argument("minutes",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 600))
                                .executes(DebugCommands::sickness)))
                .then(Commands.literal("curetime")
                        .then(Commands.argument("minutes",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 600))
                                .executes(DebugCommands::sickness)))
                .then(Commands.literal("curemode")
                        .then(Commands.literal("permanent").executes(ctx -> cureMode(ctx, true)))
                        .then(Commands.literal("timed").executes(ctx -> cureMode(ctx, false))))
                .then(Commands.literal("advancements").executes(DebugCommands::advancements))
                .then(Commands.literal("entities").executes(DebugCommands::entities)));
    }

    /** 校验数据标签是否生效（箭能不能被弓弩认、终结武器算不算命令方块工具）。 */
    private static int tags(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.tags.TagKey<net.minecraft.world.item.Item> arrowsTag =
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                        new ResourceLocation("minecraft", "arrows"));
        net.minecraft.tags.TagKey<net.minecraft.world.item.Item> cbTag =
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                        new ResourceLocation("witherstormmod", "command_block_tools"));
        net.minecraft.world.item.ItemStack arrow = new net.minecraft.world.item.ItemStack(
                com.mogaidiaolin.witherchallenge.registry.ModItems.INFECTION_ARROW.get());
        net.minecraft.world.item.ItemStack knife = new net.minecraft.world.item.ItemStack(
                com.mogaidiaolin.witherchallenge.registry.ModItems.WITHER_END_KNIFE.get());
        net.minecraft.world.item.ItemStack axe = new net.minecraft.world.item.ItemStack(
                com.mogaidiaolin.witherchallenge.registry.ModItems.WITHER_END_WAR_AXE.get());
        boolean echo = com.mogaidiaolin.witherchallenge.registry.ModEffects.WITHER_ECHO.isPresent();
        final String msg = String.format(
                "[凋亡回响] 感染箭∈minecraft:arrows=%s｜刀∈command_block_tools=%s｜斧∈command_block_tools=%s｜凋零残响已注册=%s",
                arrow.is(arrowsTag), knife.is(cbTag), axe.is(cbTag), echo);
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int recipes = server.getRecipeManager().getRecipes().size();
        long storms = 0;
        long mangled = 0;
        // 感染是否结束只看「世界上（所有维度）还有没有活着的时空间隙」，不看击败数量
        StringBuilder perDim = new StringBuilder();
        long corpses = 0;
        for (ServerLevel level : server.getAllLevels()) {
            long here = 0;
            for (Entity e : level.getAllEntities()) {
                EntityType<?> type = e.getType();
                ResourceLocation id = EntityType.getKey(type);
                if (id == null) {
                    continue;
                }
                if ("witherstormmod".equals(id.getNamespace()) && "wither_storm".equals(id.getPath())) {
                    storms++;
                } else if ("withering_away_reborn".equals(id.getNamespace())) {
                    if ("the_corpse_of_the_wither".equals(id.getPath()) && e.isAlive()) {
                        corpses++;
                        here++;
                    } else if ("the_mangled".equals(id.getPath())) {
                        mangled++;
                    }
                }
            }
            if (perDim.length() > 0) {
                perDim.append(' ');
            }
            perDim.append(level.dimension().location().getPath()).append('=').append(here);
        }
        com.mogaidiaolin.witherchallenge.world.InfectionControl.State st =
                com.mogaidiaolin.witherchallenge.world.InfectionControl.state(server.overworld());
        final String msg = String.format(
                "[凋亡回响] 配方 %d 条｜凋暴 %d｜the Mangled %d｜活着的时空间隙 %d（%s）｜感染结束=%s%s",
                recipes, storms, mangled, corpses, perDim, st.ended,
                st.challengeCompleted ? "（挑战完成）" : "");
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return recipes;
    }


    /** /witherchallenge find wither|storm：分别定位最近的凋灵 / 凋暴（两个独立功能）。 */
    private static int find(CommandContext<CommandSourceStack> ctx, boolean wither) {
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("[凋亡回响] 这个命令必须由玩家执行"));
            return 0;
        }
        var target = wither
                ? com.mogaidiaolin.witherchallenge.world.WitherPresence.nearest(player,
                        net.minecraft.world.entity.EntityType.WITHER)
                : com.mogaidiaolin.witherchallenge.world.WitherPresence.nearestByType(player,
                        "witherstormmod", "wither_storm");
        final String name = wither ? "凋灵" : "凋暴";
        if (target == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("[凋亡回响] 这个维度里没有" + name), false);
            return 0;
        }
        double dist = Math.sqrt(player.distanceToSqr(target));
        String dir = com.mogaidiaolin.witherchallenge.world.WitherPresence.compass(player, target);
        final String msg = String.format("[凋亡回响] 最近的%s：%d 格，%s（%.0f, %.0f）",
                name, (int) dist, dir, target.getX(), target.getZ());
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    /** /witherchallenge curemode permanent|timed：设置凋零腐败的治愈模式。 */
    private static int cureMode(CommandContext<CommandSourceStack> ctx, boolean permanent) {
        com.mogaidiaolin.witherchallenge.combat.SicknessControl.setPermanent(
                ctx.getSource().getServer().overworld(), permanent);
        final String msg = "[凋亡回响] 凋零腐败治愈模式 = " + (permanent ? "永久" : "限时")
                + (permanent ? "" : "（" + com.mogaidiaolin.witherchallenge.world.InfectionControl
                .state(ctx.getSource().getServer().overworld()).sicknessMinutes + " 分钟后再次感染）");
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return permanent ? 1 : 0;
    }

    /** /witherchallenge sickness|curetime <分钟>：设置限时模式下「凋零残响」的时长。 */
    private static int sickness(CommandContext<CommandSourceStack> ctx) {
        int minutes = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "minutes");
        com.mogaidiaolin.witherchallenge.combat.SicknessControl.setMinutes(
                ctx.getSource().getServer().overworld(), minutes);
        final String msg = "[凋亡回响] 金苹果炖菜压制凋零腐败的时长已设为 " + minutes + " 分钟";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return minutes;
    }

    /** 统计进度数量（排查「原版进度不见了」用）。 */
    private static int advancements(CommandContext<CommandSourceStack> ctx) {
        var manager = ctx.getSource().getServer().getAdvancements();
        java.util.Map<String, Integer> byNs = new java.util.TreeMap<>();
        for (var adv : manager.getAllAdvancements()) {
            byNs.merge(adv.getId().getNamespace(), 1, Integer::sum);
        }
        final String msg = "[凋亡回响] 进度按命名空间：" + byNs;
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return byNs.size();
    }

    private static int recipes(CommandContext<CommandSourceStack> ctx) {
        RecipeManager rm = ctx.getSource().getServer().getRecipeManager();
        List<String> ids = rm.getRecipes().stream()
                .map(r -> r.getId().toString())
                .sorted()
                .collect(Collectors.toList());
        ctx.getSource().sendSuccess(() -> Component.literal("[凋亡回响] 配方总数 " + ids.size()), false);
        String own = ids.stream().filter(s -> s.startsWith("witherchallenge:")).collect(Collectors.joining(", "));
        ctx.getSource().sendSuccess(() -> Component.literal("本 mod 配方: " + (own.isEmpty() ? "（无）" : own)), false);
        String head = ids.stream().limit(8).collect(Collectors.joining(", "));
        ctx.getSource().sendSuccess(() -> Component.literal("前 8 条: " + head), false);
        return ids.size();
    }

    private static int entities(CommandContext<CommandSourceStack> ctx) {
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            int n = 0;
            for (Entity e : level.getAllEntities()) {
                n++;
            }
            int finalN = n;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    level.dimension().location() + " 实体数 " + finalN), false);
        }
        return 1;
    }

    private DebugCommands() {
    }
}
