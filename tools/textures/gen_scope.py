"""Generates the test scope textures (original pixel art). Run: python3 tools/textures/gen_scope.py

The scope is a vanilla spyglass fitted at the attachment bench, so the mounted scope uses a
spyglass-like copper and glass palette, and the scope view is a square frame in the style of the
spyglass overlay with a reticle on the glass.
"""
import os
from PIL import Image

ASSETS = os.path.join(os.path.dirname(__file__), "..", "..", "common", "src", "main", "resources", "assets", "shotgun", "textures")
COPPER = [(0x55, 0x29, 0x25), (0xa0, 0x51, 0x11), (0xbd, 0x6a, 0x28), (0xde, 0x92, 0x3a), (0xf4, 0xe0, 0x63)]
DARK = [(0x33, 0x17, 0x12), (0x55, 0x29, 0x25)]
GLASS = [(0x96, 0xb5, 0xd6), (0xbf, 0xd4, 0xeb), (0xe2, 0xef, 0xfd)]
CLEAR = (0, 0, 0, 0)


def model_texture():
    """UV 0-8 x 0-8: copper tube with dark end rings; 8-12 x 0-4: glass lens."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(8):
        for x in range(8):
            if x in (0, 7) or y in (0, 7):
                c = DARK[(x + y) % 2]
            else:
                c = COPPER[2 + ((x * 3 + y * 5) % 3 == 0)] if y > 2 else COPPER[3 + (y == 1 and x % 3 == 1)]
            img.putpixel((x, y), c)
    for y in range(4):
        for x in range(8, 12):
            edge = x in (8, 11) or y in (0, 3)
            img.putpixel((x, y), DARK[0] if edge else GLASS[2] if (x, y) == (9, 1) else GLASS[1])
    img.save(os.path.join(ASSETS, "item", "scope.png"))


def overlay_texture():
    """The scope view (textures/misc/scope_overlay.png). Odd size so the reticle has a true centre pixel."""
    size, c = 255, 127
    img = Image.new("RGBA", (size, size), (100, 130, 163, 13))  # faint glass tint, like the spyglass
    px = img.load()

    # Frame: dark outer band, copper inner band with a lit top/left and shaded bottom/right edge.
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if d < 4:
                px[x, y] = DARK[0] + (255,)
            elif d < 8:
                lit = x < size - 8 and y < size - 8 and (x == d or y == d)
                px[x, y] = (COPPER[3] if lit else COPPER[1 + (d == 7)]) + (255,)
            elif d == 8:
                px[x, y] = (30, 63, 88, 238)

    ink = (24, 22, 26, 235)
    inner, gap, outer = 3, 2, 44  # thin line from gap..outer, thick posts from outer to the frame

    def put(x, y, col=ink):
        if 9 <= x < size - 9 and 9 <= y < size - 9:
            px[x, y] = col

    for i in range(9, size - 9):
        r = abs(i - c)
        if r <= gap:
            continue
        w = 1 if r < outer else inner  # duplex: thin near the centre, heavy posts outside
        for t in range(-(w // 2), w // 2 + 1):
            put(i, c + t)
            put(c + t, i)

    # Range marks outside the ring: a short tick, then a longer one.
    for r in (26, 36):
        half = 3 if r == 36 else 2
        for t in range(-half, half + 1):
            put(c + r, c + t)
            put(c - r, c + t)
            put(c + t, c + r)
            put(c + t, c - r)

    # Ring around the centre and a red aiming dot.
    for y in range(c - 20, c + 21):
        for x in range(c - 20, c + 21):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if 15.5 <= d < 16.5:
                put(x, y, (24, 22, 26, 200))
    for x, y in [(c, c), (c - 1, c), (c + 1, c), (c, c - 1), (c, c + 1)]:
        put(x, y, (0xd8, 0x2a, 0x1e, 255))

    os.makedirs(os.path.join(ASSETS, "misc"), exist_ok=True)
    img.save(os.path.join(ASSETS, "misc", "scope_overlay.png"))


if __name__ == "__main__":
    model_texture()
    overlay_texture()
