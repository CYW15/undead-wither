# -*- coding: utf-8 -*-
"""极简 RCON 客户端（Minecraft 协议）+ 服务端冒烟测试驱动。

用法：
    python tools/server_smoke.py            # 启动服务端、等 Done、跑指令、stop
    python tools/server_smoke.py --keep     # 跑完不 stop（留给人手动看）
"""
import argparse
import os
import socket
import struct
import subprocess
import sys
import time

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(TOOLS_DIR)      # AI_document：pack / patched_mods / addon / final_modpack
ROOT = os.path.dirname(PROJECT)           # 工作区根：凋暴 / classcial_mods / server-test / _build
SRV = os.path.join(ROOT, "server-test")
JAVA = os.path.join(ROOT, "_build", "tools", "jdk-17.0.20.1+1", "bin", "java.exe")
ARGS = os.path.join(SRV, "libraries", "net", "minecraftforge", "forge", "1.20.1-47.4.28", "win_args.txt")
RCON_HOST = "127.0.0.1"
RCON_PORT = 25575
RCON_PASS = "wcdev"


class Rcon:
    def __init__(self, host, port, password, timeout=20):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.req_id = 0
        self._send(3, password)
        rid, typ, body = self._recv()
        if rid == -1:
            raise RuntimeError("RCON 认证失败")

    def _send(self, typ, body):
        self.req_id += 1
        payload = struct.pack("<ii", self.req_id, typ) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)
        return self.req_id

    def _recv(self):
        raw = self._recvn(4)
        (length,) = struct.unpack("<i", raw)
        data = self._recvn(length)
        rid, typ = struct.unpack("<ii", data[:8])
        return rid, typ, data[8:-2].decode("utf-8", "replace")

    def _recvn(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise RuntimeError("RCON 连接断开")
            buf += chunk
        return buf

    def cmd(self, command):
        self._send(2, command)
        _, _, body = self._recv()
        return body

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def wait_done(timeout):
    log = os.path.join(SRV, "logs", "latest.log")
    deadline = time.time() + timeout
    start = time.time()
    while time.time() < deadline:
        time.sleep(2)
        if not os.path.exists(log):
            if int(time.time() - start) % 20 < 2:
                print("  [%ds] 等待 latest.log 出现" % int(time.time() - start), flush=True)
            continue
        try:
            with open(log, "rb") as f:
                data = f.read()
        except OSError as e:
            print("  读取日志失败:", e, flush=True)
            continue
        if b"Done (" in data:
            print("  [%ds] 检测到 Done" % int(time.time() - start), flush=True)
            return True
        if b"Failed to start the minecraft server" in data:
            print("  [%ds] 检测到启动失败" % int(time.time() - start), flush=True)
            return False
        if int(time.time() - start) % 30 < 2:
            tail = data.splitlines()[-1][:120] if data else b""
            print("  [%ds] 仍在加载: %s" % (int(time.time() - start), tail.decode("utf-8", "replace").encode("ascii", "backslashreplace").decode("ascii")),
                  flush=True)
    print("  [timeout] 未等到 Done", flush=True)
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=int, default=420)
    args = ap.parse_args()

    log = os.path.join(SRV, "run.out")
    if os.path.exists(log):
        os.remove(log)
    # 上一次运行留下的 latest.log 会让「检测到 Done」立刻假阳性，必须先删
    for stale in ("latest.log",):
        p = os.path.join(SRV, "logs", stale)
        if os.path.exists(p):
            os.remove(p)
    env = dict(os.environ, JAVA_HOME=os.path.dirname(os.path.dirname(JAVA)))
    env.pop("JDK_JAVA_OPTIONS", None)
    with open(log, "wb") as out:
        proc = subprocess.Popen(
            [JAVA, "-Xms2G", "-Xmx6G", "@" + os.path.join(SRV, "user_jvm_args.txt"), "@" + ARGS, "nogui"],
            cwd=SRV, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT, env=env)
        try:
            ok = wait_done(args.timeout)
            print("world loaded:", ok, flush=True)
            if ok:
                time.sleep(3)
                rcon = Rcon(RCON_HOST, RCON_PORT, RCON_PASS)
                commands = [
                    "gamerule sendCommandFeedback true",
                    "gamerule doMobGriefing true",
                    # 0) 附属 mod 的物品 / 实体是否注册成功
                    "summon item 5 70 0 {Item:{id:\"witherchallenge:infection_core\",Count:1b}}",
                    "summon item 6 70 0 {Item:{id:\"witherchallenge:infection_arrow\",Count:1b}}",
                    "summon witherchallenge:infection_arrow 7 70 0",
                    "execute if entity @e[type=witherchallenge:infection_arrow]",
                    # 1) W.A.R. / CWS 实体
                    "summon withering_away_reborn:the_mangled 12 70 0 {NoAI:1b}",
                    "execute if entity @e[type=withering_away_reborn:the_mangled]",
                    "summon witherstormmod:wither_storm 0 70 0",
                    "execute if entity @e[type=witherstormmod:wither_storm]",
                    # 2) 凋灵死亡 → 后处理动画实体 → 时空间隙
                    "summon minecraft:wither 0 80 0",
                    "execute if entity @e[type=minecraft:wither]",
                ]
                for c in commands:
                    print(">> " + c, flush=True)
                    print("<< " + rcon.cmd(c), flush=True)
                    time.sleep(1.5)

                print(">> kill @e[type=minecraft:wither]", flush=True)
                print("<< " + rcon.cmd("kill @e[type=minecraft:wither]"), flush=True)
                for step in range(1, 13):
                    time.sleep(5)
                    cut = rcon.cmd("execute if entity @e[type=withering_away_reborn:wither_post_death_cutscane_entitty]")
                    rift = rcon.cmd("execute if entity @e[type=withering_away_reborn:spawningrift]")
                    corpse = rcon.cmd("execute if entity @e[type=withering_away_reborn:the_corpse_of_the_wither]")
                    print("[+%2ds] 动画=%s | rift=%s | corpse=%s" % (step * 5, cut, rift, corpse), flush=True)
                    if "Test passed" in corpse:
                        break
                print(">> data get entity @e[type=withering_away_reborn:the_corpse_of_the_wither,limit=1]",
                      flush=True)
                print("<< " + rcon.cmd(
                    "data get entity @e[type=withering_away_reborn:the_corpse_of_the_wither,limit=1]"), flush=True)
                print(">> list entities (corpse pos)", flush=True)
                print("<< " + rcon.cmd(
                    "execute as @e[type=withering_away_reborn:the_corpse_of_the_wither] run data get entity @s Pos"),
                      flush=True)
                # 4) 清理
                rcon.cmd("kill @e[type=witherstormmod:wither_storm]")
                rcon.close()
            if not args.keep:
                try:
                    r = Rcon(RCON_HOST, RCON_PORT, RCON_PASS)
                    r.cmd("stop")
                    r.close()
                except Exception as e:
                    print("stop via rcon failed:", e)
                try:
                    proc.wait(timeout=90)
                except subprocess.TimeoutExpired:
                    proc.terminate()
        finally:
            if args.keep and proc.poll() is None:
                print("server left running, pid", proc.pid)
            elif proc.poll() is None:
                proc.terminate()


if __name__ == "__main__":
    main()
