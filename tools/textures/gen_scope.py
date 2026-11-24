"""Generates the test scope textures (original pixel art). Run: python3 tools/textures/gen_scope.py

The scope is a vanilla spyglass fitted at the attachment bench, so the mounted scope uses a
spyglass-like copper and glass palette, and the scope view is the spyglass view's style with
rounded corners and a rustic sniper reticle.
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
    """The scope view (textures/misc/scope_overlay.png).

    Drawn in the style of vanilla's spyglass view: the same 8-texel dark and copper frame bands
    (shaded top/left, lit bottom/right), the blue glass edge and a faint mottled glass tint, but
    with rounded corners and an old-fashioned three-post sniper reticle. Odd size so the reticle
    has a true centre pixel.
    """
    size, c = 255, 127
    radius = 44
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    shade = [(49, 30, 10), (64, 41, 16)]       # outer, inner band on the top/left
    lit = [(64, 41, 16), (125, 80, 20)]        # outer, inner band on the bottom/right
    edge = [(30, 63, 88, 238), (45, 78, 121, 204), (69, 98, 147, 120), (131, 186, 223, 39)]
    tints = [(100, 130, 163, 13), (130, 163, 199, 13), (115, 150, 185, 20)]

    half = size / 2
    for y in range(size):
        for x in range(size):
            # Distance inside a rounded square, and which way its edge faces here.
            qx, qy = abs(x + 0.5 - half) - (half - radius), abs(y + 0.5 - half) - (half - radius)
            if qx > 0 and qy > 0:
                d = radius - (qx * qx + qy * qy) ** 0.5
                nx, ny = qx * (1 if x >= c else -1), qy * (1 if y >= c else -1)
            else:
                d = radius - max(qx, qy)
                nx, ny = (qx * (1 if x >= c else -1), 0) if qx > qy else (0, qy * (1 if y >= c else -1))
            if d < 0:
                px[x, y] = (0, 0, 0, 255)  # same black as around the square
                continue
            bands = lit if nx + ny > 0 else shade
            if d < 8:
                px[x, y] = bands[0] + (255,)
            elif d < 16:
                px[x, y] = bands[1] + (255,)
            elif d < 20:
                px[x, y] = edge[int(d) - 16]
            else:
                px[x, y] = tints[((x // 32) * 7 + (y // 32) * 3) % 3]  # mottled glass, like the spyglass

    # Three-post reticle (old military style): thick tapered posts from the left, right and
    # bottom; the bottom post comes to a point at the centre, which is where the gun aims.
    ink = (30, 22, 16, 240)

    def put(x, y):
        if 0 <= x < size and 0 <= y < size and px[x, y][3] < 200:
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
    img.save(os.path.join(ASSETS, "misc", "scope_overlay.png"))


if __name__ == "__main__":
    model_texture()
    overlay_texture()
