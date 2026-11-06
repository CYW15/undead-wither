# -*- coding: utf-8 -*-
"""按 tools/pack_plan.py 生成 mrpack 源目录与 .mrpack，并准备服务端 mods 目录。

用法（工作区根目录）：
    python tools/build_pack.py            # 生成 pack/ + dist/*.mrpack + server-test/mods
    python tools/build_pack.py --no-zip   # 只刷新 pack/ 与 server-test/mods
"""
import json
import os
import shutil
import sys
import zipfile

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(TOOLS_DIR)      # AI_document：pack / patched_mods / addon / final_modpack
ROOT = os.path.dirname(PROJECT)           # 工作区根：凋暴 / classcial_mods / server-test / _build
sys.path.insert(0, os.path.join(ROOT, "tools"))
import pack_plan as P  # noqa: E402

INVENTORY = os.path.join(ROOT, "_build", "jar_inventory2.json")
PACK_DIR = os.path.join(PROJECT, "pack")
OVR_MODS = os.path.join(PACK_DIR, "overrides", "mods")
OVR_CONFIG = os.path.join(PACK_DIR, "overrides", "config")
DIST = os.path.join(PROJECT, "final_modpack", "version_" + P.OUT_VERSION)
SERVER_MODS = os.path.join(ROOT, "server-test", "mods")


LICENSE_TEXT = """《凋亡回响》整合包 · 使用许可
版权所有 (C) 2026 DeepSeek Harness、cyw12

【允许】
1. 免费下载、游玩、分享给朋友和群友（请保持文件完整）。
2. 原样转载本整合包（必须保留本文件与指南书末尾的鸣谢页）。
3. 开服、直播、录制视频（请注明整合包名称《凋亡回响》）。

【禁止】
1. 任何形式的售卖：付费下载、付费墙、淘宝/闲鱼出售、作为会员或赞助的赠品等。
2. 去除或篡改署名、鸣谢页、版本标识后重新发布。
3. 声称本整合包或其附属 mod（witherchallenge）为他人原创。
4. 单独再分发本包内被修改过的第三方 jar 文件。

【第三方模组】
包内所有模组的版权归各自作者所有，完整名单见《鸣谢-模组作者名单》
以及游戏内挑战指南书末尾的鸣谢页。

如发现违规售卖，请保留截图证据并联系整合包作者；原作者也可依其自身许可维权。
"""


def load_inventory():
    with open(INVENTORY, encoding="utf-8") as f:
        return json.load(f)


def fresh_dir(path):
    if os.path.isdir(path):
        shutil.rmtree(path)
    os.makedirs(path, exist_ok=True)


