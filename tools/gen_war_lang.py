# -*- coding: utf-8 -*-
"""生成 W.A.R.（withering_away_reborn）的 zh_cn 语言覆盖。

只翻译「玩家看得见」的实体 / 物品 / 方块 / 效果名；内部辅助生物保持英文原名，
避免瞎编名字。生成的文件放进附属 mod 的 assets/withering_away_reborn/lang/zh_cn.json，
资源包按 mod 合并时会覆盖 W.A.R. 自带的英文键。
"""
import io
import json
import os
import zipfile

AI_DOC = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(AI_DOC)   # 工作区根
JAR = os.path.join(ROOT, "classcial_mods", "W.A.R._B1.4.5_1.20.1_gecolib_needed.jar")
OUT = os.path.join(AI_DOC, "addon", "src", "main", "resources",
                   "assets", "withering_away_reborn", "lang", "zh_cn.json")

# 英文显示名 -> 中文
NAMES = {
    # 核心生物
    "The Mangled": "扭曲残骸",
    "The Corpse Of The Wither": "时空间隙",
    "The Blob": "凋灵质团",
    "The Multitude": "群聚体",
    "The Hunter": "猎手",
    "The Attaker": "袭击者",
    "The Spreeder": "扩散体",
    "The Crawler": "爬行者",
    "The Spore": "孢子",
    "The Lingering": "滞留者",
    "The Prowler": "潜行者",
    "The Spawner": "孵化体",
    "The Deeplerker": "深潜者",
    "The Hive 1": "巢母",
    "The Aftermath": "余烬",
    "The Wither Patrol": "凋灵巡逻队",
    "The Wither Raid Entitty": "凋灵袭击者",
    "Wither Traveler": "凋灵旅者",
    "Wither Decayer": "凋灵衰败者",
    "Wither Flameshooter": "凋灵喷火者",
    "Wither Ballerina 1": "凋灵舞者",
    "Wither Ballerina 2": "凋灵舞者",
    "Wither Ballerina 3": "凋灵舞者",
    "Wither Ballerina 4": "凋灵舞者",
    "Wither Ballerina 5": "凋灵舞者",
    "Wither Post Death Cutscane Entitty": "凋暴死亡过场实体",
    "Spawning Rift": "时空裂隙",
    "The Dimensional Rift": "维度裂隙",
    "Wither Curing Entity": "凋灵治愈体",
    "Living Golem Arm": "活体傀儡手臂",
    "Golem Arm": "傀儡手臂",
    # 感染生物
    "Withering Zombie": "凋灵僵尸",
    "Withering Zombie (old)": "凋灵僵尸（旧）",
    "New Withering Zombie": "凋灵僵尸",
    "Mutating Withering Zombie": "变异凋灵僵尸",
    "Drowend Mutating Withering Zombie": "溺亡变异凋灵僵尸",
    "Failed Mutating Withering Zombie": "失败的变异凋灵僵尸",
    "Withering Creeper": "凋灵苦力怕",
    "Withering Creeper (old)": "凋灵苦力怕（旧）",
    "New Withering Creeper": "凋灵苦力怕",
    "Agoning Creeper": "痛苦苦力怕",
    "Withering Spider": "凋灵蜘蛛",
    "Withering Enderman": "凋灵末影人",
    "Withering Cow": "凋灵牛",
    "New Withering Cow": "凋灵牛",
    "Withering Pig": "凋灵猪",
    "Withering Chiken": "凋灵鸡",
    "Withering Turtle": "凋灵海龟",
    "Withering Villager": "凋灵村民",
    "Withering VIllager": "凋灵村民",
    "Withering Vindicator": "凋灵卫道士",
    "Withering Pillager": "凋灵掠夺者",
    "Withering Evoker": "凋灵唤魔者",
    "Withering Ravager": "凋灵劫掠兽",
    "Withering Phantom": "凋灵幻翼",
    "Withering Hoglin": "凋灵疣猪兽",
    "Withering Piglin": "凋灵猪灵",
    "Withering Golem": "凋灵傀儡",
    "Destroyed Withering Golem": "损毁的凋灵傀儡",
    "Wither Blaze": "凋灵烈焰人",
    "Agoning Zombie": "痛苦僵尸",
    "Agoning Bomber": "痛苦轰炸者",
    "Agoning Carrier": "痛苦运载者",
    "Agoning Walker 1": "痛苦行者",
    "Agoning Walker 2": "痛苦行者",
    "Agoning Remnent 1": "痛苦残骸",
    "Agoning Remnent 2": "痛苦残骸",
    "Burrower Stage 1": "掘地虫（一阶）",
    "Burrower Stage 2": "掘地虫（二阶）",
    "Vurrower Stage 3": "掘地虫（三阶）",
    "Hunting Ground Entity": "猎场",
    # 物品
    "Wither Knife": "凋灵刀",
    "Wither Waraxe": "凋灵战斧",
    "Wither Bone": "凋灵骨",
    "Wither Bone Shard": "凋灵骨碎片",
    "Wither Mass Cheker": "凋灵质量检测器",
    "Wither State Checker": "凋灵状态检测器",
    "Show Wither Mobs": "显示凋灵生物",
    "Rage Mode On Off": "狂暴模式开关",
    "Potion Of Plague": "瘟疫药水",
    "Withering Void Bucket": "凋灵虚空桶",
    "Spawner Spawning Mobs Item": "刷怪笼生成物",
    # 方块
    "Withering Mass": "凋灵质",
    "Withering Mass (old)": "凋灵质（旧）",
    "Withered Ash": "凋灵灰",
    "Small Wither Stem": "凋灵茎",
    "Wither Stem": "凋灵茎干",
    "Hanging Wither Plant": "凋灵藤",
    "Tall Wither Plant": "凋灵灌木",
    "Growing Wither Bone Block": "生长中的凋灵骨块",
    "Wither Bone Block": "凋灵骨块",
    "Wither Bone Block (y angle)": "凋灵骨块",
    "Ancient Wither Block": "远古凋灵块",
    "Charged Wither Block": "充能凋灵块",
    "Wither Block": "凋灵块",
    "Soul Spikes": "灵魂尖刺",
    "Iron Immunity Core": "铁之免疫核心",
    "Broken Command Block": "破损的命令方块",
    "Wither Roof Block": "凋灵顶盖",
    "Withering Void": "凋灵虚空",
}


def translate_spawn_egg(name: str) -> str:
    if name.endswith(" Spawn Egg"):
        base = name[: -len(" Spawn Egg")]
        zh = NAMES.get(base)
        if zh:
            return zh + "刷怪蛋"
    return ""


def main():
    with zipfile.ZipFile(JAR) as z:
        src = json.loads(z.read("assets/withering_away_reborn/lang/en_us.json").decode("utf-8"))

    out = {}
    hits = 0
    for key, value in src.items():
        if not isinstance(value, str):
            continue
        zh = NAMES.get(value)
        if zh is None:
            zh = translate_spawn_egg(value)
        if zh:
            out[key] = zh
            hits += 1

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with io.open(OUT, "w", encoding="utf-8", newline="") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("translated", hits, "of", len(src), "->", OUT)


if __name__ == "__main__":
    main()
