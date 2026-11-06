# -*- coding: utf-8 -*-
"""打包 0.0.5 的三件交付物：mrpack(已由 build_pack 生成) + 起始存档 zip + 服务端包 zip。"""
import os
import shutil
import zipfile

AI = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # AI_document
ROOT = os.path.dirname(AI)                                      # 工作区根
import sys
sys.path.insert(0, os.path.join(AI, "tools"))
import pack_plan as P
OUT = os.path.join(AI, "final_modpack", "version_" + P.OUT_VERSION)
SRV = os.path.join(ROOT, "server-test")


def zip_dir(src_dir, out_zip, arc_prefix=""):
    if os.path.exists(out_zip):
        os.remove(out_zip)
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED, compresslevel=1) as z:
        for dirpath, _, files in os.walk(src_dir):
            for f in files:
                full = os.path.join(dirpath, f)
                rel = os.path.relpath(full, src_dir).replace(os.sep, "/")
                z.write(full, (arc_prefix + "/" + rel) if arc_prefix else rel)
    print("zip:", out_zip, round(os.path.getsize(out_zip) / 1024 / 1024, 1), "MB")


def make_start_save():
    world = os.path.join(SRV, "world")
    if not os.path.isdir(world):
        raise SystemExit("没有 world 目录，先跑 make_startsave2.py")
    stage = os.path.join(ROOT, "_build", "startsave_stage", "凋亡回响-起始存档")
    if os.path.exists(os.path.dirname(stage)):
        shutil.rmtree(os.path.dirname(stage))
    shutil.copytree(world, stage)
    # 起始存档不要服务端自己的 session 锁和日志
    for junk in ("session.lock", "level.dat_old"):
        p = os.path.join(stage, junk)
        if os.path.exists(p):
            os.remove(p)
    zip_dir(os.path.dirname(stage), os.path.join(OUT, "起始存档-凋亡回响.zip"))
    print("start save ready")


SERVER_README = """凋亡回响 · 专用服务端包（完整版，解压即用）
================================

【这是什么】
  一个已经装好 Forge 47.4.28 + 全部 mod 的专用服务端。
  含 libraries（Forge 运行时库），所以**不需要联网下载**，也不用再跑 Forge 安装器。

【怎么启动】
  1. 解压到任意目录，**路径别带中文/空格**（例如 D:\\MC-Server）；
  2. 需要 Java 17（`java -version` 显示 17.x）；Java 21 也能跑；
  3. 双击 `run.bat`（Windows）或 `./run.sh`（Linux/Mac）；
  4. 第一次启动会在同一目录生成 world / logs 等；
  5. 关服：在控制台输入 `stop` 回车（别直接关窗口，会掉档）。

【改内存】
  打开 `user_jvm_args.txt`，把 `-Xmx4G` 改成你要的值（4~8G 常见）。
  改之前先确保机器物理内存够。

【玩家怎么进服】
  - 服务端默认端口 25565；同一局域网：客户端「多人游戏 → 直接连接」，输入
    服务器内网 IP（如 192.168.1.10:25565）；
  - 外网：需要路由器端口映射 25565/TCP，或用联机工具（如 PCL 联机、ZeroTier）。
  - 客户端必须用**同版本 mrpack**（凋亡回响 0.0.20）导入，
    两边 mod 要对得上，否则会被踢（服务端少 4 个纯客户端 mod，属正常）。

【关键设置】
  - `server.properties`：`online-mode=false`（离线模式，国内常用）；
    正版服把它改成 true；`max-players`、`view-distance` 也可调。
  - 想开 OP：控制台输入 `op 玩家名`。
  - 首次进服会自动发挑战指南书。

【常用指令（游戏内 /op 后可用）】
  /witherchallenge status                 看凋暴 / 时空间隙 / 感染状态
  /witherchallenge find wither|storm      定位最近的凋灵 / 凋暴
  /witherchallenge curemode permanent|timed
  /witherchallenge curetime <分钟>

【注意】
  - 包里没有 WorldEdit / 反作弊 / 白名单配置，需要请自行添加；
  - 服务端已关闭 RCON；如要开，改 server.properties 里 enable-rcon=true 并设密码；
  - 别把 world 文件夹和别人的混用；升级整合包时只替换 mods/config，保留 world。
"""


def make_server_zip():
    stage = os.path.join(ROOT, "_build", "server_stage")
    if os.path.exists(stage):
        shutil.rmtree(stage)
    os.makedirs(stage)
    # 运行时：Forge 库（必须，否则 run.bat 直接报找不到 win_args.txt）
    shutil.copytree(os.path.join(SRV, "libraries"), os.path.join(stage, "libraries"))
    # mods / config / 默认配置
    shutil.copytree(os.path.join(SRV, "mods"), os.path.join(stage, "mods"))
    for name in ("config", "defaultconfigs"):
        src = os.path.join(SRV, name)
        if os.path.isdir(src):
            shutil.copytree(src, os.path.join(stage, name))
    # 启动脚本 + jvm 参数（默认 -Xmx4G）
    for name in ("run.bat", "run.sh"):
        p = os.path.join(SRV, name)
        if os.path.exists(p):
            shutil.copy(p, os.path.join(stage, name))
    jvm = os.path.join(stage, "user_jvm_args.txt")
    with open(jvm, "w", encoding="utf-8") as f:
        f.write("-Xms2G\n-Xmx4G\n")
    # 服务端配置：端口改回 25565、关 RCON、改 motd
    props = os.path.join(SRV, "server.properties")
    if os.path.exists(props):
        lines = []
        with open(props, encoding="utf-8") as f:
            for line in f:
                s = line.strip()
                if s.startswith("server-port="):
                    lines.append("server-port=25565")
                elif s.startswith("enable-rcon="):
                    lines.append("enable-rcon=false")
                elif s.startswith("rcon.password="):
                    lines.append("rcon.password=")
                elif s.startswith("motd="):
                    lines.append("motd=凋亡回响（Forge 1.20.1）")
                elif s.startswith("level-name="):
                    lines.append("level-name=world")
                else:
                    lines.append(line.rstrip("\n"))
        with open(os.path.join(stage, "server.properties"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    # 使用许可一并放进服务端包
    lic = os.path.join(AI, "pack", "overrides", "LICENSE-凋亡回响.txt")
    if os.path.exists(lic):
        shutil.copy(lic, os.path.join(stage, "LICENSE-凋亡回响.txt"))
    # eula 预置为同意（用户自己承担）
    with open(os.path.join(stage, "eula.txt"), "w", encoding="utf-8") as f:
        f.write("eula=true\n")
    with open(os.path.join(stage, "怎么启动.txt"), "w", encoding="utf-8") as f:
        f.write(SERVER_README)
    zip_dir(stage, os.path.join(OUT, "服务端-凋亡回响.zip"))
    print("server pack ready")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    make_start_save()
    make_server_zip()
