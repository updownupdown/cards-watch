#!/usr/bin/env python3
"""
Cuts games' pictures for the watch from the phone app's, and prints their credits.

    python3 scripts/game-art.py ../cards elevens nestor

For each game id named (the phone's id: "little-windmill"), it reads the phone's
picture (assets/thumbnails/<Name>.webp in the phone repo, given first) and writes
the watch's two cuts of it to app/src/main/res/drawable-nodpi:

- banner_<id>.webp, 384×128 (3:1), behind the game's home button;
- thumb_<id>.webp, 96×96, for the tile and complication.

Each cut is as wide as the picture allows, at the height in CROPS (how far down
the picture its middle sits, 0 the top, 1 the foot; the middle when not listed),
so a face or a windmill stays in it. Check the result on the home screen and
the tile, and bump RESOURCES_VERSION in widget/GamesTileService.kt when a
thumbnail changes.

It then prints the game's `override val credit = ArtCredit(...)` line from the
phone's thumbnailCredit (src/data/games/<id>.ts), to paste over the game's own.

Needs Pillow (pip install pillow).
"""

import re
import sys
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"

# Where each cut's middle sits, down the picture: (banner, thumbnail).
CROPS = {
    "elevens": (0.3, 0.4),
    "little-windmill": (0.62, 0.55),
    "nestor": (0.45, 0.5),
    "ozanams-square": (0.4, 0.45),
    "royal-marriage": (0.5, 0.5),
}


def cut(image: Image.Image, aspect: float, middle: float) -> Image.Image:
    """The widest part of [image] at [aspect] (width / height), its middle [middle] of the way down."""
    w, h = image.size
    if w / h < aspect:
        cw, ch = w, round(w / aspect)
    else:
        cw, ch = round(h * aspect), h
    x = (w - cw) // 2
    y = min(max(round(h * middle - ch / 2), 0), h - ch)
    return image.crop((x, y, x + cw, y + ch))


def kotlin(value):
    if value is None:
        return "null"
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def credit(phone: Path, game: str) -> str:
    source = (phone / f"src/data/games/{game}.ts").read_text()
    block = re.search(r"thumbnailCredit: \{(.*?)\n  \}", source, re.S).group(1)

    def field(name):
        found = re.search(name + r""":\s*(["'])((?:\\.|(?!\1).)*)\1""", block, re.S)
        return found.group(2).replace("\\'", "'").replace('\\"', '"') if found else None

    values = [field(n) for n in ("title", "artistName", "date", "sourceName", "sourceLink")]
    return f"    override val credit = ArtCredit({', '.join(kotlin(v) for v in values)})"


def picture_name(phone: Path, game: str) -> str:
    source = (phone / f"src/data/games/{game}.ts").read_text()
    return re.search(r'thumbnail:\s*"([^"]+)"', source).group(1)


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    phone = Path(sys.argv[1])
    for game in sys.argv[2:]:
        image = Image.open(phone / "assets/thumbnails" / picture_name(phone, game)).convert("RGB")
        banner, thumb = CROPS.get(game, (0.5, 0.5))
        name = game.replace("-", "_")
        cut(image, 3, banner).resize((384, 128), Image.LANCZOS).save(OUT / f"banner_{name}.webp", quality=85)
        cut(image, 1, thumb).resize((96, 96), Image.LANCZOS).save(OUT / f"thumb_{name}.webp", quality=85)
        print(f"{game}: banner_{name}.webp, thumb_{name}.webp")
        print(credit(phone, game))


if __name__ == "__main__":
    main()
