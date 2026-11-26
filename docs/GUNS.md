# Making a gun

A gun is one line of Java, a few JSON files of stats, and assets: its parts as ordinary item models,
an item model of type `tff:gun` that puts them together, and an animation file that moves them.
The test shotgun is a complete example, and the numbers below come from it.

| What | Shotgun file (resources under `common/src/main/resources/`) |
|---|---|
| Registration | `common/src/main/java/io/github/trini3/tff/testgun/TestShotgun.java` |
| Stats | `data/shotgun/tff/gun/shotgun.json`, `data/shotgun/tff/ammo/buckshot.json`, `data/shotgun/tff/attachment/scope.json` |
| Item model | `assets/shotgun/items/shotgun.json` |
| Part models | `assets/shotgun/models/item/shotgun_*.json`, `scope_on_shotgun.json` |
| Display transforms | `assets/shotgun/models/item/shotgun.json` (the whole, unsplit model) |
| Animation | `assets/shotgun/tff/animations/shotgun.json` |

Positions are in model pixels (the numbers in a model's elements, 16 to a block) and angles in
degrees. Guns are modelled like any held item: lying along the Z axis, muzzle toward -Z (north), Y up.

## 1. Register the items

```java
public static final Supplier<GunItem> RIFLE = TffApi.registerGun(Identifier.fromNamespaceAndPath("mymod", "rifle"));
public static final Supplier<Item> RIFLE_ROUND = TffApi.registerItem(Identifier.fromNamespaceAndPath("mymod", "rifle_round"), p -> p);
```

Call these from your mod initializer. `registerGun` makes a gun item whose stats come from
`data/mymod/tff/gun/rifle.json`: it stacks to 1, doesn't swing, and aiming slows the player to half
walking speed. To point it at another definition or change its item properties, use
`registerGun(itemId, definitionId, properties -> ...)`. Ammo and attachments are plain items, which
`registerItem` makes. Both list the item in the Firearms creative tab, in call order
(`addToCreativeTab` lists any other item).

New kinds of projectile (explosive, incendiary, ...) are registered with
`TffApi.registerProjectileBehavior(id, behavior)`, implementing `ProjectileBehavior` (`tick`,
`onHitEntity`, `onHitBlock`; server side only). Ammo picks one by id.

## 2. Stats (data)

Guns, ammo and attachments are datapack registries: a datapack can retune any gun without code,
and they are synced to clients, so the shooter's client predicts its own shots and reloads.

### Gun: `data/<ns>/tff/gun/<name>.json`

| Field | Default | Meaning |
|---|---|---|
| `ammo` | required | Ammo ids the gun takes, in the order a reload prefers them |
| `capacity` | required | Rounds when full (1 to 1024) |
| `fire_mode` | `semi` | `semi`: one shot per click. `auto`: keeps firing while held |
| `fire_interval` | 10 | Fewest ticks between shots |
| `reload.mode` | `magazine` | `magazine`: every missing round goes in at the end. `per_round`: one round after `ticks`, then one every `round_ticks`, and a shot interrupts it (tube magazines) |
| `reload.ticks` | required | Length of the whole reload (magazine) or of the first round (per round) |
| `reload.round_ticks` | 10 | Length of each further round (per round only) |
| `reload.sounds` | none | Sound cues timed from the start of the reload |
| `reload.round_sounds` | none | Sound cues timed from the start of each further round (per round only) |
| `spread.hip`, `spread.aim` | 4, 1 | Half-angle of the shot cone in degrees, from the hip and aiming. With a patterned ammo it moves the whole pattern instead (see below) |
| `recoil.pitch` | 2 | Upward camera kick per shot, in degrees |
| `recoil.yaw` | 0.5 | Largest sideways kick, in degrees (random direction) |
| `recoil.recovery` | 0.4 | Share of the upward kick that settles back by itself (0 to 1) |
| `aim.zoom` | 0.85 | Field of view multiplier while aiming (1 = no zoom) |
| `aim.recoil_factor` | 0.6 | Recoil multiplier while aiming |
| `sounds.fire`, `sounds.dry_fire` | none | Played for every shot, and when the trigger is pulled on an empty gun |
| `muzzle.particle` | `{"type": "minecraft:smoke"}` | Smoke particle, any particle type |
| `muzzle.count`, `muzzle.speed` | 4, 0.02 | Particles per shot (0 = no smoke) and how fast they drift |
| `damage_multiplier`, `velocity_multiplier` | 1, 1 | Scale the ammo's damage and speed |
| `attachments` | none | Accepted attachments per slot, e.g. `{"scope": ["mymod:rifle_scope"]}` |

A sound is written `{"sound_id": "mymod:item.rifle.fire"}`, naming any sound in your
`sounds.json`. A sound cue looks like this:

```json
{"tick": 19, "sound": {"sound_id": "minecraft:entity.item_frame.add_item"}, "volume": 0.6, "pitch": 1.4}
```

`volume` is 0 to 4 (default 1) and `pitch` 0.5 to 2 (default 1, plus a little random spread). The
server plays cues for everyone nearby and the shooter's client plays them straight away, on the
same tick. Put them on the moments the reload animation shows: tick = seconds × 20.

### Ammo: `data/<ns>/tff/ammo/<name>.json`

| Field | Default | Meaning |
|---|---|---|
| `item` | required | Item used up when reloading, one per round |
| `behavior` | `tff:bullet` | Registered projectile behaviour. `tff:bullet` damages what it hits and stops at blocks |
| `pellets` | 1, or the pattern's size | Projectiles per shot (buckshot has several) |
| `pattern` | none | A fixed spot for every pellet, in degrees off the aim (see below) |
| `damage` | required | Damage per projectile at close range |
| `min_damage` | `damage` | Damage per projectile at `falloff_end` and beyond |
| `falloff_start`, `falloff_end` | 16, 48 | Distance in blocks where damage starts to drop, and where it reaches `min_damage` |
| `speed` | 6 | Starting speed in blocks per tick |
| `gravity` | 0.01 | Downward acceleration in blocks per tick squared |
| `drag` | 0.99 | Share of its speed a projectile keeps each tick (1 = no drag) |
| `lifetime` | 60 | Ticks before the projectile disappears |
| `pierce` | 0 | Extra entities it passes through |

Every pellet counts: bullet damage skips the hurt cooldown, so 9 pellets of 5 damage deal 45.
Bullets hit exact hitboxes. Vanilla widens every target by up to 0.3 blocks for arrows that have
flown a few ticks, which would quietly spread a pattern's reach at range, so the framework doesn't.

**Spread or pattern.** Without a `pattern`, each pellet lands somewhere random inside the gun's
spread cone. With one, the pellets keep the pattern's shape and the gun's spread only moves where
the whole pattern lands, so aimed shots (`spread.aim` 0) put it exactly on the crosshair. Each
entry is either one point, `[right, up]` in degrees as the shooter sees it, or a ring of evenly
spaced points. A ring's first point sits `rotation` degrees clockwise from straight up. The
shotgun's buckshot is one centre pellet and a ring of eight, none of them straight up, down, left
or right:

```json
"pattern": [
  [0, 0],
  {"ring": 8, "radius": 0.65, "rotation": 22.5}
]
```

A pattern's size is the pellet count, so leave `pellets` out (a value that disagrees is an error).
To size one, work out where it lands: a point `a` degrees off the aim is `distance × tan(a)`
blocks off at the target. A zombie is 0.6 blocks wide and 1.95 tall.

### Attachment: `data/<ns>/tff/attachment/<name>.json`

| Field | Default | Meaning |
|---|---|---|
| `item` | required | Item fitted at the Attachment Bench |
| `slot` | required | `scope` (the only slot so far) |
| `zoom` | the gun's | Replaces the gun's `aim.zoom` while fitted |
| `aim_spread_multiplier` | 1 | Multiplies the aimed spread |
| `recoil_multiplier` | 1 | Multiplies recoil while aiming |
| `overlay` | none | Gives a spyglass-style view while aiming in first person: a texture id, read as `<ns>:textures/<path>.png`. It covers the screen, the gun is hidden and mouse speed follows the zoom. `minecraft:misc/spyglass_scope` reuses the spyglass's own |
| `reticle` | none | Texture drawn over the overlay |

The gun has to list the attachment under `attachments`, and its item model needs a part that
shows it (see below).

## 3. Models

### Part models

Split the gun into one ordinary item model per moving part (body, bolt, magazine, ...), all in the
same model space so that they line up when drawn together. Don't move elements while splitting:
moving parts is the animation's job. `tools/models/gen_shotgun_parts.py` does this for the shotgun
by copying elements from the single resource-pack model by index. Things that only show up during
an animation, like the shotgun's shell, are part models too, and so are attachments, modelled
where they sit on the gun (`scope_on_shotgun.json`).

### Display transforms

One model holds the display transforms for the whole gun: the item model's `transforms`. It can be
the complete, unsplit model, which then still works as a plain static item. Its
`firstperson_righthand` entry sets the hold:

- Keep `rotation` at `[0, 0, 0]`. The gun then lies parallel to the view direction, and perspective
  makes it point at the crosshair from the lower right, the way most shooters and gun mods hold a
  long gun. Fine-tune the angle with the `root` bone's rest rotation instead (the shotgun turns 3°
  in toward the middle).
