# -*- coding: utf-8 -*-
"""生成「去掉内嵌 TRender/TRansition 的 entityculling 副本」。

原因（2026-11-07 崩溃报告）：
`entityculling` 与 `skinlayers3d` **都**内嵌了 TRender + TRansition（两个 tr7zw 库）。
Forge 并行构造 mod 时，两边都会调用
`TRender → TRansition.ConfigScreenManager.registerConfigScreen`，
而那个 List 不是线程安全的 —— 并发 add 会把 ArrayList 内部状态写坏，
抛 `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0`，
整个 mod 加载失败（0.0.6 换成 1.11.3 后依然如此，说明不是版本问题）。

所以：把 entityculling 里的内嵌 GUI 库去掉，让**只有 skinlayers3d 一个调用方**，
竞态消失；entityculling 的剔除功能不依赖它们（只影响它的配置界面）。
"""
import os
import zipfile

AI = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(AI)
SRC = os.path.join(ROOT, "_build", "dl", "mods", "entityculling-forge-1.11.3-mc1.20.1.jar")
OUT_DIR = os.path.join(AI, "patched_mods")
OUT = os.path.join(OUT_DIR, "entityculling-forge-1.11.3-mc1.20.1-wc1.jar")


def main():
    if not os.path.exists(SRC):
        raise SystemExit("缺少源 jar：" + SRC)
    os.makedirs(OUT_DIR, exist_ok=True)
    dropped = []
    with zipfile.ZipFile(SRC) as zin, zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            name = item.filename
            if name.startswith("META-INF/jars/") or name.startswith("META-INF/jarjar/"):
                dropped.append(name)
                continue
            zout.writestr(item, zin.read(name))
    print("dropped:", dropped)
    print("written:", OUT, os.path.getsize(OUT), "bytes")
    with zipfile.ZipFile(OUT) as z:
        print("remaining embedded:",
              [n for n in z.namelist() if "META-INF/jars/" in n or "jarjar" in n])


if __name__ == "__main__":
    main()
