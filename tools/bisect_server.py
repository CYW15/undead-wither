# -*- coding: utf-8 -*-
"""二分定位造成专用服务端崩溃的 mod。

把 mods/ 里不在「启用列表」中的 jar 临时移到 mods-off/，跑一次服务端，
看是否到 "Done ("。用完自动恢复。

用法：
    python tools/bisect_server.py --only "a.jar,b.jar"     # 只启用这些
    python tools/bisect_server.py --all                    # 全量跑一次
"""
import argparse
import os
import shutil
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


def all_mods():
    return sorted(f for f in os.listdir(MODS) if f.endswith(".jar"))


def set_enabled(enabled):
    os.makedirs(OFF, exist_ok=True)
    # 先把 mods-off 的全部移回
    for f in os.listdir(OFF):
        if f.endswith(".jar"):
            shutil.move(os.path.join(OFF, f), os.path.join(MODS, f))
    disabled = []
    for f in all_mods():
        if f not in enabled:
            shutil.move(os.path.join(MODS, f), os.path.join(OFF, f))
            disabled.append(f)
    return disabled


def run(timeout=180):
    log = os.path.join(SRV, "bisect.log")
    if os.path.exists(log):
        os.remove(log)
    env = dict(os.environ, JAVA_HOME=os.path.dirname(os.path.dirname(JAVA)))
    with open(log, "wb") as out:
        proc = subprocess.Popen(
            [JAVA, "-Xms1G", "-Xmx4G", "@" + os.path.join(SRV, "user_jvm_args.txt"), "@" + ARGS, "nogui"],
            cwd=SRV, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT, env=env)
        deadline = time.time() + timeout
        result = "timeout"
        while time.time() < deadline:
            time.sleep(2)
            if proc.poll() is not None:
                result = "exited:%d" % proc.returncode
                break
            try:
                with open(log, "rb") as f:
                    data = f.read()
            except OSError:
                continue
            if b"Done (" in data:
                result = "done"
                break
        if proc.poll() is None:
            try:
                proc.terminate()
                proc.wait(timeout=20)
            except Exception:
                proc.kill()
    return result, log


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default=None)
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--timeout", type=int, default=180)
    args = ap.parse_args()

    mods = all_mods() + [f for f in os.listdir(OFF) if f.endswith(".jar")] if os.path.isdir(OFF) else all_mods()
    enabled = set(mods) if args.all else set(x.strip() for x in (args.only or "").split(",") if x.strip())
    if not args.all and not enabled:
        sys.exit("需要 --only 或 --all")

    missing = [m for m in enabled if not os.path.exists(os.path.join(MODS, m)) and
               not os.path.exists(os.path.join(OFF, m))]
    if missing:
        sys.exit("未知 jar: %s" % missing)
    disabled = set_enabled(enabled)
    print("enabled=%d disabled=%d" % (len(enabled), len(disabled)))
    try:
        result, log = run(args.timeout)
    finally:
        set_enabled(set(all_mods()) | set(f for f in os.listdir(OFF) if f.endswith(".jar")))
    print("RESULT:", result)
    print("LOG:", log)
    with open(log, "rb") as f:
        data = f.read().decode("utf-8", "replace")
    for line in data.splitlines():
        if any(k in line for k in ("Exception in thread", "Caused by", "Attempted to load class",
                                   "Done (", "Failed to start", "Crash", "ERROR] [ne.mi.fm")):
            print("  ", line[:200])


if __name__ == "__main__":
    main()
