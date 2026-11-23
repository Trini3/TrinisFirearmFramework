# Trini's Firearm Framework

A vanilla-styled firearm framework for Minecraft (Fabric 26.3, structured for a later NeoForge port).
Guns aim down sights and reload with simple animations, but stay close to Minecraft's own look and feel.

`main` holds the shared framework. Content branches add their own guns and ammo on top of it.

## Layout

| Module   | What lives there |
|----------|------------------|
| `common` | Loader-agnostic code, compiled against vanilla Minecraft only. Gun logic, data formats, rendering hooks. |
| `fabric` | Fabric entrypoints and loader glue. Packages `common` into the shipped jar. Gametests live in `fabric/src/gametest`. |

## Building

Requires Java 25.

```
./gradlew build -x sourcesJar
```

`build` compiles, runs JUnit tests in `common` and the server gametests in `fabric`.
The jar lands in `fabric/build/libs/`.

## Adding a gun (content branches)

1. Register items, one line each, e.g. in your mod initializer:
   ```java
   TffApi.registerGun(Identifier.fromNamespaceAndPath("mymod", "rifle"));
   TffApi.registerItem(Identifier.fromNamespaceAndPath("mymod", "rifle_round"), p -> p);
   ```
2. Data (`data/mymod/tff/`): `gun/rifle.json` (capacity, fire rate, reload, spread, recoil, aim zoom, sounds, muzzle smoke, accepted attachments), `ammo/<name>.json` (item, pellets, damage and falloff, speed, gravity, drag, behaviour) and optionally `attachment/<name>.json` (scopes: the item fitted at the bench, zoom, spread/recoil multipliers and an optional spyglass-style `overlay` texture).
3. Assets: normal item models and textures. `assets/mymod/items/rifle.json` can use the `tff:aiming`, `tff:reloading`, `tff:reload_progress`, `tff:ammo` and `tff:attachment` model properties for animations and fitted scopes.

The test shotgun (`io.github.trini3.tff.testgun`, `assets/shotgun`, `data/shotgun`) is a complete example.

Controls: left click fires, right click aims down sights, R reloads (rebindable under Controls > Firearms). Scopes are fitted at the Attachment Bench; the test shotgun's scope is a spyglass.

See [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) for pitfalls and design decisions.
