# -*- coding: utf-8 -*-
"""把「模组作者鸣谢」写进指南书最后（0.0.25）。"""
import io
import os

AI = r"D:\zhenghebaoxiangmu\mogaidiaolin\AI_document"
P = os.path.join(AI, "addon", "src", "main", "java", "com", "mogaidiaolin", "witherchallenge",
                 "item", "GuideBook.java")

MODS = [
    ("Wither Storm Mod", "nonamecrackers2, Nazaru"),
    ("CWSM V-Sides", "_TheExiIedFeIIow, Vertigo"),
    ("Command Tool Expansion", "Pandaboi, Tainted"),
    ("Withering Away: Reborn", "Da Jacob / BadPlay3r"),
    ("Wither Reincarnated", "Alexander"),
    ("Wither Storm Tweaks", "WitherBean"),
    ("Ender Dragon Fight Remastered", "MrGenie151"),
    ("GeckoLib 4", "Gecko, Eliot, AzureDoom, DerToaster, Tslat, Witixin"),
    ("KubeJS", "LatvianModder"),
    ("Rhino", "latvian.dev, Mozilla"),
    ("Architectury", "shedaniel"),
    ("Cloth Config", "shedaniel"),
    ("Bookshelf", "Darkhax"),
    ("Placebo", "Shadows_of_Fire"),
    ("Puzzles Lib", "Fuzs"),
    ("Iceberg", "Grend"),
    ("Prism", "Grend"),
    ("ModernFix", "embeddedt"),
    ("Embeddium", "embeddedt"),
    ("Ferrite Core", "malte0811"),
    ("Memory Leak Fix", "FX - PR0CESS"),
    ("EntityCulling", "tr7zw"),
    ("BadOptimizations", "Thosea"),
    ("Dynamic FPS", "juliand665 & LostLuma"),
    ("Remove Reloading Screen", "dima_dencep"),
    ("AI-Improvements", "BuiltBrokenModding"),
    ("Fast IP Ping", "Fallen_Breath"),
    ("AttributeFix", "Darkhax"),
    ("Fast Workbench", "Shadows_of_Fire"),
    ("FastFurnace", "Shadows_of_Fire"),
    ("Universal Enchants", "Fuzs"),
    ("EnchantmentDescriptions", "Darkhax"),
    ("Just Enough Items", "mezz"),
    ("Jade", "Snownee"),
    ("AppleSkin", "squeek"),
    ("Mouse Tweaks", "Ivan Molodetskikh (YaLTeR)"),
    ("Colorful Hearts", "Terrails"),
    ("Enhanced Boss Bars", "Nakuring"),
    ("Model Gap Fix", "MehVahdJukaar"),
    ("Sodium Options API", "toni"),
    ("Entity Model Features", "Traben"),
    ("Entity Texture Features", "Traben"),
    ("Oculus", "NanoLive, dima_dencep, coderbot, IMS212, Justsnoopy30, FoundationGames"),
    ("Xaero's World Map", "xaero96"),
    ("Xaero's Minimap", "xaero96"),
    ("CustomSkinLoader", "xfl03, JLChnToZ, ZekerZhayard"),
    ("Simple Voice Chat", "Max Henkel"),
    ("ToroHealth", "ToroCraft"),
    ("Fullbright", "n8M4"),
]

LIMIT = 225  # 单页字符上限，留余量给页眉


def build_pages():
    header = "【鸣谢 · 模组作者】\n"
    entries = [f"· {n} — {a}\n" for n, a in MODS]
    pages = []
    cur = header
    for e in entries:
        if len(cur) + len(e) > LIMIT:
            pages.append(cur.rstrip("\n"))
            cur = "【鸣谢 · 续】\n"
        cur += e
    pages.append(cur.rstrip("\n"))
    # 末尾追加一页总结
    pages.append("【版权说明】\n本整合包由 cyw12 整合配置，不修改原作代码。\n"
                 "全部模组的版权归各自作者所有，作者名单见前几页。\n\n"
                 "如果你喜欢其中某个模组，请去它的发布页点赞/反馈，"
                 "这是对作者最好的支持。\n\n—— 谢谢每一位免费分享作品的模组作者")
    return pages


def java_str(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'


def main():
    pages = build_pages()
    t = io.open(P, encoding="utf-8").read()

    if "鸣谢 · 模组作者" in t:
        print("already patched")
        return

    # PAGES 数组以 "    };" 结束，取最后一处（create() 之前）
    idx = t.rindex("    };")
    insert = "".join("        " + java_str(p) + ",\n" for p in pages)
    t = t[:idx] + insert + t[idx:]
    # 更新文件头注释里的页数
    import re
    total = t.count("        \"")
    t = re.sub(r"（共 \d+ 页）", f"（共 {total} 页）", t)
    io.open(P, "w", encoding="utf-8", newline="").write(t)
    print(f"added {len(pages)} credit pages, total pages ~{total}")


if __name__ == "__main__":
    main()
