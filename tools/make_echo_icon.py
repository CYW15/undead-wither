# -*- coding: utf-8 -*-
"""生成最终版「凋零残响」图标（方案 A：加粗的回声环，18×18 RGBA）。"""
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # AI_document
ICON_DIR = os.path.join(ROOT, "_build", "icons")
OUT = os.path.join(ROOT, "addon", "src", "main", "resources", "assets",
                   "witherchallenge", "textures", "mob_effect", "wither_echo.png")
S = 18


def build():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx = cy = 8
    # 外环（最暗、最细）
    d.ellipse([cx - 8, cy - 8, cx + 8, cy + 8], outline=(58, 24, 92, 170), width=1)
    # 中环（2px）
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], outline=(104, 46, 158, 235), width=2)
    # 内环（2px，最亮）
    d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], outline=(176, 110, 226, 255), width=2)
    # 中心亮点
    d.rectangle([cx, cy, cx, cy], fill=(255, 255, 255, 255))
    d.rectangle([cx - 1, cy, cx + 1, cy], fill=(236, 210, 255, 255))
    d.rectangle([cx, cy - 1, cx, cy + 1], fill=(236, 210, 255, 255))
    return im


def preview(im, path):
    scale = 8
    pad = 10
    # 左：1×1 实际大小；中：8×；右：深色 HUD 背景上的 1× 与 2×
    w = 40 + S * scale + pad + 40
    h = max(S * scale, 46)
    canvas = Image.new("RGBA", (w, h), (22, 18, 28, 255))
    canvas.alpha_composite(im, (16, (h - S) // 2))
    canvas.alpha_composite(im.resize((S * scale, S * scale), Image.NEAREST), (40, (h - S * scale) // 2))
    small = im.resize((S * 2, S * 2), Image.NEAREST)
    canvas.alpha_composite(small, (w - 40, (h - S * 2) // 2))
    canvas.save(path)


if __name__ == "__main__":
    icon = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    os.makedirs(ICON_DIR, exist_ok=True)
    icon.save(OUT)
    icon.save(os.path.join(ICON_DIR, "A_echo_rings_final.png"))
    preview(icon, os.path.join(ICON_DIR, "preview_final.png"))
    print("icon ->", OUT, os.path.getsize(OUT), "bytes")
