# -*- coding: utf-8 -*-
"""《凋亡回响》整合包 —— 最终 mod 清单与打包决策（唯一事实来源）。

依据：task/mod冲突检查报告.docx 第五节「最终 mod 清单」+ 需求确认清单 R3/R4。
本文件只描述「装什么、从哪来」，具体落地由 build_pack.py 执行。
"""

PACK_NAME = "凋亡回响"
PACK_VERSION = "1.0.0"
OUT_VERSION = "0.0.28"   # 交付目录 / mrpack 文件名用这个
MC_VERSION = "1.20.1"
FORGE_VERSION = "47.4.28"

# 凋暴整合包 mods 目录（原始材料，只读）
SRC_DIEFENG = "凋暴/mods"
# classcial_mods 目录（原始材料，只读）
SRC_CLASSIC = "classcial_mods"

# ---------------------------------------------------------------- 删除
# P1 两套 CWS：保留凋暴包的高清构建，删 classcial 的原版构建
# P2 两套 geckolib：保留 4.8.4（W.A.R. 硬依赖），删 4.8.2
# P3 I18nUpdateMod 是 Fabric 构建，Forge 端不加载
# 删除 Pehkui：无任何 mod 依赖、无类引用
DELETE = [
    (SRC_CLASSIC, "witherstormmod-1.20.1-4.2.1-all.jar", "P1 与凋暴包构建重复，保留凋暴包高清构建（2427 个文件哈希一致，仅 2 张贴图不同）"),
    (SRC_DIEFENG, "geckolib-forge-1.20.1-4.8.2.jar", "P2 与 4.8.4 重复"),
    (SRC_DIEFENG, "[自动汉化更新] I18nUpdateMod-3.7.0-all.jar", "P3 只有 fabric.mod.json"),
    (SRC_DIEFENG, "Pehkui-3.8.2+1.20.1-forge.jar", "无 mod 依赖、无类引用的历史残留"),
    # 2026-11-07：skinlayers3d 与 entityculling 都内嵌 TRender/TRansition，
    # Forge 并行构造时两边并发 registerConfigScreen 会写坏 ArrayList 导致启动崩溃。
    # 只保留 entityculling 的内嵌库（单调用方 = 不竞态），删掉 3D 皮肤层。
    (SRC_DIEFENG, "[3D 皮肤层] skinlayers3d-forge-1.10.1-mc1.20.1.jar", "与 entityculling 的 TRender 并发注册冲突，导致启动崩溃"),
    # 2026-11-06 客户端实测崩溃后删除：
    # ImmediatelyFast 1.2.4（1.20.1 最后一个构建）的 IrisCompat 只认旧包名 net.coderbot.iris.*，
    # 而包里是 Oculus 1.8.0（新包名 net.irisshaders.iris.*），ClassNotFoundException 之后
    # 代码里直接 System.exit(-1)（IrisCompat.init 第 46-49 行），游戏必然秒退、且日志里没有异常栈。
    (SRC_DIEFENG, "ImmediatelyFast-Forge-1.5.3+1.20.4.jar", "与 Oculus 1.8.0 不兼容（IF 1.2.4 会 System.exit），且 1.5.3 是 1.20.4 构建，直接去掉"),
]

# ---------------------------------------------------------------- 替换
# P4 两个 1.20.4 构建换成 1.20.1 构建
REPLACE = {
    "[实体渲染机制优化] entityculling-forge-1.9.5-mc1.20.1.jar": {
        "filename": "entityculling-forge-1.11.3-mc1.20.1.jar",
        "url": "https://cdn.modrinth.com/data/NNAgCjsB/versions/HUUJcFnt/entityculling-forge-1.11.3-mc1.20.1.jar",
        "sha1": "d3c513e88ef9f5cdd7e27d17d923d4dca69c6d55",
        "sha512": "be5be53897ae7403a202e4b0b094701bcc4ecdb94afcf2ea144cd07d9a4553a1fc6a88917732a72c477d01c33583162cffb9c4a19d259089deb018878eec1da8",
        "size": 1515076,
    },
    "fast-ip-ping-v1.0.8-mc1.20.4-forge.jar": {
        "filename": "fast-ip-ping-mc1.20.1-forge-v1.0.0.jar",
        "url": "https://cdn.modrinth.com/data/9mtu0sUO/versions/frdZOP14/fast-ip-ping-mc1.20.1-forge-v1.0.0.jar",
        "sha1": "a58614ba847c0f0cd64927e63cebc7d649cf6588",
        "sha512": "c89b58b87753da1ebc8c99d0e1901d948ea7887a1124a9e9596f887292dab51a69b4fd8e8d90abcb463fa50b11cb624858d49645853935b6dd351bf18e42aa8b",
        "size": 10947,
    },
}

