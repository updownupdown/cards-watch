#!/usr/bin/env python3
"""
Draws every spade the watch uses from solitarius-spade.svg (the retraced
spade, five filled ribbons on a 1024 grid), in place:

- res/drawable: logo_spade (white, tinted where it's drawn), logo_spade_outline
  (ambient), ic_spade_mono (watch faces tint it), ic_launcher_foreground
  (white, inside the icon's safe zone),
- res/drawable-nodpi: complication_logo and complication_spade_mono (256 px),
- card-icons/Back.svg (the card back's logo).

    python3 scripts/logo.py

Needs Pillow for the PNGs.
"""
import math
import re
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main"
SOURCE = ROOT / "solitarius-spade.svg"


def subpaths(d):
    """The path's subpaths as lists of segments ('C', p0, p1, p2, p3) or ('L', p0, p1). Absolute M, C, L, Z only."""
    toks = re.findall(r"[MCLZ]|-?\d*\.?\d+(?:e-?\d+)?", d)
    subs, pos, i = [], None, 0
    num = lambda k: float(toks[k])
    while i < len(toks):
        t = toks[i]
        if t == "M":
            pos = (num(i + 1), num(i + 2)); subs.append([]); i += 3
        elif t == "C":
            p1, p2, p3 = (num(i + 1), num(i + 2)), (num(i + 3), num(i + 4)), (num(i + 5), num(i + 6))
            subs[-1].append(("C", pos, p1, p2, p3)); pos = p3; i += 7
        elif t == "L":
            p = (num(i + 1), num(i + 2)); subs[-1].append(("L", pos, p)); pos = p; i += 3
        elif t == "Z":
            i += 1
        else:
            raise ValueError(f"unexpected {t!r} in the spade's path")
    return subs


def path_data(sub):
    """One subpath back as path data, three decimals."""
    f = lambda p: f"{p[0]:.3f} {p[1]:.3f}".replace(".000", "")
    out = "M" + f(sub[0][1])
    for seg in sub:
        out += ("C" + " ".join(f(p) for p in seg[2:])) if seg[0] == "C" else ("L" + f(seg[2]))
    return out + "Z"


def points(sub, steps=24):
    """The subpath flattened to a polygon."""
    pts = []
    for seg in sub:
        if seg[0] == "L":
            pts += [seg[1], seg[2]]
            continue
        _, p0, p1, p2, p3 = seg
        for k in range(steps + 1):
            t = k / steps; u = 1 - t
            pts.append((u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
                        u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]))
    return pts


svg = SOURCE.read_text()
SUBS = subpaths(" ".join(re.findall(r' d="([^"]+)"', svg)))
DATA = [path_data(s) for s in SUBS]


def vector(comment, paths, width=48, group=None):
    body = "".join(paths)
    if group:
        body = f"""
    <group
        android:pivotX="512"
        android:pivotY="512"
        android:scaleX="{group}"
        android:scaleY="{group}">{body.replace(chr(10) + '    ', chr(10) + '        ')}
    </group>"""
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Drawn by scripts/logo.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{width}dp"
    android:height="{width}dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">{body}
</vector>
"""


def filled(colour):
    return [f"""
    <path
        android:fillColor="{colour}"
        android:pathData="{d}" />""" for d in DATA]


def outlined(colour, width):
    return [f"""
    <path
        android:strokeColor="{colour}"
        android:strokeWidth="{width}"
        android:fillColor="#00000000"
        android:pathData="{d}" />""" for d in DATA]


drawable = MAIN / "res/drawable"
(drawable / "logo_spade.xml").write_text(vector("The spade, in white: tinted where it's drawn.", filled("#FFFFFFFF")))
(drawable / "logo_spade_outline.xml").write_text(vector("The spade's outline, for ambient.", outlined("#FF8A8A8A", 14)))
(drawable / "ic_spade_mono.xml").write_text(vector("The spade, in white, for watch faces to tint.", filled("#FFFFFFFF")))
(drawable / "ic_launcher_foreground.xml").write_text(
    vector("The spade, in white, scaled into the icon's safe zone.", filled("#FFFFFFFF"), width=108, group="0.6"))

# The card back's logo, read by CardIcons.
(MAIN / "card-icons/Back.svg").write_text(
    '<svg width="1024" height="1024" viewBox="0 0 1024 1024" fill="none" xmlns="http://www.w3.org/2000/svg">\n'
    + "".join(f'<path d="{d}" fill="white"/>\n' for d in DATA) + "</svg>\n")


def png(colour, size=256, ss=4):
    """The spade on a transparent square, as the 1024 grid scaled to `size`, antialiased by supersampling."""
    big = size * ss
    mask = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(mask)
    k = big / 1024
    for sub in SUBS:
        draw.polygon([(x * k, y * k) for x, y in points(sub)], fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), colour + (0,))
    out.putalpha(mask)
    return out


nodpi = MAIN / "res/drawable-nodpi"
png((255, 255, 255)).save(nodpi / "complication_logo.png", optimize=True)
png((255, 255, 255)).save(nodpi / "complication_spade_mono.png", optimize=True)
print("Drew the spade into", len(DATA), "paths: logo_spade, logo_spade_outline, ic_spade_mono, ic_launcher_foreground, Back.svg, complication PNGs.")
