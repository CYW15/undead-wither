# -*- coding: utf-8 -*-
"""生成「凋零残响」状态效果的候选图标（18×18，Minecraft mob_effect 标准尺寸）。

输出到 _build/icons/ 下，并拼一张 8 倍放大的对比图。
"""
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AI = ROOT   # 本脚本在 AI_document/tools 下
OUT = os.path.join(ROOT, "_build", "icons")
os.makedirs(OUT, exist_ok=True)

S = 18


def blank():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def design_echo():
    """A：同心回声波纹 —— 表示「残留的回响」，和凋零腐败的骷髅脸明显不同。"""
    im = blank()
    d = ImageDraw.Draw(im)
    cx, cy = 8, 8
    rings = [
        (7, (74, 31, 115, 150)),
        (6, (94, 40, 143, 190)),
        (5, (122, 58, 176, 220)),
        (3, (168, 96, 214, 240)),
    ]
    for r, color in rings:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=1)
    # 中心核
    d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=(232, 200, 255, 255))
    d.point((cx, cy), fill=(255, 255, 255, 255))
    return im


def design_faded_skull(src_path):
    """B：原凋零腐败图标褪色/半透明 —— 表示「腐败正在退去，但还没消失」。"""
    src = Image.open(src_path).convert("RGBA").resize((S, S), Image.NEAREST)
    px = src.load()
    im = blank()
    out = im.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            # 去饱和 + 偏冷紫
            gray = int(0.35 * r + 0.45 * g + 0.2 * b)
            r2 = int(gray * 0.85 + 40)
            g2 = int(gray * 0.65 + 20)
            b2 = int(gray * 0.9 + 70)
            # 右下角逐渐淡出
            fade = 1.0 - (x + y) / (2.0 * (S - 1)) * 0.75
            out[x, y] = (min(r2, 255), min(g2, 255), min(b2, 255), int(a * fade * 0.55))
    return im


def design_shard():
    """C：裂开的紫晶碎片 —— 「残响」= 感染留下的碎片。"""
    im = blank()
    d = ImageDraw.Draw(im)
    body = [(8, 1), (14, 6), (12, 15), (5, 16), (2, 8)]
    d.polygon(body, fill=(96, 42, 150, 235))
    highlight = [(8, 2), (12, 6), (8, 9), (5, 7)]
    d.polygon(highlight, fill=(160, 100, 214, 235))
    d.line([(9, 3), (8, 9), (11, 14)], fill=(46, 18, 74, 255), width=1)
    d.line([(5, 8), (8, 9), (7, 15)], fill=(46, 18, 74, 200), width=1)
    d.point((8, 9), fill=(240, 220, 255, 255))
    return im


def preview(images, out_path, scale=8, gap=6):
    w = len(images) * (S * scale + gap) - gap
    h = S * scale
    canvas = Image.new("RGBA", (w, h), (24, 20, 30, 255))
    x = 0
    for im in images:
        big = im.resize((S * scale, S * scale), Image.NEAREST)
        canvas.alpha_composite(big, (x, 0))
        x += S * scale + gap
    canvas.save(out_path)


if __name__ == "__main__":
    src_icon = os.path.join(AI, "addon", "src", "main", "resources", "assets",
                            "witherchallenge", "textures", "mob_effect", "wither_echo.png")
    a = design_echo()
    b = design_faded_skull(src_icon)
    c = design_shard()
    for name, im in (("A_echo_rings", a), ("B_faded_skull", b), ("C_shard", c)):
        im.save(os.path.join(OUT, name + ".png"))
    preview([a, b, c], os.path.join(OUT, "preview.png"))
    print("written", sorted(os.listdir(OUT)))
