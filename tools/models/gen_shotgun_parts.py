#!/usr/bin/env python3
"""Splits the test shotgun's model into the parts its animation moves.

The shotgun comes from Trini3's "Simpleguns" resource pack as one item model
(assets/shotgun/models/item/shotgun.json). The tff:gun item model draws a gun
as several models, one per moving part, so this script copies the pack's
elements into one model per part, unchanged, and adds a 3D shell for the
reload. Run it again after editing shotgun.json:

    python3 tools/models/gen_shotgun_parts.py

shotgun.json itself stays whole: it holds the display transforms the gun uses
(and still works as a plain, static model).
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODELS = ROOT / "common/src/main/resources/assets/shotgun/models/item"

# Element indices in shotgun.json, by part.
PARTS = {
    # Barrel, front bead, fore-end and hinge knuckle: tip down on the hinge to open.
    "shotgun_barrel": [0, 1, 2, 3],
    # Receiver, rear sight, trigger guard, trigger and stock.
    "shotgun_body": [4, 5, 6, 10, 11, 12, 13, 14, 15, 16, 17],
    # Top lever: pushed aside to unlock the barrel.
    "shotgun_lever": [7],
    # Hammer: falls on the last shot, cocked at the end of a reload.
    "shotgun_hammer": [8, 9],
}

# Shell in the chamber, read from the item texture's flat colours (shotgun:item/shell):
# a red hull and a brass head. Hidden inside the barrel while it is closed.
SHELL_RED = [7, 6, 8, 7]
SHELL_BRASS = [4, 8, 5, 9]


def box(frm, to, uv):
    return {
        "from": frm,
        "to": to,
        "faces": {face: {"uv": uv, "texture": "#shell"} for face in ("north", "east", "south", "west", "up", "down")},
    }


def main():
    source = json.loads((MODELS / "shotgun.json").read_text())
    elements = source["elements"]
    used = sorted(i for indices in PARTS.values() for i in indices)
    assert used == list(range(len(elements))), f"every element must belong to exactly one part: {used}"
    for name, indices in PARTS.items():
        model = {
            "textures": source["textures"],
            "elements": [elements[i] for i in indices],
        }
        (MODELS / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n")

    shell = {
        "textures": {"shell": "shotgun:item/shell", "particle": "shotgun:item/shell"},
        "elements": [
            box([7.55, 4.7, 1.6], [8.45, 5.6, 4.0], SHELL_RED),
            box([7.5, 4.65, 4.0], [8.5, 5.65, 4.35], SHELL_BRASS),
        ],
    }
    (MODELS / "shotgun_shell.json").write_text(json.dumps(shell, indent=2) + "\n")
    print("wrote", ", ".join(list(PARTS) + ["shotgun_shell"]))


if __name__ == "__main__":
    main()