- `translation` places the gun and `scale` sizes it. At scale 1 the gun is drawn at the same scale
  as the player's arms. The shotgun's `[0.96, 5.22, -6.88]` at scale 1 puts the grip about 0.6
  blocks right of, 0.45 below and 0.85 in front of the eye.
- Leave out `firstperson_lefthand`: left-handed players get the right-handed setup mirrored.

The other entries (`thirdperson_righthand`, `gui`, `ground`, `fixed`) work as for any item.

### Item model: `assets/<ns>/items/<gun>.json`

```json
{
  "model": {
    "type": "tff:gun",
    "transforms": "mymod:item/rifle",
    "animation": "mymod:rifle",
    "parts": [
      {"model": "mymod:item/rifle_body"},
      {"model": "mymod:item/rifle_bolt", "bone": "bolt"},
      {"model": "mymod:item/rifle_iron_sight", "hidden_by": ["mymod:rifle_scope"]},
      {"model": "mymod:item/scope_on_rifle", "attachment": "mymod:rifle_scope", "sight": {"position": [8, 12, 6]}},
      {"model": "tff:item/muzzle_flash", "flash": true}
    ],
    "sight": {"position": [8, 10.5, 6]},
    "muzzle": {"position": [8, 9, -12]}
  }
}
```

| Field | Default | Meaning |
|---|---|---|
| `transforms` | required | Model whose display transforms (and inventory lighting) the gun uses |
| `animation` | none | Animation id: `mymod:rifle` is `assets/mymod/tff/animations/rifle.json`. Without one the gun is drawn still, with no arms |
| `parts` | required | The parts, drawn in order |
| `sight` | none | Eye point while aiming. Without one, aiming doesn't line up the sights |
| `muzzle` | none | Barrel tip, where the flash and smoke come from |
| `flash_ticks` | 2 | How long the flash shows after a shot (0 to 20) |
| `arms` | true | Whether the player's arms are drawn in first person |

