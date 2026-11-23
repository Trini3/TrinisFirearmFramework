#!/usr/bin/env python3
"""Generates the test shotgun's break-action reload animation.

Item models can't interpolate, so the animation is one keyframe model per game
tick of the reload, picked with a tff:reload_progress range_dispatch. Every frame
is a plain vanilla item model (cuboids plus display transforms), so it renders
through the normal item pipeline and stays shader-compatible.

Each frame:
  * hinges the barrel group (and the scope, which sits on the barrel) open/closed,
  * extracts the spent shell and slides a fresh one into the chamber,
  * tilts the gun in first person so the breech faces the player.

Run from the repo root after editing the base model or the timings:
    python3 tools/models/gen_shotgun_reload.py
It rewrites models/item/reload/*.json and items/shotgun.json.
"""
import copy
import json
import math
import os

ROOT = "common/src/main/resources/assets/shotgun"
MODELS = os.path.join(ROOT, "models/item")
OUT = os.path.join(MODELS, "reload")
ITEM = os.path.join(ROOT, "items/shotgun.json")

FRAMES = 36            # one per tick of the 36-tick reload in data/shotgun/tff/gun/shotgun.json
BARREL = [0, 1, 2, 3]  # barrel, front sight, fore-end, hinge block
HINGE = [8, 3.7, 4.6]  # same pivot the pack's pulling_0 model uses
OPEN_ANGLE = -42.0     # how far the barrel drops open, in degrees
BREECH_Z = 4.4         # rear face of the barrel


def ease(t):
    t = min(1.0, max(0.0, t))
    return t * t * (3 - 2 * t)


def seg(p, a, b):
    """0..1 progress of p through the [a, b] window, eased."""
    return ease((p - a) / (b - a))


def lerp(a, b, t):
    return [x + (y - x) * t for x, y in zip(a, b)] if isinstance(a, list) else a + (b - a) * t


def r(v):
    return round(v, 3)


# --- timeline (fraction of the reload) -------------------------------------------
def barrel_angle(p):
    opening = seg(p, 0.03, 0.17)
    closing = seg(p, 0.78, 0.92)
    return OPEN_ANGLE * (opening - closing)


def tilt(p):
    """0 = hip pose, 1 = full reload pose."""
    return seg(p, 0.0, 0.12) - seg(p, 0.88, 1.0)


def spent_offset(p):
    """Offset of the spent shell from its seated position, or None when gone."""
    if p < 0.17:
        return [0, 0, 0]
    if p > 0.32:
        return None
    pull = seg(p, 0.17, 0.22)
    fly = seg(p, 0.22, 0.32)
    return [0.6 * fly, 2.5 * fly - 1.5 * fly * fly, 2.5 * pull + 7 * fly]


def fresh_offset(p):
    """Offset of the new shell from its seated position, or None when not shown."""
    if p < 0.36:
        return None
    if p >= 0.66:
        return [0, 0, 0]
    reach = seg(p, 0.36, 0.48)
    push = seg(p, 0.5, 0.66)
    start, lined_up = [2.5, -4.0, 7.0], [0, 0, 3.2]
    return lerp(lerp(start, lined_up, reach), [0, 0, 0], push)


# --- geometry -------------------------------------------------------------------
def face(uv, tex):
    return {"uv": uv, "texture": tex}


