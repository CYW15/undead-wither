# -*- coding: utf-8 -*-
"""生成「改过的 CWS 副本」：把出生点附近「凋暴小屋」里的命令方块换成灵魂沙。

原 jar 不动。做法：把 `data/witherstormmod/structures/*_storm_spawn_platform.nbt`
里 palette 的 `minecraft:command_block` 改成 `minecraft:soul_sand`。

NBT 里字符串自带 2 字节长度前缀、复合标签本身没有长度字段，
所以「改短字符串 + 改自己的长度前缀」是安全的（不会破坏外层结构）。
"""
import gzip
import hashlib
import os
import re
import shutil
import zipfile

AI = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(AI)
SRC = os.path.join(ROOT, "server-test", "mods",
                   "[Cracker的凋灵风暴] witherstormmod-1.20.1-4.2.1-all.jar")
OUT_DIR = os.path.join(AI, "patched_mods")
OUT = os.path.join(OUT_DIR, "witherstormmod-1.20.1-4.2.1-all-wc1.jar")

OLD = b"minecraft:command_block"
NEW = b"minecraft:soul_sand"


def patch_nbt(raw: bytes) -> tuple[bytes, int]:
    count = 0
    out = bytearray()
    i = 0
    while True:
        j = raw.find(OLD, i)
        if j < 0:
            out += raw[i:]
            break
        # 字符串格式：TAG_String 的数据 = 2 字节长度 + UTF-8 内容
        # 前两个字节应当是长度前缀
        if j >= 2:
            length = int.from_bytes(raw[j - 2:j], "big")
        else:
            length = -1
        if length == len(OLD):
            # 去掉旧的长度前缀，写新的
            out += raw[i:j - 2]
            out += len(NEW).to_bytes(2, "big")
            out += NEW
            count += 1
            i = j + len(OLD)
        else:
            out += raw[i:j + len(OLD)]
            i = j + len(OLD)
    return bytes(out), count


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    total = 0
    touched = []
    with zipfile.ZipFile(SRC) as zin, zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename.endswith(".nbt") and "storm_spawn_platform" in item.filename:
                raw = gzip.decompress(data)
                patched, count = patch_nbt(raw)
                if count:
                    data = gzip.compress(patched, mtime=0)
                    total += count
                    touched.append((item.filename, count))
            zout.writestr(item, data)
    print("patched", total, "command blocks in", len(touched), "structures ->", OUT)
    for n, c in touched:
        print("  ", n, c)

    # 复检：重新读回来，确认已经没有 command_block
    with zipfile.ZipFile(OUT) as z:
        left = 0
        for n in z.namelist():
            if n.endswith(".nbt") and "storm_spawn_platform" in n:
                raw = gzip.decompress(z.read(n))
                left += raw.count(OLD)
        print("remaining command_block in platforms:", left)
        print("soul_sand occurrences:", sum(
            gzip.decompress(z.read(n)).count(NEW) for n in z.namelist()
            if n.endswith(".nbt") and "storm_spawn_platform" in n))


if __name__ == "__main__":
    main()