# ---------------------------------------------------------------- 改过的副本
# 原 jar 一律不动；改了内容的副本放 patched_mods/，文件名带 -wcN 后缀，并在 CHANGELOG 记录。
PATCHED = {
    "wwst-0.5.5-1.20.1-Forge-bugfix.jar": {
        "dir": "patched_mods",
        "filename": "wwst-0.5.5-1.20.1-Forge-bugfix-wc1.jar",
        "note": "修 5 个门类战利品表缺少开头引号（half\" -> \"half\"），原件不动",
    },
    "[Cracker的凋灵风暴] witherstormmod-1.20.1-4.2.1-all.jar": {
        "dir": "patched_mods",
        "filename": "witherstormmod-1.20.1-4.2.1-all-wc1.jar",
        "note": "9 个 storm_spawn_platform 结构里的 18 个命令方块换成灵魂沙（用户要求）",
    },
}

# ---------------------------------------------------------------- 强制走 overrides/mods
# 不在 Modrinth（CurseForge 独占 / 自制改版）：jar 直接放进 mrpack 的 overrides/mods
FORCE_OVERRIDES = {
    "CWSM V-Sides 1.20.1 1.1.0.jar": "CurseForge 独占的 CWS 官方音乐集",
    "CommandToolExpansion2.1.jar": "CurseForge 独占",
    "edf-remastered-4.3.jar": "低代码数据包式 mod，Modrinth 无对应 jar 哈希",
    "wwst-0.5.5-1.20.1-Forge-bugfix-wc1.jar": "wwst 的修改副本（见 PATCHED）",
    "W.A.R._B1.4.5_1.20.1_gecolib_needed.jar": "CurseForge 独占",
    "[Cracker的凋灵风暴] witherstormmod-1.20.1-4.2.1-all.jar": "凋暴包内的改版构建（5120×5120 贴图），非公开发行版",
}

# ---------------------------------------------------------------- 服务端排除
# 这些 mod 只在客户端生效 / 在专用服务端会崩，打包给客户端保留，服务端包里剔除。
# ETF 7.0.8 实测会让 Forge 专用服务端在加载阶段崩（BrigadierExceptions.<clinit> 触发 Screen）。
SERVER_EXCLUDE = [
    "[实体纹理特性] entity_texture_features_1.20.1-forge-7.0.8.jar",  # 触发 Screen 类加载，直接崩
    "fullbright-1.0.jar",                                             # 构造期加载 KeyMapping
    "torohealth-1.20.1-forge-1.jar",                                  # 构造期加载 Screen
    "oculus-mc1.20.1-1.8.0.jar",                                      # 构造期加载 Screen
]

# ---------------------------------------------------------------- config 清理
# 报告第六节：mod 已删除但配置残留
CONFIG_RESIDUE = [
    "advancementplaques", "alexscaves", "alexscaves_biome_generation", "bestylewither",
    "charmofundying", "citadel", "connector.json", "constructionwand", "curios",
    "dummmmmmy", "emotecraft", "emotecraft_emote_map.json", "enchantinginfuser",
    "ending_library", "explosiveenhancement", "firstperson", "goety", "goety_revelation",
    "highlighter", "itemborders", "mobbattle", "moonlight", "notenoughanimations",
    "obscuria", "replaymod", "revelationfix", "roundabout", "royalvariations",
    "shouldersurfing", "sound_physics_remastered", "transition", "trender", "waveycapes",
    # 删掉的 mod 对应的配置
    "pehkui-client.toml", "pehkui-common.toml", "fabric",
    # AttributeFix 的配置里全是已删 mod 的属性（goety_revelation 等），删掉让它按实际 mod 重建
    "attributefix.json",
]
