#!/usr/bin/env python3
"""
Quick first-person preview of a tff:gun item model, without launching the game.

Replays the same transform chain the game uses (vanilla's first-person hand offset, the gun's
first-person display transform, the animation file's bones and clips, the automatic sight
alignment and the player's arms) and draws the parts as flat-coloured boxes with a crosshair and
the hotbar area marked. Good for tuning hold poses and reload keyframes; check the result in game
afterwards (./gradlew :fabric:runClientGameTest), since textures and lighting are only roughed in.

    python3 tools/preview/viewmodel.py shotgun:shotgun --out /tmp/hip.png
    python3 tools/preview/viewmodel.py shotgun:shotgun --aim 1 --out /tmp/aim.png
    python3 tools/preview/viewmodel.py shotgun:shotgun --reload-sheet 12 --out /tmp/reload.png

Needs numpy and Pillow. Paths are resolved against common/src/main/resources.
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "common", "src", "main", "resources")
WIDTH, HEIGHT = 854, 480
HAND_FOV = 70.0  # vanilla draws hands and held items with a fixed 70 degree field of view
NEAR = 0.05
HAND = (0.56, -0.52, -0.72)  # applyItemArmTransform, right hand
VIEW_ONLY = {"root", "gun", "right_arm", "left_arm"}
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


# ---------------------------------------------------------------- matrices (column vectors, like JOML)

def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def S(x, y, z):
    return np.diag([x, y, z, 1.0])


def Rx(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], dtype=float)


def Ry(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], dtype=float)


def Rz(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], dtype=float)


def item_transform(display):
    """ItemTransform.apply for the right hand: translate, rotationXYZ (= Rx Ry Rz), scale, translate(-0.5)."""
    if display is None:
        return T(-0.5, -0.5, -0.5)
    r = display.get("rotation", [0, 0, 0])
    t = [v / 16.0 for v in display.get("translation", [0, 0, 0])]
    s = display.get("scale", [1, 1, 1])
    return T(*t) @ Rx(r[0]) @ Ry(r[1]) @ Rz(r[2]) @ S(*s) @ T(-0.5, -0.5, -0.5)


def apply(m, p):
    v = m @ np.array([p[0], p[1], p[2], 1.0])
    return v[:3]


# ---------------------------------------------------------------- resources

def res_path(identifier, folder, ext):
    ns, path = identifier.split(":", 1) if ":" in identifier else ("minecraft", identifier)
    return os.path.join(ROOT, "assets", ns, folder, path + ext)


def load_json(path):
    with open(path) as f:
        return json.load(f)


def load_model(identifier):
    """Returns (elements, textures, display) following parents inside this mod."""
    data = load_json(res_path(identifier, "models", ".json"))
    elements, textures, display = data.get("elements"), dict(data.get("textures", {})), data.get("display", {})
    parent = data.get("parent")
    if parent and os.path.exists(res_path(parent, "models", ".json")):
        p_el, p_tex, p_disp = load_model(parent)
        elements = elements if elements is not None else p_el
        p_tex.update(textures)
        textures = p_tex
        merged = dict(p_disp)
        merged.update(display)
        display = merged
    return elements or [], textures, display


_texture_cache = {}


def face_colour(textures, face):
    ref = face.get("texture", "#0").lstrip("#")
    seen = 0
    while ref in textures and not ":" in ref and seen < 8:
        ref = textures[ref].lstrip("#")
        seen += 1
    tex_id = textures.get(ref, ref)
    path = res_path(tex_id, "textures", ".png")
    if path not in _texture_cache:
        _texture_cache[path] = Image.open(path).convert("RGBA") if os.path.exists(path) else None
    img = _texture_cache[path]
    if img is None:
        return (255, 0, 255)
    uv = face.get("uv", [0, 0, 16, 16])
    w, h = img.size
    x0, x1 = sorted((uv[0] / 16 * w, uv[2] / 16 * w))
    y0, y1 = sorted((uv[1] / 16 * h, uv[3] / 16 * h))
    box = (int(math.floor(x0)), int(math.floor(y0)), max(int(math.ceil(x1)), int(math.floor(x0)) + 1),
           max(int(math.ceil(y1)), int(math.floor(y0)) + 1))
    region = np.asarray(img.crop(box)).reshape(-1, 4).astype(float)
    opaque = region[region[:, 3] > 8]
    if len(opaque) == 0:
        return None
    return tuple(int(c) for c in opaque[:, :3].mean(axis=0))


def element_matrix(el):
    rot = el.get("rotation")
    if not rot:
        return np.eye(4)
    o = [v / 16.0 for v in rot.get("origin", [8, 8, 8])]
    if "axis" in rot:
        r = {"x": Rx, "y": Ry, "z": Rz}[rot["axis"]](rot.get("angle", 0))
    else:
        r = Rz(rot.get("z", 0)) @ Ry(rot.get("y", 0)) @ Rx(rot.get("x", 0))
    if rot.get("rescale"):
        scale = []
        for axis in np.eye(3):
            d = r[:3, :3] @ axis
            scale.append(1.0 / max(abs(d)))
        r = r @ S(*scale)
    return T(*o) @ r @ T(-o[0], -o[1], -o[2])


FACE_CORNERS = {
    # corner indices into the 8 box corners (bit 0 = x max, bit 1 = y max, bit 2 = z max)
    "north": (0, 2, 3, 1), "south": (4, 5, 7, 6), "west": (0, 4, 6, 2),
    "east": (1, 3, 7, 5), "down": (0, 1, 5, 4), "up": (2, 6, 7, 3),
}


def box_corners(lo, hi):
    return [np.array([hi[0] if i & 1 else lo[0], hi[1] if i & 2 else lo[1], hi[2] if i & 4 else lo[2]]) for i in range(8)]


def model_quads(identifier):
    """Quads in model space (block units): list of (corners[4], colour, face name)."""
    elements, textures, _ = load_model(identifier)
    quads = []
    for el in elements:
        lo = [v / 16.0 for v in el["from"]]
        hi = [v / 16.0 for v in el["to"]]
        m = element_matrix(el)
        corners = [apply(m, c) for c in box_corners(lo, hi)]
        for name, face in el.get("faces", {}).items():
            colour = face_colour(textures, face)
            if colour is None:
                continue
            quads.append(([corners[i] for i in FACE_CORNERS[name]], colour, name))
    return quads


# ---------------------------------------------------------------- animation (mirrors io.github.trini3.tff.anim)

EASINGS = {}


def _easing(name):
    def deco(fn):
        EASINGS[name] = fn
        return fn
    return deco


BACK = 1.70158
EASINGS.update({
    "linear": lambda t: t, "catmullrom": lambda t: t, "step": lambda t: 1.0 if t >= 1 else 0.0,
    "in_sine": lambda t: 1 - math.cos(t * math.pi / 2), "out_sine": lambda t: math.sin(t * math.pi / 2),
    "in_out_sine": lambda t: -(math.cos(math.pi * t) - 1) / 2,
    "in_quad": lambda t: t * t, "out_quad": lambda t: 1 - (1 - t) ** 2,
    "in_out_quad": lambda t: 2 * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 2 / 2,
    "in_cubic": lambda t: t ** 3, "out_cubic": lambda t: 1 - (1 - t) ** 3,
    "in_out_cubic": lambda t: 4 * t ** 3 if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2,
    "in_back": lambda t: (BACK + 1) * t ** 3 - BACK * t * t,
    "out_back": lambda t: 1 + (BACK + 1) * (t - 1) ** 3 + BACK * (t - 1) ** 2,
})


def normalize_easing(name):
    out = ""
    for c in name.strip():
        if c.isupper() and out:
            out += "_"
        out += c.lower()
    out = out.replace("-", "_")
    if out.startswith("ease_"):
        out = out[5:]
    return {"smooth": "catmullrom", "catmull_rom": "catmullrom", "constant": "step", "hold": "step"}.get(out, out)


class Channel:
    def __init__(self, data):
        if isinstance(data, list):
            self.keys = [(0.0, np.array(data, dtype=float), "linear")]
        else:
            keys = []
            for t, v in data.items():
                if isinstance(v, list):
                    keys.append((float(t), np.array(v, dtype=float), "linear"))
                else:
                    value = v.get("value", v.get("post", [0, 0, 0]))
                    easing = normalize_easing(v.get("easing", v.get("lerp_mode", "linear")))
                    keys.append((float(t), np.array(value, dtype=float), easing))
            self.keys = sorted(keys, key=lambda k: k[0])

    def length(self):
        return self.keys[-1][0]

    def sample(self, time):
        k = self.keys
        if len(k) == 1 or time <= k[0][0]:
            return k[0][1]
        if time >= k[-1][0]:
            return k[-1][1]
        hi = next(i for i in range(len(k)) if k[i][0] > time)
        lo = hi - 1
        a, b = k[lo], k[hi]
        span = b[0] - a[0]
        t = 1.0 if span <= 0 else (time - a[0]) / span
        if b[2] == "catmullrom":
            p0 = k[max(lo - 1, 0)][1]
            p3 = k[min(hi + 1, len(k) - 1)][1]
            t2, t3 = t * t, t * t * t
            return 0.5 * (2 * a[1] + (-p0 + b[1]) * t + (2 * p0 - 5 * a[1] + 4 * b[1] - p3) * t2 + (-p0 + 3 * a[1] - 3 * b[1] + p3) * t3)
        e = EASINGS[b[2]](min(max(t, 0.0), 1.0))
        return a[1] + (b[1] - a[1]) * e


class Clip:
    def __init__(self, data):
        self.loop = data.get("loop", False)
        self.tracks = {}
        for bone, track in data.get("bones", {}).items():
            self.tracks[bone] = {k: Channel(v) for k, v in track.items() if k in ("position", "rotation", "scale")}
        default = max([c.length() for tr in self.tracks.values() for c in tr.values()] or [0.0])
        self.length = data.get("length", default)

    def clip_time(self, time):
        if self.loop and self.length > 0:
            return time % self.length
        return min(max(time, 0.0), self.length)


class Animation:
    def __init__(self, data):
        self.bones = {}
        defs = data.get("bones", {})
        order, remaining = [], list(defs)
        while remaining:
            progressed = False
            for name in list(remaining):
                parent = defs[name].get("parent")
                if parent is None or parent in order:
                    order.append(name)
                    remaining.remove(name)
                    progressed = True
            if not progressed:
                raise ValueError("bone parent loop: %s" % remaining)
        self.order = order
        self.defs = defs
        self.clips = {name: Clip(c) for name, c in data.get("clips", {}).items()}

    def view_only(self, bone):
        return bone in VIEW_ONLY or bool(self.defs[bone].get("view_only", False))


class Pose:
    def __init__(self, animation):
        self.a = animation
        self.reset()

    def reset(self):
        self.pos = {b: np.array(d.get("position", [0, 0, 0]), dtype=float) for b, d in self.a.defs.items()}
        self.rot = {b: np.array(d.get("rotation", [0, 0, 0]), dtype=float) for b, d in self.a.defs.items()}
        self.scl = {b: np.array(d.get("scale", [1, 1, 1]), dtype=float) for b, d in self.a.defs.items()}

    def apply(self, name, time, weight, view_bones=True):
        clip = self.a.clips.get(name)
        if clip is None or weight <= 0:
            return
        t = clip.clip_time(time)
        for bone, track in clip.tracks.items():
            if bone not in self.a.defs or (not view_bones and self.a.view_only(bone)):
                continue
            if "position" in track:
                self.pos[bone] = self.pos[bone] + track["position"].sample(t) * weight
            if "rotation" in track:
                self.rot[bone] = self.rot[bone] + track["rotation"].sample(t) * weight
            if "scale" in track:
                self.scl[bone] = self.scl[bone] * (1 + (track["scale"].sample(t) - 1) * weight)

    def compute(self, view_bones=True):
        self.world = {}
        for b in self.a.order:
            d = self.a.defs[b]
            parent = d.get("parent")
            m = self.world[parent].copy() if parent else np.eye(4)
            if view_bones or not self.a.view_only(b):
                p = np.array(d.get("pivot", [0, 0, 0]), dtype=float) / 16
                q = self.pos[b] / 16 + p
                r, s = self.rot[b], self.scl[b]
                m = m @ T(*q) @ Rz(r[2]) @ Ry(r[1]) @ Rx(r[0]) @ S(*s) @ T(*(-p))
            self.world[b] = m
        return self


def pose_gun(animation, view, now=0.0):
    """GunPoser.pose for the first-person view."""
    pose = Pose(animation)

    def hold(name, weight):
        clip = animation.clips.get(name)
        if clip is None or weight <= 0:
            return
        time = now % clip.length if clip.loop and clip.length > 0 else clip.length
        pose.apply(name, time, weight)

    def once(name, age, weight):
        clip = animation.clips.get(name)
        if clip is not None and weight > 0 and age <= clip.length:
            pose.apply(name, age, weight)

    aim = view["aim"]
    hold("idle", 1 - 0.75 * aim)
    hold("aim", aim)
    hold("sprint", view["sprint"])
    hold("loaded", 1 - view["empty"])
    hold("empty", view["empty"])
    if view["fire"] >= 0:
        if "fire_aim" in animation.clips:
            once("fire", view["fire"], 1 - aim)
            once("fire_aim", view["fire"], aim)
        else:
            once("fire", view["fire"], 1 - 0.4 * aim)
    if view["reload"] is not None:
        name = "reload_round" if view["step"] > 0 and "reload_round" in animation.clips else "reload"
        clip = animation.clips.get(name)
        if clip:
            pose.apply(name, view["reload"] * clip.length, 1.0)
    return pose.compute(True)


# ---------------------------------------------------------------- the player's arm (AvatarRenderer + ArmRenderer)

def arm_alignment(right, slim):
    side = 1 if right else -1
    pivot_x = -5 * side
    hand_x = (-0.5 if slim else -1.0) * side
    z_rot = 0.1 * side
    swap = np.array([[-1, 0, 0, 0], [0, 0, -1, 0], [0, -1, 0, 0], [0, 0, 0, 1]], dtype=float)
    return swap @ T(-hand_x / 16, -9 / 16, 0) @ Rz(-math.degrees(z_rot)) @ T(-pivot_x / 16, -2 / 16, 0)


def arm_quads(right, slim, skin=(196, 140, 110), sleeve=(110, 200, 200)):
    """The arm part as vanilla poses it in renderHand (pivot, 0.1 rad tilt), split in sleeve and skin boxes."""
    side = 1 if right else -1
    pivot = (-5 * side, 2, 0)
    width = 3 if slim else 4
    x0 = (-2 if slim else -3) if right else -1
    part = T(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16) @ Rz(math.degrees(0.1 * side))
    quads = []
    for (y0, y1, colour) in ((-2, 2, sleeve), (2, 10, skin)):
        lo = (x0 / 16, y0 / 16, -2 / 16)
        hi = ((x0 + width) / 16, y1 / 16, 2 / 16)
        corners = [apply(part, c) for c in box_corners(lo, hi)]
        for name, idx in FACE_CORNERS.items():
            quads.append(([corners[i] for i in idx], colour, name))
    return quads


def vanilla_empty_arm():
    """renderPlayerArm for an empty right hand, no swing: the reference Minecraft first-person arm."""
    m = T(0.64000005, -0.6, -0.71999997) @ Ry(45) @ T(-1, 3.6, 3.5) @ Rz(120) @ Rx(200) @ Ry(-135) @ T(5.6, 0, 0)
    return [([apply(m, c) for c in q[0]], (150, 150, 150), q[2]) for q in arm_quads(True, False, (150, 150, 150), (120, 120, 120))]


# ---------------------------------------------------------------- the gun

class Gun:
    def __init__(self, item_id):
        ns, path = item_id.split(":", 1)
        definition = load_json(os.path.join(ROOT, "assets", ns, "items", path + ".json"))["model"]
        assert definition["type"] == "tff:gun", "not a tff:gun item model"
        self.definition = definition
        _, _, display = load_model(definition["transforms"])
        self.display = display
        self.animation = Animation(load_json(res_path(definition["animation"], "tff/animations", ".json"))) if "animation" in definition else Animation({})
        self.parts = []
        for part in definition["parts"]:
            self.parts.append(dict(part, quads=model_quads(part["model"])))
        self.sight = definition.get("sight")
        self.muzzle = definition.get("muzzle")

    def hand_matrix(self, context="firstperson_righthand"):
        return T(*HAND) @ item_transform(self.display.get(context))

    def alignment(self, attachments):
        sight = self.sight
        for part in self.parts:
            if part.get("sight") and part.get("attachment") in attachments:
                sight = part["sight"]
        if not sight:
            return np.zeros(3)
        ref = Pose(self.animation)
        if "aim" in self.animation.clips:
            ref.apply("aim", self.animation.clips["aim"].length, 1.0)
        ref.compute(True)
        point = np.array(sight["position"], dtype=float) / 16
        bone = sight.get("bone", "gun")
        if bone in ref.world:
            point = apply(ref.world[bone], point)
        view = self.hand_matrix()
        p = apply(view, point)
        linear = np.linalg.inv(view[:3, :3])
        return linear @ np.array([-p[0], -p[1], 0.0]) * 16

    def quads(self, view, attachments=(), arms=True, now=0.0):
        """All quads in view space (camera at the origin looking down -Z)."""
        pose = pose_gun(self.animation, view, now)
        shift = np.eye(4)
        if view["aim"] > 0:
            a = self.alignment(attachments) * view["aim"] / 16
            shift = T(*a)
        hand = self.hand_matrix()
        out = []
        flashing = view["fire"] >= 0 and view["fire"] * 20 < self.definition.get("flash_ticks", 2)
        for part in self.parts:
            if part.get("attachment") and part["attachment"] not in attachments:
                continue
            if any(h in attachments for h in part.get("hidden_by", [])):
                continue
            if part.get("flash") and not flashing:
                continue
            bone = part.get("bone", "gun")
            if part.get("flash") and self.muzzle:
                bone = self.muzzle.get("bone", "gun")
            m = hand @ shift @ pose.world.get(bone, np.eye(4))
            if np.linalg.det(m[:3, :3]) == 0:
                continue
            if part.get("flash") and self.muzzle:
                mp = np.array(self.muzzle["position"], dtype=float) / 16
                m = m @ T(*mp) @ T(-0.5, -0.5, -0.5)
            for corners, colour, name in part["quads"]:
                out.append(([apply(m, c) for c in corners], colour, name))
        if arms:
            for bone, right in (("right_arm", True), ("left_arm", False)):
                if bone not in pose.world:
                    continue
                pivot = np.array(self.animation.defs[bone].get("pivot", [0, 0, 0]), dtype=float) / 16
                m = hand @ shift @ pose.world[bone] @ T(*pivot) @ arm_alignment(right, False)
                if abs(np.linalg.det(m[:3, :3])) < 1e-9:
                    continue
                for corners, colour, name in arm_quads(right, False):
                    out.append(([apply(m, c) for c in corners], colour, name))
        return out


# ---------------------------------------------------------------- drawing

def clip_near(poly):
    out = []
    n = len(poly)
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        ina, inb = a[2] <= -NEAR, b[2] <= -NEAR
        if ina:
            out.append(a)
        if ina != inb:
            t = (-NEAR - a[2]) / (b[2] - a[2])
            out.append(a + (b - a) * t)
    return out


def project(p, width=WIDTH, height=HEIGHT, fov=HAND_FOV):
    f = 1.0 / math.tan(math.radians(fov) / 2)
    aspect = width / height
    x = f / aspect * p[0] / -p[2]
    y = f * p[1] / -p[2]
    return ((x + 1) / 2 * width, (1 - y) / 2 * height)


def render(quads, width=WIDTH, height=HEIGHT, label=None, background=(120, 160, 210)):
    img = Image.new("RGB", (width, height), background)
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, height // 2, width, height), fill=(90, 130, 60))
    faces = []
    for corners, colour, name in quads:
        poly = clip_near([np.asarray(c, dtype=float) for c in corners])
        if len(poly) < 3:
            continue
        depth = sum(p[2] for p in poly) / len(poly)
        pts = [project(p, width, height) for p in poly]
        # back-face cull in screen space (faces are wound counter-clockwise seen from outside)
        area = sum(pts[i][0] * pts[(i + 1) % len(pts)][1] - pts[(i + 1) % len(pts)][0] * pts[i][1] for i in range(len(pts)))
        if area > 0:
            continue
        shade = SHADE.get(name, 0.8)
        faces.append((depth, pts, tuple(int(c * shade) for c in colour)))
    for depth, pts, colour in sorted(faces, key=lambda f: f[0]):
        draw.polygon(pts, fill=colour, outline=tuple(max(0, c - 40) for c in colour))
    # crosshair, hotbar and health rows at GUI scale 2 (854x480)
    cx, cy = width // 2, height // 2
    draw.line((cx - 9, cy, cx + 9, cy), fill=(255, 255, 255))
    draw.line((cx, cy - 9, cx, cy + 9), fill=(255, 255, 255))
    draw.rectangle((cx - 182, height - 44, cx + 182, height - 1), outline=(20, 20, 20))
    draw.rectangle((cx - 182, height - 78, cx + 182, height - 62), outline=(120, 30, 30))
    if label:
        draw.text((6, 6), label, fill=(255, 255, 255))
    return img


def sheet(images, columns):
    w, h = images[0].size
    rows = (len(images) + columns - 1) // columns
    out = Image.new("RGB", (w * columns, h * rows), (0, 0, 0))
    for i, im in enumerate(images):
        out.paste(im, ((i % columns) * w, (i // columns) * h))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("item", help="item id, e.g. shotgun:shotgun")
    ap.add_argument("--aim", type=float, default=0.0)
    ap.add_argument("--sprint", type=float, default=0.0)
    ap.add_argument("--empty", type=float, default=0.0)
    ap.add_argument("--fire", type=float, default=-1.0, help="seconds since the shot")
    ap.add_argument("--reload", type=float, default=None, help="reload progress 0 to 1")
    ap.add_argument("--attachment", action="append", default=[], help="fitted attachment id, e.g. shotgun:scope")
    ap.add_argument("--reload-sheet", type=int, default=0, help="draw this many reload frames on one sheet")
    ap.add_argument("--columns", type=int, default=4)
    ap.add_argument("--vanilla-arm", action="store_true", help="also draw vanilla's empty-hand arm (grey) for comparison")
    ap.add_argument("--no-arms", action="store_true")
    ap.add_argument("--scale", type=float, default=0.5, help="sheet frame scale")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    gun = Gun(args.item)
    base = {"aim": args.aim, "sprint": args.sprint, "empty": args.empty, "fire": args.fire, "reload": args.reload, "step": 0}
    extra = vanilla_empty_arm() if args.vanilla_arm else []
    if args.reload_sheet:
        frames = []
        n = args.reload_sheet
        length = gun.animation.clips["reload"].length
        for i in range(n):
            progress = i / (n - 1)
            view = dict(base, reload=progress)
            img = render(gun.quads(view, args.attachment, not args.no_arms) + extra, label="reload %.2fs" % (progress * length))
            frames.append(img.resize((int(WIDTH * args.scale), int(HEIGHT * args.scale))))
        sheet(frames, args.columns).save(args.out)
    else:
        render(gun.quads(base, args.attachment, not args.no_arms) + extra).save(args.out)
    print(args.out)


if __name__ == "__main__":
    sys.exit(main())
