"""Generates the test scope textures (original pixel art). Run: python3 tools/textures/gen_scope.py"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "common", "src", "main", "resources", "assets", "shotgun", "textures", "item")
METAL = [(0x2b, 0x2b, 0x30), (0x36, 0x36, 0x3c), (0x42, 0x42, 0x49), (0x55, 0x55, 0x5d)]
LENS = [(0x3d, 0x6e, 0x8f), (0x5d, 0x9b, 0xbf), (0x9f, 0xd4, 0xec)]
CLEAR = (0, 0, 0, 0)


def model_texture():
    """UV 0-8 x 0-8: tube metal; 8-12 x 0-4: lens."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(8):
        for x in range(8):
            img.putpixel((x, y), METAL[(x * 3 + y * 5) % 3 + (1 if y in (2, 3) else 0)])
    for y in range(4):
        for x in range(8, 12):
            img.putpixel((x, y), LENS[2] if (x, y) == (9, 1) else LENS[1 if 0 < y < 3 and 8 < x < 11 else 0])
    img.save(os.path.join(OUT, "scope.png"))


def item_sprite():
    """Inventory sprite: a scope lying diagonally, eyepiece bottom-left, lens top-right."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for i in range(3, 13):
        x, y = i, 15 - i
        for dx, dy, c in [(0, 0, METAL[2]), (1, 0, METAL[1]), (0, 1, METAL[1]), (1, 1, METAL[0]), (-1, 0, METAL[3])]:
            if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                img.putpixel((x + dx, y + dy), c)
    # Objective bell and lens (top right), eyepiece (bottom left), mount rings.
    for p in [(12, 1), (13, 1), (14, 2), (14, 3), (11, 2), (13, 4)]:
        img.putpixel(p, METAL[0])
    for p in [(12, 2), (13, 2), (13, 3), (12, 3)]:
        img.putpixel(p, LENS[1])
    img.putpixel((13, 2), LENS[2])
    for p in [(1, 13), (2, 14), (1, 14), (2, 13), (3, 14), (1, 12)]:
        img.putpixel(p, METAL[0])
    for i in (6, 9):
        img.putpixel((i, 15 - i + 2), METAL[3])
        img.putpixel((i + 1, 15 - i + 2), METAL[0])
    img.save(os.path.join(OUT, "scope_item.png"))


if __name__ == "__main__":
    model_texture()
    item_sprite()
