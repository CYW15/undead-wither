# -*- coding: utf-8 -*-
"""自动二分：找出让专用服务端在加载阶段崩溃的 mod。

判定「崩溃」= 进程在 timeout 内退出，且日志里出现
  Attempted to load class ... for invalid dist DEDICATED_SERVER
  + Exception in thread "main"
判定「通过」= 跑满 timeout 仍未退出（说明已过加载阶段），或日志出现 "Done ("。
"""
import os
import subprocess
import sys
import time

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(TOOLS_DIR)      # AI_document：pack / patched_mods / addon / final_modpack
ROOT = os.path.dirname(PROJECT)           # 工作区根：凋暴 / classcial_mods / server-test / _build
SRV = os.path.join(ROOT, "server-test")
MODS = os.path.join(SRV, "mods")
OFF = os.path.join(SRV, "mods-off")
JAVA = os.path.join(ROOT, "_build", "tools", "jdk-17.0.20.1+1", "bin", "java.exe")
ARGS = os.path.join(SRV, "libraries", "net", "minecraftforge", "forge", "1.20.1-47.4.28", "win_args.txt")

CORE = [
    "[Cracker的凋灵风暴] witherstormmod-1.20.1-4.2.1-all.jar",
    "W.A.R._B1.4.5_1.20.1_gecolib_needed.jar",
    "witherreincarnated-1.20.1-1.0.5.jar",
    "geckolib-forge-1.20.1-4.8.4.jar",
    "wwst-0.5.5-1.20.1-Forge-bugfix.jar",
    "kubejs-forge-2001.6.5-build.16.jar",
    "[犀牛] rhino-forge-2001.2.3-build.10.jar",
    "architectury-9.2.14-forge.jar",
    "cloth-config-11.1.136-forge.jar",
    "PuzzlesLib-v8.1.33-1.20.1-Forge.jar",
    "Bookshelf-Forge-1.20.1-20.2.13.jar",
    "Placebo-1.20.1-8.6.3.jar",
    "[冰山] Iceberg-1.20.1-forge-1.1.25.jar",
    "Prism-1.20.1-forge-1.0.5.jar",
    "[通用附魔] UniversalEnchants-v8.0.0-1.20.1-Forge.jar",
    "[JEI物品管理器] jei-1.20.1-forge-15.20.0.129.jar",
    "[玉 🔍] Jade-1.20.1-Forge-11.13.2.jar",
    "[属性修复] AttributeFix-Forge-1.20.1-21.0.4.jar",
    "CommandToolExpansion2.1.jar",
]

SUSPECTS = [
    "BadOptimizations-2.4.1-1.20.1.jar",
    "CWSM V-Sides 1.20.1 1.1.0.jar",
    "edf-remastered-4.3.jar",
    "embeddium-0.3.31+mc1.20.1.jar",
    "fast-ip-ping-mc1.20.1-forge-v1.0.0.jar",
    "fullbright-1.0.jar",
    "ImmediatelyFast-1.2.4+1.20.1.jar",
    "modelfix-1.15.jar",
    "oculus-mc1.20.1-1.8.0.jar",
    "sodiumoptionsapi-forge-1.0.10-1.20.1.jar",
    "torohealth-1.20.1-forge-1.jar",
    "[3D 皮肤层] skinlayers3d-forge-1.10.1-mc1.20.1.jar",
    "[AI改进] AI-Improvements-1.20-0.5.2.jar",
    "[万用皮肤补丁] CustomSkinLoader_ForgeV2-14.27.jar",
    "[内存泄漏修复] memoryleakfix-forge-1.17+-1.1.5.jar",
    "[动态 FPS] dynamic-fps-3.9.5+minecraft-1.20.0-forge.jar",
    "[实体模型特性] entity_model_features_1.20.1-forge-3.0.10.jar",
    "[实体渲染机制优化] entityculling-forge-1.9.5-mc1.20.1.jar",
    "[实体纹理特性] entity_texture_features_1.20.1-forge-7.0.8.jar",
    "[工作台性能优化] FastWorkbench-1.20.1-8.0.4.jar",
    "[更好的Boss血条] enhanced_boss_bars-1.20.1-1.0.0.jar",
    "[更美观的血条] colorfulhearts-forge-1.20.1-4.3.16.jar",
    "[熔炉性能优化] FastFurnace-1.20.1-8.0.2.jar",
    "[现代化修复] modernfix-forge-5.25.2+mc1.20.1.jar",
    "[移除重载界面] rrls-4.0.6.1+mc1.20.1-forge.jar",
    "[简单的语音聊天] voicechat-forge-1.20.1-2.6.10.jar",
    "[苹果皮] appleskin-forge-mc1.20.1-2.5.1.jar",
    "[铁氧体磁芯] ferritecore-6.0.1-forge.jar",
    "[附魔描述] EnchantmentDescriptions-Forge-1.20.1-17.1.20.jar",
    "[鼠标手势] MouseTweaks-forge-mc1.20.1-2.25.1.jar",
    "[Xaero的世界地图] XaerosWorldMap_1.40.2_Forge_1.20.jar",
    "[Xaero的小地图] Xaeros_Minimap_25.3.2_Forge_1.20.jar",
]