def build():
    inv = load_inventory()
    by_file = {}
    for e in inv:
        by_file.setdefault(e["file"], e)

    deleted = {(s, f) for s, f, _ in P.DELETE}
    files_entries = []
    override_jars = []          # (src_path, dest_filename)
    server_jars = []            # (src_path, dest_filename)
    report = []

    # ---- 替换件先落盘
    rep_local = {}
    for old, info in P.REPLACE.items():
        dest = os.path.join(ROOT, "_build", "dl", "mods", info["filename"])
        if not os.path.exists(dest):
            raise SystemExit("缺少替换件，请先运行 tools/fetch_replacements.py：" + dest)
        rep_local[old] = dest

    # ---- 遍历凋暴/mods + classcial_mods 的 55 个 jar，按清单落地
    for e in inv:
        name = e["file"]
        src_label = P.SRC_DIEFENG if e["src"] == "凋暴" else P.SRC_CLASSIC
        src_dir = os.path.join(ROOT, src_label)
        src_path = os.path.join(src_dir, name)
        if (src_label, name) in deleted:
            continue
        if not os.path.exists(src_path):
            raise SystemExit("源 jar 不存在：" + src_path)

        # 替换件：换成本地已下载的 1.20.1 构建
        if name in rep_local:
            info = P.REPLACE[name]
            dest_name = info["filename"]
            files_entries.append({
                "path": "mods/" + dest_name,
                "hashes": {"sha1": info["sha1"], "sha512": info["sha512"]},
                "downloads": [info["url"]],
                "fileSize": info["size"],
            })
            # 替换件来自 Modrinth，走 files 下载；不能再放 overrides，否则同一 modid 两份必崩
            server_jars.append((rep_local[name], dest_name))
            report.append(("替换", dest_name, "1.20.1 构建"))
            continue

        # 改过的副本（原 jar 不动）
        if name in P.PATCHED:
            info = P.PATCHED[name]
            p = os.path.join(PROJECT, info["dir"], info["filename"])
            if not os.path.exists(p):
                raise SystemExit("缺少修改副本，请先运行 tools/patch_wwst.py：" + p)
            override_jars.append((p, info["filename"]))
            server_jars.append((p, info["filename"]))
            report.append(("修改副本", info["filename"], info["note"]))
            continue

        mr = e.get("modrinth")
        if name in P.FORCE_OVERRIDES or not mr:
            override_jars.append((src_path, name))
            server_jars.append((src_path, name))
            report.append(("overrides", name,
                           P.FORCE_OVERRIDES.get(name, "Modrinth 无此哈希")))
            continue

        cand = [f for f in mr["files"] if f["filename"] == name] or mr["files"]
        f = [x for x in cand if x.get("primary")] or cand
        f = f[0]
        url = f["url"] if f["url"].startswith("http") else "https://cdn.modrinth.com/" + f["url"]
        files_entries.append({
            "path": "mods/" + name,
            "hashes": {"sha1": f["sha1"], "sha512": f["sha512"]},
            "downloads": [url],
            "fileSize": f["size"],
        })
        server_jars.append((src_path, name))
        report.append(("files", name, "Modrinth " + str(mr.get("version_number"))))

    # ---- 附属 mod
    addon_jar = os.path.join(PROJECT, "addon", "build", "libs")
    addon_name = None
    if os.path.isdir(addon_jar):
        cands = [x for x in os.listdir(addon_jar) if x.endswith(".jar")]
        if cands:
            addon_name = cands[0]
            override_jars.append((os.path.join(addon_jar, addon_name), addon_name))
            server_jars.append((os.path.join(addon_jar, addon_name), addon_name))
            report.append(("附属mod", addon_name, "自建"))

    # ---- 写 pack/
    os.makedirs(PACK_DIR, exist_ok=True)
    fresh_dir(OVR_MODS)
    for src, dest in override_jars:
        shutil.copy2(src, os.path.join(OVR_MODS, dest))

    # config：拷凋暴包 config，剔除残留
    if os.path.isdir(OVR_CONFIG):
        shutil.rmtree(OVR_CONFIG)
    src_cfg = os.path.join(ROOT, "凋暴", "config")
    if os.path.isdir(src_cfg):
        shutil.copytree(src_cfg, OVR_CONFIG)
    # 使用许可：随整合包一起分发（实例根目录）
    with open(os.path.join(PACK_DIR, "overrides", "LICENSE-凋亡回响.txt"), "w", encoding="utf-8") as f:
        f.write(LICENSE_TEXT)
        for residue in P.CONFIG_RESIDUE:
            target = os.path.join(OVR_CONFIG, residue)
            if os.path.isdir(target):
                shutil.rmtree(target)
            elif os.path.isfile(target):
                os.remove(target)

    index = {
        "game": "minecraft",
        "formatVersion": 1,
        "versionId": P.OUT_VERSION,
        "name": P.PACK_NAME,
        "summary": "凋亡回响挑战：强化凋灵 → 感染核心 → 凋暴 → 终结武器 → 两只时空间隙",
        "dependencies": {"minecraft": P.MC_VERSION, "forge": P.FORGE_VERSION},
        "files": sorted(files_entries, key=lambda x: x["path"]),
    }
    with open(os.path.join(PACK_DIR, "modrinth.index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)

    # ---- 服务端 mods（剔除客户端专用 / 服务端会崩的）
    fresh_dir(SERVER_MODS)
    for src, dest in server_jars:
        if dest in P.SERVER_EXCLUDE:
            continue
        shutil.copy2(src, os.path.join(SERVER_MODS, dest))

    # ---- 报告
    report.sort()
    lines = ["处理\t文件\t说明"]
    for r in report:
        lines.append("\t".join(r))
    lines.append("")
    lines.append(f"files 条目：{len(files_entries)}，overrides/mods：{len(os.listdir(OVR_MODS))}，"
                 f"服务端 mods：{len(os.listdir(SERVER_MODS))}")
    with open(os.path.join(ROOT, "_build", "pack_build_report.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("\n".join(lines[-1:]))
    return index


def zip_pack():
    os.makedirs(DIST, exist_ok=True)
    out = os.path.join(DIST, f"{P.PACK_NAME}-{P.OUT_VERSION}.mrpack")
    if os.path.exists(out):
        os.remove(out)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(os.path.join(PACK_DIR, "modrinth.index.json"), "modrinth.index.json")
        base = os.path.join(PACK_DIR, "overrides")
        for dirpath, _, filenames in os.walk(base):
            for fn in filenames:
                full = os.path.join(dirpath, fn)
                rel = os.path.relpath(full, PACK_DIR).replace(os.sep, "/")
                z.write(full, rel)
    print("mrpack:", out, os.path.getsize(out), "bytes")
    return out


if __name__ == "__main__":
    build()
    if "--no-zip" not in sys.argv:
        zip_pack()
