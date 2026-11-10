package com.mogaidiaolin.witherchallenge.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 挑战指南书（成书）：首次进服自动放进物品栏第一格，由 DeepSeek Harness 编写。 */
public final class GuideBook {
    public static final String GIVEN_TAG = "wc_guide_given";
    public static final String TITLE = "凋亡回响 · 挑战指南";
    public static final String AUTHOR = "DeepSeek Harness";

    private static final String[] PAGES = {
        "凋亡回响 · 挑战指南\n作者：DeepSeek Harness　｜　整合：DeepSeek Harness、cyw12　｜　版本：1.0.0　｜　说明书式排版\n游戏内成书正文（共 34 页）\n以下每一块是游戏内成书的一页，按顺序写入 written_book 的 pages。每页都控制在 256 字符以内，可直接复制。",
        "凋亡回响 · 挑战指南\n作者：DeepSeek Harness\n整合：DeepSeek Harness、cyw12\n版本：1.0.0\n\n使用说明：\n· 本指南按挑战流程顺序排列\n· 每页对应游戏内成书的一页\n· 标「注意」的段落是容易卡住的地方\n· 建议边玩边对照，不要一次读完",
        "【挑战目标】\n最终击败两只加强的「时空间隙」。\n\n完整流程：\n1. 击杀凋灵，生成时空间隙\n2. 开启狂暴，刷出 The Mangled\n3. 收集 8 个感染核心，合成感染箭\n4. 射时空间隙，召唤凋暴\n5. 进入凋暴体内，破坏命令方块\n6. 用凋零之星搭 IV 级信标\n7. 激活终结书，铁砧打造武器\n8. 击败两只加强时空间隙",
        "【出发前准备】\n装备：钻石或下界合金一套\n武器：弓、弩各一把，箭至少 2 组\n工具：镐、锹，方块至少 2 组\n移动：末影珍珠 4~8 个，或鞘翅\n补给：食物、治疗药水、牛奶桶\n特殊：金苹果炖菜（压制凋零腐败）\n建议：至少两人，一人拉仇恨一人输出",
        "【步骤 1：击杀凋灵】\n1. 用灵魂沙摆出 T 形\n2. 放上 3 个凋灵骷髅头召唤凋灵\n3. 击败它（原版凋灵；W.A.R. 会在它死后播放演出并生成时空间隙）\n4. 原地出现切场动画\n5. 数秒后生成时空间隙\n注意：刚生成时它处于无敌状态，不要攻击",
        "【时空间隙（未加强）】\n· 不会移动、不会攻击\n· 会把周围方块转化为凋灵化方块\n· 普通武器攻击无效\n· 记下它的坐标，后面要回来\n用途：召唤凋暴的起点，也是最终目标之一",
        "【步骤 2：开启狂暴（rage）】\n以下任一条件达成即可：\n· 累计击杀 50 只带凋灵标签的生物\n· 击杀 3 只 The Blob\n\n开启后全服提示，感染扩散加快，\n凋灵生物属性提升。\n注意：这是刷出 The Mangled 的前置条件",
        "【步骤 3：积累质量（Wither Mass）】\nThe Blob 只有在质量 ≥50 时才会刷怪。\n质量来源：对带有凋零效果的生物造成伤害。\n\n1. 合成凋灵刀\n   材料：凋灵骨碎片、凋灵铁锭、凋灵骨、石英\n2. 用凋灵刀攻击凋灵生物，使其获得凋零\n3. 继续攻击带凋零的目标，质量上升\n4. 质量足够后，Blob 开始刷怪",
        "【步骤 4：获取感染核心】\n目标：The Mangled\n· 被玩家或驯服的狼击杀时，10% 掉落感染核心\n· 抢夺每级 +5%\n· 合成 1 支箭需要 8 个核心\n建议：在 Blob 附近反复刷，配合抢夺附魔",
        "【步骤 5：合成感染力量之箭】\n配方（工作台）：\n感染核心 ×8 围住箭 ×1\n产出：感染力量之箭 ×1\n\n· 箭本身无伤害\n· 命中生物：转化为凋灵生物\n· 命中玩家：凋零 + 凋零腐败\n· 未命中实体：箭退回背包，不消耗",
        "【步骤 6：召唤凋暴】\n1. 手持感染力量之箭\n2. 射击未加强的时空间隙\n3. 时空间隙被加强，凋暴出现\n\n注意：\n· 已加强的时空间隙对箭免疫\n· 凋暴只在主世界追踪，不跨维度\n· 召唤后先远离，它会先锁定玩家",
        "【凋暴：阶段 1–3】\n· 地面爬行，移动较慢\n· 每次开火 8 连发：黑黑黑蓝黑黑黑蓝\n· 血量打空不会死亡，会进化到阶段 4 并回满血\n\n应对：保持距离，绕侧输出，不要站在正面",
        "【凋暴：阶段 4 以后】\n· 长出多个头，飞在空中\n· 头会发射蓝色头颅炸弹\n· 牵引光束会吸取玩家和方块\n\n注意：不要在基地附近战斗，它会破坏沿途方块\n建议：提前在开阔地搭好平台，或利用地形",
        "【凋暴：阶段 7】\n· 凋暴身上会出现入口\n· 跳入入口会被传送到体内维度\n· 体内是独立的 Boss 战\n\n进入前确认：\n· 背包里有命令方块工具或终结武器\n· 带足食物、方块、治疗药水",
        "【体内：命令方块】\n· 命令方块会分阶段战斗、召唤小怪\n· 只有命令方块工具或终结武器能造成伤害\n· 每次有效命中扣除 25% 最大生命\n· 共需 4 次有效命中\n\n注意：它只在特定阶段可被攻击，观察阶段提示",
        "【击杀凋暴之后】\n1. 命令方块被破坏，凋暴进入死亡动画\n2. 死亡时掉落凋零之星\n3. 玩家被传送回主世界凋暴附近\n4. 捡起凋零之星\n\n注意：确认背包有空间，星掉在脚下",
        "【步骤 7：搭建 IV 级凋暴信标】\n1. 用凋零之星合成凋暴信标\n2. 放置信标，在正下方搭建金字塔：\n   第 1 层：3×3 青金石\n   第 2 层：5×5 铁/金/钻石/绿宝石块\n   第 3 层：7×7 同上\n   第 4 层：9×9 同上\n3. 四层齐全即 IV 级，信标自动激活",
        "【步骤 8：激活凋灵终结书】\n合成未激活的书（工作台）：\n钻石 | 感染核心 | 铁锭\n感染核心 | 命令方块书 | 感染核心\n红石粉 | 感染核心 | 绿宝石\n\n激活：\n1. 主手持未激活的书\n2. 右击 IV 级凋暴信标\n3. 书变为激活版\n注意：信标不足 IV 级时不消耗物品",
        "【步骤 9：铁砧打造终结武器】\n凋灵刀 + 激活的书 → 凋灵终结刀\n凋灵战斧 + 激活的书 → 凋灵终结战斧\n\n· 保留原武器附魔\n· 无限耐久\n· 未激活的书不能用于铁砧\n· 两把武器都能对体内命令方块造成伤害",
        "【步骤 10：两只加强时空间隙】\n位置：\n· 第一只：最初召唤凋暴的位置，在地面\n· 第二只：第一只凋暴死亡的位置，通常在空中 y≈75\n注：凋暴无论用箭、信标还是命令召唤，死亡都会生成加强 corpse；\n挑战只认第一只凋暴掉的那只。\n规则：两只都必须由玩家或驯服的狼击杀；空中的用方块/珍珠/鞘翅接近。",
        "【如何伤害时空间隙】\n· 普通武器：无效\n· 终结刀/战斧：仅「重击」有效\n· 重击判定：攻击冷却 ≥90% 时出手\n· 伤害：刀 1 点/次，斧 2 点/次\n· 生命：基础 100，加强 150\n· 免疫一切状态效果\n轻击提示：你需要用更大的力气才能伤到它",
        "【保命与常见问题】\n· 凋零腐败跨维度保留，重生后仍在\n· 金苹果炖菜只能暂时压制\n· 躲进地狱/末地只是暂停，凋暴留在主世界\n· 中箭后尽快喝牛奶或吃炖菜\n· 多人时明确分工，避免误伤",
        "【通关检查表】\n□ 击杀凋灵\n□ 开启狂暴\n□ 获得 8 个感染核心\n□ 合成感染箭\n□ 召唤并击败凋暴\n□ 获得凋零之星\n□ 搭建 IV 级信标\n□ 激活终结书\n□ 打造终结武器\n□ 击败两只加强时空间隙\n—— DeepSeek Harness",
        "【指令（仅 OP 可用）】\n/witherchallenge status\n查看配方数、凋暴/时空间隙数量、感染与挑战状态\n\n/witherchallenge tags\n检查感染箭与终结武器的标签是否生效\n\n/witherchallenge recipes\n列出本 mod 的配方",
        "/witherchallenge entities\n查看各维度实体数量\n\n/witherchallenge sickness <分钟>\n设置金苹果炖菜压制凋零腐败的时长（默认 10 分钟）\n\n注意：感染只看「世界上还有没有时空间隙」——所有时空间隙被击败后感染终止；新的凋灵/凋暴又会重新带来时空间隙。",
        "【鸣谢 · 模组作者】\n· Wither Storm Mod — nonamecrackers2, Nazaru\n· CWSM V-Sides — _TheExiIedFeIIow, Vertigo\n· Command Tool Expansion — Pandaboi, Tainted\n· Withering Away: Reborn — Da Jacob / BadPlay3r",
        "【鸣谢 · 续】\n· Wither Reincarnated — Alexander\n· Wither Storm Tweaks — WitherBean\n· Ender Dragon Fight Remastered — MrGenie151\n· GeckoLib 4 — Gecko, Eliot, AzureDoom, DerToaster, Tslat, Witixin\n· KubeJS — LatvianModder",
        "【鸣谢 · 续】\n· Rhino — latvian.dev, Mozilla\n· Architectury — shedaniel\n· Cloth Config — shedaniel\n· Bookshelf — Darkhax\n· Placebo — Shadows_of_Fire\n· Puzzles Lib — Fuzs\n· Iceberg — Grend\n· Prism — Grend\n· ModernFix — embeddedt",
        "【鸣谢 · 续】\n· Embeddium — embeddedt\n· Ferrite Core — malte0811\n· Memory Leak Fix — FX - PR0CESS\n· EntityCulling — tr7zw\n· BadOptimizations — Thosea\n· Dynamic FPS — juliand665 & LostLuma\n· Remove Reloading Screen — dima_dencep",
        "【鸣谢 · 续】\n· AI-Improvements — BuiltBrokenModding\n· Fast IP Ping — Fallen_Breath\n· AttributeFix — Darkhax\n· Fast Workbench — Shadows_of_Fire\n· FastFurnace — Shadows_of_Fire\n· Universal Enchants — Fuzs",
        "【鸣谢 · 续】\n· EnchantmentDescriptions — Darkhax\n· Just Enough Items — mezz\n· Jade — Snownee\n· AppleSkin — squeek\n· Mouse Tweaks — Ivan Molodetskikh (YaLTeR)\n· Colorful Hearts — Terrails\n· Enhanced Boss Bars — Nakuring",
        "【鸣谢 · 续】\n· Model Gap Fix — MehVahdJukaar\n· Sodium Options API — toni\n· Entity Model Features — Traben\n· Entity Texture Features — Traben\n· Oculus — NanoLive, dima_dencep, coderbot, IMS212, Justsnoopy30, FoundationGames",
        "【鸣谢 · 续】\n· Xaero's World Map — xaero96\n· Xaero's Minimap — xaero96\n· CustomSkinLoader — xfl03, JLChnToZ, ZekerZhayard\n· Simple Voice Chat — Max Henkel\n· ToroHealth — ToroCraft\n· Fullbright — n8M4",
        "【版权说明】\n本整合包由下列两位完成，署名按贡献排序：\n1. DeepSeek Harness（AI 编程助手）—— 附属 mod、全部配置与文档\n2. cyw12 —— 需求设计、玩法设想与测试\n\n整合包内所有第三方模组的版权归各自作者，名单见前几页。\n全部模组的版权归各自作者所有，作者名单见前几页。\n\n如果你喜欢其中某个模组，请去它的发布页点赞/反馈，这是对作者最好的支持。\n\n—— 谢谢每一位免费分享作品的模组作者",
    };

    public static ItemStack create() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", TITLE);
        tag.putString("author", AUTHOR);
        tag.putInt("generation", 0);
        ListTag list = new ListTag();
        for (String page : PAGES) {
            list.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        }
        tag.put("pages", list);
        return book;
    }

    private GuideBook() {
    }
}
