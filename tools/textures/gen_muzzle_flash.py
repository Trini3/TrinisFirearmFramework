#!/usr/bin/env python3
"""Draws the framework's muzzle flash textures (16x16 pixel art, hard-edged alpha).

    python3 tools/textures/gen_muzzle_flash.py

muzzle_flash_front.png is the burst seen down the barrel, muzzle_flash_side.png the
flame seen from the side (muzzle on the left, flame going right). Both use a few
flat colours and fully opaque or fully clear pixels only, so they draw as cutout
like vanilla item textures and stay crisp.
"""
import math
import pathlib

from PIL import Image

OUT = pathlib.Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/tff/textures/item"

WHITE = (255, 255, 240, 255)
PALE = (255, 241, 160, 255)
YELLOW = (255, 206, 72, 255)
ORANGE = (255, 146, 40, 255)
DEEP = (226, 92, 24, 255)
CLEAR = (0, 0, 0, 0)


def front():
    img = Image.new("RGBA", (16, 16), CLEAR)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            # Four long rays on the axes, four short ones on the diagonals.
            reach = 3.4 + 4.4 * abs(math.cos(2 * a)) ** 8 + 2.0 * abs(math.sin(2 * a)) ** 10
            if r > reach:
                continue
            t = r / reach
            if r < 1.6:
                c = WHITE
            elif t < 0.45:
                c = PALE
            elif t < 0.7:
                c = YELLOW
            elif t < 0.9:
                c = ORANGE
            else:
                c = DEEP
            img.putpixel((x, y), c)
    return img


def side():
    img = Image.new("RGBA", (16, 16), CLEAR)
    cy = 7.5
    for x in range(16):
        # Flame half-height along its length: flares out of the muzzle, then tapers to a point.
        u = (x + 0.5) / 16
        half = 1.2 + 5.6 * math.sin(math.pi * min(1.0, u * 1.15)) ** 0.8 * (1 - 0.35 * u)
        # A few notches so the edge is not a smooth curve.
        if 2 <= x <= 11:
            half -= 0.9 * (x % 3 == 2) + 0.6 * (x % 5 == 4)
        for y in range(16):
            d = abs(y - cy)
            if d > half:
                continue
            t = d / max(half, 0.01)
            core = u < 0.55 and t < 0.35
            if core and u < 0.3:
                c = WHITE
            elif core or (t < 0.45 and u < 0.8):
                c = PALE
            elif t < 0.7:
                c = YELLOW
            elif t < 0.9:
                c = ORANGE
            else:
                c = DEEP
            img.putpixel((x, y), c)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    front().save(OUT / "muzzle_flash_front.png")
    side().save(OUT / "muzzle_flash_side.png")
    print("wrote", OUT / "muzzle_flash_front.png", OUT / "muzzle_flash_side.png")


if __name__ == "__main__":
    main()
