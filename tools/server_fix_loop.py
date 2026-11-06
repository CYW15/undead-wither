# -*- coding: utf-8 -*-
"""反复启动专用服务端，自动把「只在客户端存在、在服务端崩溃」的 mod 剔出去。

每轮：启动 -> 等 Done 或崩溃 -> 从日志解析 `Failed to create mod instance. ModID: xxx`
-> 找到对应 jar 移到 mods-off/ -> 下一轮。最后打印需要写进 pack_plan.SERVER_EXCLUDE 的列表。
"""
import json
import os
import re
import shutil
import subprocess
import time

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(TOOLS_DIR)      # AI_document：pack / patched_mods / addon / final_modpack
ROOT = os.path.dirname(PROJECT)           # 工作区根：凋暴 / classcial_mods / server-test / _build
SRV = os.path.join(ROOT, "server-test")
MODS = os.path.join(SRV, "mods")
OFF = os.path.join(SRV, "mods-off")
JAVA = os.path.join(ROOT, "_build", "tools", "jdk-17.0.20.1+1", "bin", "java.exe")
ARGS = os.path.join(SRV, "libraries", "net", "minecraftforge", "forge", "1.20.1-47.4.28", "win_args.txt")
INV = os.path.join(ROOT, "_build", "jar_inventory2.json")


def modid_map():
    """modid -> jar 文件名。优先用 jar 自己声明的第一个 modid，避免 embeddium/oculus 这类
    「一个 jar 声明多个 modid」把别的 jar 认成目标。"""
    m = {}
    with open(INV, encoding="utf-8") as f:
        entries = json.load(f)
    for e in entries:
        ids = e.get("modids", [])
        if ids:
            m.setdefault(ids[0], e["file"])
    for e in entries:
        for mid in e.get("modids", []):
            m.setdefault(mid, e["file"])
    return m


def run(timeout):
    log = os.path.join(SRV, "fixloop.log")
    if os.path.exists(log):
        os.remove(log)
    env = dict(os.environ, JAVA_HOME=os.path.dirname(os.path.dirname(JAVA)))
    env.pop("JDK_JAVA_OPTIONS", None)
    with open(log, "wb") as out:
        proc = subprocess.Popen([JAVA, "-Xms1G", "-Xmx4G", "@" + os.path.join(SRV, "user_jvm_args.txt"),
                                 "@" + ARGS, "nogui"], cwd=SRV, stdin=subprocess.DEVNULL,
                                stdout=out, stderr=subprocess.STDOUT, env=env)
        deadline = time.time() + timeout
        status = "timeout"
        while time.time() < deadline:
            time.sleep(2)
            if proc.poll() is not None:
                status = "exited"
                break
            try:
                data = open(log, "rb").read()
            except OSError:
                continue
            if b"Done (" in data:
                status = "done"
                break
            if b"Failed to create mod instance" in data and b"Exception in thread" in data:
                status = "crashed"
                break
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except Exception:
                proc.kill()
    data = open(log, "rb").read().decode("utf-8", "replace")
    return status, data


def main():
    os.makedirs(OFF, exist_ok=True)
    idmap = modid_map()
    excluded = []
    for i in range(15):
        status, data = run(150)
        print("round %d: %s (mods=%d)" % (i + 1, status, len(os.listdir(MODS))), flush=True)
        if status == "done":
            print("SERVER OK")
            break
        ids = re.findall(r"Failed to create mod instance\. ModID: ([\w\-\.]+)", data)
        ids += re.findall(r"Skipping mod \S+ \(([\w\-\.]+)\)", data)
        if not ids:
            print("无法从日志定位失败 mod，最后 40 行：")
            print("\n".join(data.splitlines()[-40:]))
            break
        progressed = False
        for mid in dict.fromkeys(ids):
            jar = idmap.get(mid)
            if not jar or not os.path.exists(os.path.join(MODS, jar)):
                print("  ! 找不到 jar：", mid, jar, flush=True)
                continue
            shutil.move(os.path.join(MODS, jar), os.path.join(OFF, jar))
            excluded.append((mid, jar))
            print("  - 剔除 %s (%s)" % (mid, jar), flush=True)
            progressed = True
        if not progressed:
            print("没有可剔除的，停止")
            break
    print("\nSERVER_EXCLUDE 建议：")
    for mid, jar in excluded:
        print('    "%s",  # %s' % (jar, mid))


if __name__ == "__main__":
    main()