def box(frm, to, uv, tex, rot=None):
    e = {"from": [r(v) for v in frm], "to": [r(v) for v in to],
         "faces": {f: face(uv, tex) for f in ("north", "east", "south", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    return e


RED = [7, 6, 8, 7]       # shell.png hull
BRASS = [4, 9, 5, 10]    # shell.png head


def shell(offset, rot):
    """A shotgun shell seated in the chamber (rim at the breech), moved by offset."""
    dx, dy, dz = offset
    z0 = BREECH_Z - 2.4
    hull = box([7.6 + dx, 4.75 + dy, z0 + dz], [8.4 + dx, 5.55 + dy, BREECH_Z - 0.4 + dz], RED, "#shell", rot)
    head = box([7.55 + dx, 4.7 + dy, BREECH_Z - 0.4 + dz], [8.45 + dx, 5.6 + dy, BREECH_Z + 0.2 + dz], BRASS, "#shell", rot)
    return [hull, head]


def hinged(angle):
    return {"angle": r(angle), "axis": "x", "origin": HINGE} if abs(angle) > 1e-3 else None


# --- display ----------------------------------------------------------------------
def blend(a, b, t):
    return {k: [r(v) for v in lerp(a[k], b[k], t)] for k in ("rotation", "translation", "scale")}


def main():
    base = json.load(open(os.path.join(MODELS, "shotgun.json")))
    held = json.load(open(os.path.join(MODELS, "shotgun_held.json")))["display"]
    scope = json.load(open(os.path.join(MODELS, "scope_on_shotgun.json")))

    hip = base["display"]["firstperson_righthand"]
    reload_pose = {
        "rotation": [hip["rotation"][0] + 5, hip["rotation"][1] + 55, hip["rotation"][2] + 10],
        "translation": [hip["translation"][0] - 1.5, hip["translation"][1] + 1.5, hip["translation"][2] - 2],
        "scale": hip["scale"],
    }

    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        os.remove(os.path.join(OUT, f))

    for i in range(FRAMES):
        p = i / FRAMES
        rot = hinged(barrel_angle(p))

        elements = copy.deepcopy(base["elements"])
        for idx in BARREL:
            if rot:
                elements[idx]["rotation"] = rot
            else:
                elements[idx].pop("rotation", None)
        for off in (spent_offset(p), fresh_offset(p)):
            if off is not None:
                elements += shell(off, rot)

        fp = blend(hip, reload_pose, tilt(p))
        display = copy.deepcopy(base["display"])
        display["firstperson_righthand"] = fp
        display["firstperson_lefthand"] = fp  # the game mirrors left-hand transforms itself
        display["thirdperson_righthand"] = held["thirdperson_righthand"]
        display["thirdperson_lefthand"] = held["thirdperson_lefthand"]

        gun = {"gui_light": base.get("gui_light", "front"),
               "textures": dict(base["textures"], shell="shotgun:item/shell"),
               "elements": elements, "display": display}
        json.dump(gun, open(os.path.join(OUT, f"shotgun_{i:02d}.json"), "w"), indent=1)

        sc = {"parent": f"shotgun:item/reload/shotgun_{i:02d}", "textures": scope["textures"],
              "elements": copy.deepcopy(scope["elements"])}
        for e in sc["elements"]:
            if rot:
                e["rotation"] = rot
        json.dump(sc, open(os.path.join(OUT, f"scope_{i:02d}.json"), "w"), indent=1)

    write_item()


# --- item definition ---------------------------------------------------------------
def model(name):
    return {"type": "minecraft:model", "model": f"shotgun:item/{name}"}


def scoped(plain, scope):
    """Adds the scope as a composite part when one is fitted."""
    return {"type": "minecraft:select", "property": "tff:attachment", "slot": "scope",
            "cases": [{"when": "shotgun:scope",
                       "model": {"type": "minecraft:composite", "models": [model(plain), model(scope)]}}],
            "fallback": model(plain)}


def frames(with_scope):
    def m(i):
        if with_scope:
            return {"type": "minecraft:composite",
                    "models": [model(f"reload/shotgun_{i:02d}"), model(f"reload/scope_{i:02d}")]}
        return model(f"reload/shotgun_{i:02d}")
    # Thresholds sit half a tick early so float rounding never skips a frame.
    return {"type": "minecraft:range_dispatch", "property": "tff:reload_progress",
            "entries": [{"threshold": r((i - 0.5) / FRAMES), "model": m(i)} for i in range(1, FRAMES)],
            "fallback": m(0)}


def write_item():
    reloading = {"type": "minecraft:select", "property": "tff:attachment", "slot": "scope",
                 "cases": [{"when": "shotgun:scope", "model": frames(True)}],
                 "fallback": frames(False)}
    not_reloading = {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["thirdperson_righthand", "thirdperson_lefthand"],
                   "model": scoped("shotgun_held", "scope_on_shotgun_held")}],
        "fallback": {"type": "minecraft:condition", "property": "tff:aiming",
                     "on_true": scoped("shotgun_aiming_scoped", "scope_on_shotgun_aiming_scoped")
                     | {"fallback": model("shotgun_aiming")},
                     "on_false": scoped("shotgun", "scope_on_shotgun")},
    }
    item = {"model": {"type": "minecraft:condition", "property": "tff:reloading",
                      "on_true": reloading, "on_false": not_reloading}}
    json.dump(item, open(ITEM, "w"), indent=2)
    open(ITEM, "a").write("\n")


if __name__ == "__main__":
    main()
