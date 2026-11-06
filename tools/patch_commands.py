# -*- coding: utf-8 -*-
"""给 /witherchallenge 增加 sickness 与 advancements 两个子命令。"""
import io
import os

AI = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
P = os.path.join(AI, "addon", "src", "main", "java", "com", "mogaidiaolin", "witherchallenge",
                 "command", "DebugCommands.java")

t = open(P, encoding="utf-8").read()
if "sickness" in t:
    print("already patched")
    raise SystemExit(0)

t = t.replace(
    '                .then(Commands.literal("entities").executes(DebugCommands::entities)));',
    '                .then(Commands.literal("sickness")\n'
    '                        .then(Commands.argument("minutes",\n'
    '                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 600))\n'
    '                                .executes(DebugCommands::sickness)))\n'
    '                .then(Commands.literal("advancements").executes(DebugCommands::advancements))\n'
    '                .then(Commands.literal("entities").executes(DebugCommands::entities)));')

METHODS = '''
    /** /witherchallenge sickness <分钟>：设置金苹果炖菜压制凋零腐败的时长。 */
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
'''

anchor = "    private static int recipes(CommandContext<CommandSourceStack> ctx) {"
t = t.replace(anchor, METHODS + "\n" + anchor, 1)
open(P, "w", encoding="utf-8", newline="").write(t)
print("commands patched")
