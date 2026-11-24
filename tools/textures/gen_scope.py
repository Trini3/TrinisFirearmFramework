"""Generates the test scope textures (original pixel art). Run: python3 tools/textures/gen_scope.py

The scope is a vanilla spyglass fitted at the attachment bench, so the mounted scope uses a
spyglass-like copper and glass palette. The scope view is vanilla's own spyglass view with a
rustic sniper reticle drawn on top.
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


def reticle_texture():
    """The scope reticle (textures/misc/scope_reticle.png), drawn over vanilla's own spyglass view.

    An old-fashioned three-post sniper reticle: thick tapered posts from the left, right and
    bottom, the bottom one coming to a point at the centre (where the gun aims). Everything else
    is transparent. It is stretched over the same square as the spyglass view, so the posts stop
    at the spyglass glass edge (16 of 256 texels in). Odd size so there is a true centre pixel.
    """
    size, c = 255, 127
    stop = 16
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    ink = (30, 22, 16, 240)

    def put(x, y):
        if stop <= x < size - stop and stop <= y < size - stop:
            px[x, y] = ink

    def width(r, tip):
        """Post half-width at distance r from the centre: a point at the tip, 3 wide soon after, 7 wide outside."""
        if r < tip + 6:
            return (r - tip) // 3
        return 1 if r < 64 else 3

    for r in range(14, c + 1):
        w = width(r, 14)
        for t in range(-w, w + 1):
            put(c - r, c + t)
            put(c + r, c + t)
    for r in range(0, c + 1):
        w = width(r, 0)
        for t in range(-w, w + 1):
            put(c + t, c + r)

    os.makedirs(os.path.join(ASSETS, "misc"), exist_ok=True)
    img.save(os.path.join(ASSETS, "misc", "scope_reticle.png"))


if __name__ == "__main__":
    model_texture()
    reticle_texture()
