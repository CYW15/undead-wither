# -*- coding: utf-8 -*-
"""生成「改过的副本」：wwst 的门类战利品表少了开头的引号。

原 jar 一律不动。本脚本读 凋暴/mods 里的原件，把 5 个门类战利品表里的
`half",` 修成 `"half",`，再写成 patched_mods/ 下的新 jar。
"""
import json
import os
import shutil
import zipfile

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(TOOLS_DIR)      # AI_document：pack / patched_mods / addon / final_modpack
ROOT = os.path.dirname(PROJECT)           # 工作区根：凋暴 / classcial_mods / server-test / _build
SRC = os.path.join(ROOT, "凋暴", "mods", "wwst-0.5.5-1.20.1-Forge-bugfix.jar")
OUT_DIR = os.path.join(PROJECT, "patched_mods")
OUT = os.path.join(OUT_DIR, "wwst-0.5.5-1.20.1-Forge-bugfix-wc1.jar")

BROKEN = [
    "data/minecraft/loot_tables/blocks/acacia_door.json",
    "data/minecraft/loot_tables/blocks/birch_door.json",
    "data/minecraft/loot_tables/blocks/dark_oak_door.json",
    "data/minecraft/loot_tables/blocks/jungle_door.json",
    "data/minecraft/loot_tables/blocks/oak_door.json",
]

# wwst 复写的 CWS 配方：nbt 的值是一整行 SNBT，里面的双引号没有转义 -> 整份 JSON 失效
RECIPE = "data/witherstormmod/recipes/resummon_withered_horseman.json"

# wwst 自带的、坏到加载不出来的模型（Blockbench 导出的非法旋转角）：
# 它不在任何 blockstate/item 里被引用，删掉只是消掉客户端 ResourceReload 的报错。
BROKEN_MODELS = [
    "assets/minecraft/models/item/debris.json",
]


def fix_door(text):
    """`half",` -> `"half",`（少了开头引号）。"""
    for prefix in ("\t\t\t\t\t\t\t\t\t\thalf\",", "\t\t\t\t\t\t\t\t\thalf\",", "half\","):
        if prefix in text:
            if prefix.startswith("\t"):
                return text.replace(prefix, prefix.replace("half\",", "\"half\","), 1)
            return text.replace("half\",", "\"half\",", 1)
    return text


def fix_recipe(text):
    """把 "nbt": "<一整行原样 SNBT>" 里的双引号转义，再整体校验。"""
    marker = '"nbt":'
    idx = text.index(marker)
    start = text.index('"', idx + len(marker))  # nbt 值的起始引号
    end = text.rindex('"')                      # nbt 值的结束引号
    payload = text[start + 1:end]
    escaped = payload.replace('"', '\\"')
    return text[:start + 1] + escaped + text[end:]


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    fixed = 0
    dropped = 0
    with zipfile.ZipFile(SRC) as zin, zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            # wwst 把 1.20.1 的全部原版方块战利品表复制了一份，其中几十份是坏的
            # （SetContainerContents 缺 "type"、门类缺引号…）。整段删掉，让原版表生效。
            if item.filename.startswith("data/minecraft/loot_tables/"):
                dropped += 1
                continue
            if item.filename in BROKEN_MODELS:
                dropped += 1
                continue
            data = zin.read(item.filename)
            if item.filename in BROKEN:
                text = data.decode("utf-8")
                text = fix_door(text)
                json.loads(text)  # 修完必须能解析，否则直接报错
                data = text.encode("utf-8")
                fixed += 1
            elif item.filename == RECIPE:
                text = data.decode("utf-8")
                text = fix_recipe(text)
                json.loads(text)
                data = text.encode("utf-8")
                fixed += 1
            zout.writestr(item, data)
    print("patched", fixed, "files, dropped", dropped, "vanilla loot tables ->", OUT)
    # 复检
    with zipfile.ZipFile(OUT) as z:
        bad = 0
        for n in z.namelist():
            if n.startswith("data/minecraft/") and n.endswith(".json"):
                try:
                    json.loads(z.read(n).decode("utf-8"))
                except Exception:
                    bad += 1
        print("remaining broken json in data/minecraft:", bad)


if __name__ == "__main__":
    main()