A point (`sight`, `muzzle`) is `{"bone": "barrel", "position": [x, y, z]}` in model pixels. It moves
with its bone, `gun` by default.

| Part field | Default | Meaning |
|---|---|---|
| `model` | required | Item model drawn for the part |
| `bone` | `gun` | Animation bone the part follows |
| `attachment` | none | Only shown while this attachment is fitted |
| `hidden_by` | none | Hidden while any of these attachments is fitted (iron sights under a scope) |
| `flash` | false | A muzzle flash, only shown just after a shot |
| `sight` | none | Eye point while aiming, used instead of the gun's while this attachment part shows |

**Flash.** `tff:item/muzzle_flash` is a ready-made pixel-art flash. A custom one is modelled with
its base on the model centre (8, 8, 8) and the flame toward -Z. It is moved to the `muzzle` point
and turned by a quarter turn and sized a little differently for every shot.

**Sights.** In the settled `aim` pose the gun is slid sideways and up so that the `sight` point sits
on the crosshair. Put the point where the eye would be, looking along the sights. The shotgun's
sits just above its hammer, because the single centred hammer would block the front bead otherwise.

**Model properties.** A `tff:gun` model can sit inside vanilla's `select`, `condition` and
`range_dispatch` item models like any other, for example to give a gun a different inventory icon
while it's empty. The framework adds the properties `tff:reloading` and `tff:aiming` (conditions),
`tff:reload_progress` (0 to 1) and `tff:ammo` (rounds loaded) for `range_dispatch`, and
`tff:attachment` (select, with `"slot": "scope"`, giving the fitted attachment's id).

## 4. Animation: `assets/<ns>/tff/animations/<name>.json`

```json
{
  "bones": {
    "root": {"pivot": [8, 4, 13]},
    "gun": {"parent": "root", "pivot": [8, 4, 13]},
    "bolt": {"parent": "gun", "pivot": [9, 8, 10]},
    "right_arm": {"parent": "gun", "pivot": [8.3, 4, 13], "rotation": [58, 35, 0]},
    "left_arm": {"parent": "gun", "pivot": [8, 3, 1], "rotation": [49, -38, 0]}
  },
  "clips": {
    "idle": {"loop": true, "length": 4, "bones": {"root": {"rotation": {
      "0": [0, 0, 0], "2": {"value": [0.4, 0, 0.2], "easing": "in_out_sine"}, "4": {"value": [0, 0, 0], "easing": "in_out_sine"}}}}},
    "aim": {"bones": {"root": {"position": [0, 0, 10]}}},
    "fire": {"length": 0.4, "bones": {"root": {"rotation": {
      "0": [0, 0, 0], "0.04": {"value": [8, 0, 0], "easing": "out_quad"}, "0.4": {"value": [0, 0, 0], "easing": "out_back"}}}}},
    "reload": {"length": 1.5, "bones": {"bolt": {"position": {
      "0.2": [0, 0, 0], "0.3": {"value": [0, 0, 3], "easing": "out_quad"}, "1.2": [0, 0, 3], "1.3": {"value": [0, 0, 0], "easing": "in_quad"}}}}}
  }
}
```

### Bones

| Field | Default | Meaning |
|---|---|---|
| `parent` | none | Parent bone, which the bone moves with |
| `pivot` | `[0, 0, 0]` | Point that rotation and scale happen around |
| `position`, `rotation`, `scale` | `[0, 0, 0]`, `[0, 0, 0]`, `[1, 1, 1]` | Rest pose; clips add to it |
| `view_only` | false | Only moves in first person (see below) |

A bone's transform is `translate(position) · translate(pivot) · rotate(rotation) · scale(scale) ·
translate(-pivot)`, on top of its parent's. Positive X rotation raises the muzzle, positive Y turns
it left and positive Z rolls the top to the left (applied X first, then Y, then Z). +Z is toward the
player. A scale of 0 on any axis hides the bone and its children.

Names the framework knows:

- `root`: the whole first-person view model. The hold angle, idle sway, aiming, sprinting and the
  fire kick move it.
- `gun`: the gun itself, child of `root`. Parts follow it by default, and the right arm usually
  hangs on it, so the gun and the hand on its grip move together (the shotgun tilts it to reload).
- `right_arm`, `left_arm`: the player's arms, drawn in first person with their skin, slim arms and
  sleeves. The middle of the hand sits on the bone's pivot. With no rotation the arm points
  straight ahead (hand toward -Z, shoulder toward +Z) with the back of the forearm up, and rotating
  the bone swings it around the hand: positive X drops the shoulder end, positive Y moves it right.
  Arm size is the display scale times the bone's scale, where 1 is vanilla's size: the shotgun's
  display scale is 1, so its arm bones need no scale, while a gun shown at 0.85 would give them
  1.18. Parent an arm to the part its hand holds: the shotgun's left arm
  is a child of the barrel and rides along when the barrel opens. Minecraft's arms are thick and
  only 12 pixels long, so keep the cut shoulder end below the bottom of the screen in every clip.

`root`, `gun`, the arms and any bone with `"view_only": true` only move in first person. In third
person, in the inventory and on the ground they sit exactly on their parent, while the other bones
(a bolt, a barrel that opens) animate everywhere. Mark a part view-only when it leaves the gun in
first person, like a round the hand brings in: in third person the player model's arms keep
vanilla's crossbow pose, so the round would float beside the player.

### Clips

The framework plays clips by name. All are optional.

| Clip | When |
|---|---|
| `idle` | Always, as a looping sway (a quarter of it while aiming) |
| `aim` | Blended in while aiming down sights |
| `sprint` | Blended in while sprinting |
| `loaded`, `empty` | Held while the gun has rounds, or none (a slide locked back, a hammer down) |
| `fire` | Once per shot. `fire_aim` replaces it while aiming; without one, `fire` plays at 60% while aiming |
| `dry_fire` | Once per trigger pull on an empty gun |
| `reload` | During a reload, stretched over `reload.ticks` |
| `reload_round` | For each further round of a `per_round` reload, stretched over `round_ticks`. Without it, `reload` plays again |

Clips add up by weight: a fire kick plays on top of whatever pose the gun is in. Positions and
rotations add, scales multiply. A clip that doesn't loop holds its last frame, so a clip of
constant values is simply a pose (that is how `aim` and `sprint` usually look). Give the `reload`
clip a `length` of `reload.ticks / 20` seconds, so its keyframe times are real seconds and line up
with the sound cues (the shotgun's is 1.8 for 36 ticks).

A clip has `length` in seconds (defaults to its last keyframe), `loop` (default false) and
`bones`, which maps bone names to `position`, `rotation` and `scale` channels. A channel is a
constant `[x, y, z]`, or keyframes: an object of `"<seconds>": value`, where a value is `[x, y, z]`
or `{"value": [x, y, z], "easing": "out_quad"}`. The easing shapes the way into that keyframe from
the one before. Before the first keyframe and after the last, the value holds.

Easings: `linear` (the default), `step`, `catmullrom`, `in_sine`, `out_sine`, `in_out_sine`,
`in_quad`, `out_quad`, `in_out_quad`, `in_cubic`, `out_cubic`, `in_out_cubic`, `in_back`,
`out_back`, `in_out_back` and `out_bounce`. An `ease_` prefix is fine. Keyframes in Blockbench's
Bedrock style (`{"post": [x, y, z], "lerp_mode": "catmullrom"}`) are read too, but check their
rotation directions against the ones above.

### Style

Simple Animated Guns is the reference for looks and Techguns for kicks, flashes and reloads. In
practice that means:

- Few keyframes, short holds and plain easings: `out_quad` into a move, `in_quad` into a stop,
  `out_back` for a small overshoot when something snaps home. No wobble beyond the slow idle sway.
- Big, readable moves. Tilt the gun so the action faces the camera, open it, swap the round or
  magazine, close it, settle. The shotgun's reload takes 1.8 seconds.
- A kick is a quick jump up and back (about 0.04 s) and a slower settle (0.3 to 0.4 s). The camera
  recoil from the gun's `recoil` stats does the rest.
- Keep parts blocky and textures on the pixel grid. The arms are the player's own skin.

## 5. Trying it out

- `python3 tools/preview/viewmodel.py mymod:rifle --out hip.png` draws the first-person view
  without launching the game: flat-coloured boxes with the crosshair and the hotbar marked, through
  the same transforms as the game. Add `--aim 1`, `--sprint 1`, `--fire 0.04` (seconds since the
  shot), `--reload 0.5` (progress), `--attachment mymod:rifle_scope`, or `--reload-sheet 12` for a
  sheet of reload frames. It needs numpy and Pillow and reads files under `common/src/main/resources`.
- In game, F3+T reloads animation files along with the models, so keyframes can be tuned without
  restarting.
- `./gradlew :fabric:runClientGameTest` plays the shotgun's in-game test and saves screenshots of
  every animation (see [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for running it in the cloud). Copy it for
  a new gun.
- A part whose `bone` isn't in the animation file is drawn still. A missing or broken animation
  file leaves the whole gun still and without arms, and the log says what failed to load.
