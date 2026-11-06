# -*- coding: utf-8 -*-
"""从各 mod 的 mods.toml 读出「名称 + 作者」，生成一份独立的鸣谢 txt。"""
import io
import os
import re
import zipfile

ROOT = r"D:\zhenghebaoxiangmu\mogaidiaolin"
SRV = os.path.join(ROOT, "server-test", "mods")
DLF = os.path.join(ROOT, "凋暴", "mods")
OUT = os.path.join(ROOT, "鸣谢-模组作者名单.txt")

CLIENT_ONLY = {
    "[实体纹理特性] entity_texture_features_1.20.1-forge-7.0.8.jar",
    "fullbright-1.0.jar",
    "torohealth-1.20.1-forge-1.jar",
    "oculus-mc1.20.1-1.8.0.jar",
}

CATEGORY = {
    "witherstormmod": "核心玩法",
    "cwsm_soundtrack__vsides": "核心玩法",
    "command_tool_expansion": "核心玩法",
    "withering_away_reborn": "核心玩法",
    "witherreincarnated": "核心玩法",
    "wither_storm_tweaks": "核心玩法",
    "mr_edf_remastered": "核心玩法",
    "geckolib": "前置库",
    "kubejs": "前置库",
    "rhino": "前置库",
    "architectury": "前置库",
    "cloth_config": "前置库",
    "bookshelf": "前置库",
    "placebo": "前置库",
    "puzzleslib": "前置库",
    "iceberg": "前置库",
    "prism": "前置库",
    "modernfix": "性能优化",
    "embeddium": "性能优化",
    "ferritecore": "性能优化",
    "memoryleakfix": "性能优化",
    "entityculling": "性能优化",
    "badoptimizations": "性能优化",
    "dynamic_fps": "性能优化",
    "rrls": "性能优化",
    "aiimprovements": "性能优化",
    "fastipping": "性能优化",
    "jei": "界面与信息",
    "jade": "界面与信息",
    "appleskin": "界面与信息",
    "mousetweaks": "界面与信息",
    "colorfulhearts": "界面与信息",
    "enhanced_boss_bars": "界面与信息",
    "modelfix": "界面与信息",
    "sodiumoptionsapi": "界面与信息",
    "torohealth": "界面与信息",
    "fullbright": "界面与信息",
    "xaeroworldmap": "界面与信息",
    "xaerominimap": "界面与信息",
    "entity_model_features": "视觉与外观",
    "entity_texture_features": "视觉与外观",
    "oculus": "视觉与外观",
    "customskinloader": "视觉与外观",
    "attributefix": "修正与兼容",
    "fastbench": "修正与兼容",
    "fastfurnace": "修正与兼容",
    "universalenchants": "修正与兼容",
    "enchdesc": "修正与兼容",
    "voicechat": "实用工具",
}

# mods.toml 里的 displayName 有几个重复/含糊的，这里手工修正成可读名称
NAME_FIX = {
    "witherstormmod": "Cracker's Wither Storm Mod（凋暴本体）",
    "cwsm_soundtrack__vsides": "CWSM V-Sides（凋暴官方音乐集）",
    "xaeroworldmap": "Xaero's World Map（世界地图）",
    "xaerominimap": "Xaero's Minimap（小地图）",
    "wither_storm_tweaks": "Wither Storm Tweaks",
}

ORDER = ["核心玩法", "前置库", "性能优化", "界面与信息", "视觉与外观", "修正与兼容", "实用工具", "其他"]


def read_meta(path):
    try:
        z = zipfile.ZipFile(path)
    except Exception:
        return None
    toml = None
    for n in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
        if n in z.namelist():
            toml = z.read(n).decode("utf-8", "replace")
            break
    if not toml:
        return None
    name = re.search(r'displayName\s*=\s*["\']([^"\']+)', toml)
    authors = re.search(r'authors\s*=\s*["\']([^"\']+)', toml)
    modid = re.search(r'modId\s*=\s*["\']([^"\']+)', toml)
    logo = re.search(r'logoFile\s*=\s*["\']([^"\']+)', toml)
    return {
        "name": (name.group(1) if name else os.path.basename(path)),
        "authors": (authors.group(1) if authors else "").strip(),
        "modid": (modid.group(1) if modid else "").strip(),
    }


def collect():
    items = []
    for base in (SRV, DLF):
        if not os.path.isdir(base):
            continue
        for f in sorted(os.listdir(base)):
            if not f.endswith(".jar"):
                continue
            if base == DLF and f not in CLIENT_ONLY:
                continue
            meta = read_meta(os.path.join(base, f))
            if not meta or not meta["modid"] or meta["modid"] == "witherchallenge":
                continue
            meta["file"] = f
            if meta["modid"] in NAME_FIX:
                meta["name"] = NAME_FIX[meta["modid"]]
            items.append(meta)
    seen = set()
    uniq = []
    for it in items:
        key = it["modid"]
        if key in seen:
            continue
        seen.add(key)
        uniq.append(it)
    return uniq


def main():
    items = collect()
    lines = []
    lines.append("《凋亡回响》整合包 · 鸣谢名单")
    lines.append("=" * 46)
    lines.append("")
    lines.append("【本整合包署名（按贡献排序）】")
    lines.append("  1. DeepSeek Harness（AI 编程助手）")
    lines.append("     附属 mod witherchallenge、全部配置、文档、打包")
    lines.append("  2. cyw12")
    lines.append("     需求设计、玩法设想、测试与验收")
    lines.append("")
    lines.append("【第三方模组作者】")
    lines.append("  本整合包内所有模组的版权归各自作者所有。")
    lines.append("  名称 / 作者，按功能分类：")
    lines.append("")
    groups = {}
    for it in items:
        cat = CATEGORY.get(it["modid"], "其他")
        groups.setdefault(cat, []).append(it)
    for cat in ORDER:
        if cat not in groups:
            continue
        lines.append(f"— {cat} —")
        for it in sorted(groups[cat], key=lambda x: x["name"].lower()):
            authors = it["authors"] if it["authors"] else "作者未在 jar 元数据中注明"
            lines.append(f"  · {it['name']} — {authors}")
        lines.append("")
    lines.append("-" * 46)
    lines.append(f"共 {len(items)} 个第三方模组。")
    lines.append("")
    lines.append("如果你喜欢其中某个模组，请去它的发布页点赞、反馈或支持作者——")
    lines.append("这是对免费分享作品的模组作者最好的回报。")
    lines.append("")
    lines.append("数据来源：各 mod jar 内 META-INF/mods.toml 的 displayName / authors 字段。")
    lines.append("生成版本：凋亡回响 0.0.26")
    io.open(OUT, "w", encoding="utf-8", newline="\r\n").write("\n".join(lines))
    print("written:", OUT)
    print("mods:", len(items))
    print("\n".join(lines[:18]))


if __name__ == "__main__":
    main()
