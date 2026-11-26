# Trini's Firearm Framework

A vanilla-styled firearm framework for Minecraft (Fabric 26.3, structured for a later NeoForge port).
Guns are held pointing at the crosshair like in most shooters, aim down their sights, kick, flash
and reload with short keyframe animations, while keeping Minecraft's blocky look: every part is an
ordinary item model and the arms are the player's own skin.

`main` holds the shared framework. Content branches add their own guns and ammo on top of it.

## Layout

| Module   | What lives there |
|----------|------------------|
| `common` | Loader-agnostic code, compiled against vanilla Minecraft only. Gun logic, data formats, animation, rendering. |
| `fabric` | Fabric entrypoints and loader glue. Packages `common` into the shipped jar. Gametests live in `fabric/src/gametest`. |

## Building

Requires Java 25.

```
./gradlew build -x sourcesJar
```

`build` compiles, runs the JUnit tests in `common` and the server gametests in `fabric`.
The jar lands in `fabric/build/libs/`. The in-game check with screenshots is
`./gradlew :fabric:runClientGameTest` (needs a display, see [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)).

## Adding a gun (content branches)

1. Register items, one line each, e.g. in your mod initializer:
   ```java
   TffApi.registerGun(Identifier.fromNamespaceAndPath("mymod", "rifle"));
   TffApi.registerItem(Identifier.fromNamespaceAndPath("mymod", "rifle_round"), p -> p);
   ```
2. Data (`data/mymod/tff/`): `gun/rifle.json` (capacity, fire rate, reload timing and sound cues, spread,
   recoil, aim zoom, sounds, muzzle smoke, accepted attachments), `ammo/<name>.json` (item, pellets or a fixed
   pellet pattern, damage and falloff, speed, gravity, drag, behaviour) and optionally `attachment/<name>.json` (scopes).
3. Assets: the gun's parts as normal item models, an `assets/mymod/items/rifle.json` of type `tff:gun`
   listing them, and an animation file `assets/mymod/tff/animations/rifle.json` (bones, hold pose, aim,
   fire kick, reload). See [docs/GUNS.md](docs/GUNS.md).

The test shotgun (`io.github.trini3.tff.testgun`, `assets/shotgun`, `data/shotgun`) is a complete example.

Controls: left click fires, right click aims down sights, R reloads (rebindable under Controls > Firearms).
Scopes are fitted at the Attachment Bench; the test shotgun's scope is a spyglass.

See [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) for pitfalls and design decisions.
