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

See [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) for pitfalls and design decisions.
