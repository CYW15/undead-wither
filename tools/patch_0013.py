# -*- coding: utf-8 -*-
"""0.0.13 的收尾修改：/kill 计入合法击杀、成就 OR 判定、把配置项接回代码。"""
import io
import os

AI = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(AI, "addon", "src", "main", "java", "com", "mogaidiaolin", "witherchallenge")


def patch(path, old, new, tag):
    t = open(path, encoding="utf-8").read()
    if new in t:
        print("skip", tag)
        return
    if old not in t:
        print("MISS", tag)
        return
    t = t.replace(old, new, 1)
    open(path, "w", encoding="utf-8", newline="").write(t)
    print("ok", tag)


# 1) /kill、虚空这类 bypasses_invulnerability 也算合法击杀
patch(os.path.join(JAVA, "combat", "CorpseRules.java"),
'''        boolean legit = hitter != null
                || killer instanceof Player''',
'''        boolean legit = hitter != null
                || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                || killer instanceof Player''',
"legit /kill")

# 2) StormCombat：召唤上限 / 光束开关接配置
patch(os.path.join(JAVA, "combat", "StormCombat.java"),
'''        if (tagged >= TAG_CAP || totalWar >= WAR_CAP) {''',
'''        if (tagged >= com.mogaidiaolin.witherchallenge.WCConfig.SUMMON_TAG_CAP.get()
                || totalWar >= com.mogaidiaolin.witherchallenge.WCConfig.SUMMON_TOTAL_CAP.get()) {''',
"summon caps config")

patch(os.path.join(JAVA, "combat", "StormCombat.java"),
'''            infectFromBeams(level, storm);''',
'''            if (com.mogaidiaolin.witherchallenge.WCConfig.BEAM_INFECTION.get()) {
                infectFromBeams(level, storm);
            }''',
"beam config")

# 3) 加强 corpse 加速感染接配置
patch(os.path.join(JAVA, "combat", "CorpseRules.java"),
'''            BlockInfection.infectAround(level, corpse.getX(), corpse.getY(), corpse.getZ(), 6.0D, 24, random);''',
'''            if (com.mogaidiaolin.witherchallenge.WCConfig.ENHANCED_CORPSE_SPREAD.get()) {
                BlockInfection.infectAround(level, corpse.getX(), corpse.getY(), corpse.getZ(), 6.0D, 24, random);
            }''',
"corpse spread config")

# 4) 成就「终结之刃」改成 刀 或 斧
p = os.path.join(AI, "addon", "src", "main", "resources", "data", "witherchallenge",
                 "advancements", "craft_weapon.json")
t = open(p, encoding="utf-8").read()
t = t.replace('"requirements": [ [ "knife", "axe" ] ]', '"requirements": [ [ "knife" ], [ "axe" ] ]')
if '["knife", "axe"]' in t:
    t = t.replace('"requirements": [["knife", "axe"]]', '"requirements": [["knife"], ["axe"]]')
t = t.replace('"requirements": [\n    [\n      "knife",\n      "axe"\n    ]\n  ]',
              '"requirements": [\n    [\n      "knife"\n    ],\n    [\n      "axe"\n    ]\n  ]')
open(p, "w", encoding="utf-8", newline="").write(t)
print("craft_weapon requirements ->", t.split('"requirements"')[1][:80].replace("\n", " "))