def all_jars():
    jars = set()
    for d in (MODS, OFF):
        if os.path.isdir(d):
            jars |= {f for f in os.listdir(d) if f.endswith(".jar")}
    return sorted(jars)


def set_enabled(enabled):
    os.makedirs(MODS, exist_ok=True)
    os.makedirs(OFF, exist_ok=True)
    for f in os.listdir(OFF):
        if f.endswith(".jar"):
            os.replace(os.path.join(OFF, f), os.path.join(MODS, f))
    for f in list(os.listdir(MODS)):
        if f.endswith(".jar") and f not in enabled:
            os.replace(os.path.join(MODS, f), os.path.join(OFF, f))


def run(timeout):
    log = os.path.join(SRV, "bisect.log")
    if os.path.exists(log):
        os.remove(log)
    env = dict(os.environ, JAVA_HOME=os.path.dirname(os.path.dirname(JAVA)))
    with open(log, "wb") as out:
        proc = subprocess.Popen(
            [JAVA, "-Xms1G", "-Xmx4G", "@" + os.path.join(SRV, "user_jvm_args.txt"), "@" + ARGS, "nogui"],
            cwd=SRV, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT, env=env)
        deadline = time.time() + timeout
        while time.time() < deadline:
            time.sleep(1.5)
            if proc.poll() is not None:
                break
            try:
                if b"Done (" in open(log, "rb").read():
                    break
            except OSError:
                pass
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except Exception:
                proc.kill()
    data = open(log, "rb").read().decode("utf-8", "replace")
    crashed = ('Exception in thread "main"' in data)
    done = "Done (" in data
    sig = "invalid dist DEDICATED_SERVER" in data
    return ("crash" if (crashed and sig) else ("done" if done else "ok")), data


def test(candidate, timeout=40):
    set_enabled(set(CORE) | set(candidate))
    res, data = run(timeout)
    print("  test %2d mods -> %s" % (len(candidate), res), flush=True)
    return res, data


def main():
    timeout = int(sys.argv[1]) if len(sys.argv) > 1 else 40
    # 0) 基线：核心+全部嫌疑
    res, data = test(SUSPECTS, timeout)
    if res != "crash":
        print("全量不崩（可能是竞态）：", res)
        return
    lo = list(SUSPECTS)
    print("=== 二分 ===")
    while len(lo) > 1:
        half = lo[: len(lo) // 2]
        res, data = test(half, timeout)
        if res == "crash":
            lo = half
        else:
            res2, _ = test(lo[len(lo) // 2:], timeout)
            if res2 == "crash":
                lo = lo[len(lo) // 2:]
            else:
                print("两半都不崩 -> 需要同时存在多个 mod：", lo)
                return
    print("CRASH MOD:", lo[0])


if __name__ == "__main__":
    main()
