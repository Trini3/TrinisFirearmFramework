"""Generates the attachment bench block and GUI textures (original pixel art, vanilla style).

Run: python3 tools/textures/gen_bench.py  (needs Pillow). Writes into common/src/main/resources.
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "common", "src", "main", "resources", "assets", "tff", "textures")

# Spruce-like wood and iron palettes.
WOOD = [(0x5a, 0x3d, 0x22), (0x67, 0x47, 0x29), (0x72, 0x50, 0x2e), (0x7e, 0x59, 0x34)]
WOOD_DARK = (0x3e, 0x2a, 0x16)
IRON = [(0x6e, 0x6e, 0x6e), (0x8f, 0x8f, 0x8f), (0xb4, 0xb4, 0xb4), (0xd8, 0xd8, 0xd8)]
IRON_DARK = (0x45, 0x45, 0x45)
GREEN = [(0x2f, 0x4a, 0x2c), (0x37, 0x55, 0x33), (0x3f, 0x60, 0x3a)]


def planks(img, rnd, horizontal=True):
    for y in range(16):
        for x in range(16):
            a, b = (y, x) if horizontal else (x, y)
            if a % 4 == 3:
                c = WOOD_DARK
            else:
                c = WOOD[min(3, max(0, 1 + rnd.choice([-1, 0, 0, 1]) + (1 if a % 4 == 0 else 0)))]
                if (b + (a // 4) * 5) % 9 == 0:
                    c = WOOD[0]
            img.putpixel((x, y), c)


def frame(img):
    for i in range(16):
        for p in [(i, 0), (i, 15), (0, i), (15, i)]:
            img.putpixel(p, WOOD_DARK)


def block_textures():
    rnd = random.Random(7)
    os.makedirs(os.path.join(ROOT, "block"), exist_ok=True)

    # Top: green work mat inside a wooden frame, with an iron vise at the back.
    top = Image.new("RGBA", (16, 16))
    planks(top, rnd)
    for y in range(3, 15):
        for x in range(2, 14):
            top.putpixel((x, y), rnd.choice(GREEN))
    for x in range(4, 12):
        top.putpixel((x, 1), IRON[2])
        top.putpixel((x, 2), IRON[1])
    for x in (4, 11):
        top.putpixel((x, 1), IRON[3])
    top.putpixel((7, 2), IRON_DARK)
    top.putpixel((8, 2), IRON_DARK)
    # A little scope lying on the mat.
    for x in range(5, 11):
        top.putpixel((x, 9), IRON_DARK)
        top.putpixel((x, 10), IRON[1])
    top.putpixel((4, 9), IRON[0]); top.putpixel((4, 10), IRON[0])
    top.putpixel((11, 9), (0x7f, 0xb6, 0xd8)); top.putpixel((11, 10), (0x5d, 0x8a, 0xa8))
    frame(top)
    top.save(os.path.join(ROOT, "block", "attachment_bench_top.png"))

    # Side: planks with an iron band.
    side = Image.new("RGBA", (16, 16))
    planks(side, rnd, horizontal=False)
    for x in range(16):
        side.putpixel((x, 2), IRON[1])
        side.putpixel((x, 3), IRON[0])
    frame(side)
    side.save(os.path.join(ROOT, "block", "attachment_bench_side.png"))

    # Front: planks, iron band, and a tool rack with two hanging tools.
    front = Image.new("RGBA", (16, 16))
    planks(front, rnd)
    for x in range(16):
        front.putpixel((x, 2), IRON[1])
        front.putpixel((x, 3), IRON[0])
    for y in range(6, 13):
        front.putpixel((4, y), IRON[2])
        front.putpixel((11, y), IRON[2])
    for y in range(6, 9):
        front.putpixel((3, y), IRON[1]); front.putpixel((5, y), IRON[1])
    for x in range(10, 13):
        front.putpixel((x, 12), IRON[3])
    for x in range(3, 13):
        front.putpixel((x, 5), WOOD_DARK)
    frame(front)
    front.save(os.path.join(ROOT, "block", "attachment_bench_front.png"))


# Vanilla container GUI colours.
BG = (0xc6, 0xc6, 0xc6, 255)
HI = (0xff, 0xff, 0xff, 255)
SH = (0x55, 0x55, 0x55, 255)
OUT = (0x00, 0x00, 0x00, 255)
SLOT_IN = (0x8b, 0x8b, 0x8b, 255)
SLOT_DARK = (0x37, 0x37, 0x37, 255)


def slot(img, x, y):
    """An 18x18 slot whose item area starts at (x+1, y+1)."""
    for i in range(18):
        for j in range(18):
            img.putpixel((x + i, y + j), SLOT_IN)
    for i in range(17):
        img.putpixel((x + i, y), SLOT_DARK)
        img.putpixel((x, y + i), SLOT_DARK)
    for i in range(1, 18):
        img.putpixel((x + i, y + 17), HI)
        img.putpixel((x + 17, y + i), HI)


def gui():
    w, h = 176, 166
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for x in range(1, w - 1):
        for y in range(1, h - 1):
            img.putpixel((x, y), BG)
    # Outline with clipped corners, highlight top-left, shadow bottom-right (vanilla bevel).
    for x in range(2, w - 2):
        img.putpixel((x, 0), OUT); img.putpixel((x, h - 1), OUT)
        img.putpixel((x, 1), HI); img.putpixel((x, 2), HI)
        img.putpixel((x, h - 2), SH); img.putpixel((x, h - 3), SH)
    for y in range(2, h - 2):
        img.putpixel((0, y), OUT); img.putpixel((w - 1, y), OUT)
        img.putpixel((1, y), HI); img.putpixel((2, y), HI)
        img.putpixel((w - 2, y), SH); img.putpixel((w - 3, y), SH)
    for p in [(1, 1), (w - 2, h - 2)]:
        img.putpixel(p, OUT)
    img.putpixel((1, h - 2), OUT); img.putpixel((w - 2, 1), OUT)
    img.putpixel((3, 3), HI); img.putpixel((w - 4, h - 4), SH)
    # Bench slots (item areas at 44,35 and 116,35) and a "+" between them.
    slot(img, 43, 34)
    slot(img, 115, 34)
    for i in range(-4, 5):
        img.putpixel((88 + i, 43), SH)
        img.putpixel((88, 43 + i), SH)
    # Player inventory and hotbar (item areas from 8,84 and 8,142).
    for r in range(3):
        for c in range(9):
            slot(img, 7 + c * 18, 83 + r * 18)
    for c in range(9):
        slot(img, 7 + c * 18, 141)
    os.makedirs(os.path.join(ROOT, "gui", "container"), exist_ok=True)
    img.save(os.path.join(ROOT, "gui", "container", "attachment_bench.png"))


if __name__ == "__main__":
    block_textures()
    gui()
