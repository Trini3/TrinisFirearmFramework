# Known issues and decisions

Short notes per feature area: bugs, pitfalls, failed approaches and why things are the way they are.

## Build and CI

- CI (`.github/workflows/build.yml`) runs only on non-draft PRs that touch code, on `v*` tags and on manual "Run workflow". Pushes build nothing.
- Getting a jar: Actions > Build > Run workflow (artifact kept 3 days), or push a `v*` tag (creates a GitHub release with the jar).
- `./gradlew build` runs the server gametests too (Loom's `fabricApi.configureTests` hooks `runGameTest` into `check`). A full cold build takes about 45 s in the cloud.
- Java 25 is required by Loom 1.19 and Minecraft 26.x. A Java 21 JVM fails at plugin resolution ("Dependency requires at least JVM runtime version 25"); set `JAVA_HOME` to a 25 JDK.

## Multi-loader layout

- `common` uses Fabric Loom only to get a vanilla Minecraft classpath (no Fabric API, loader is `compileOnly` for Mixin annotations). Any `net.fabricmc` import in `common` is a mistake even if it compiles; keep loader calls behind interfaces implemented in `fabric`.
- `fabric` adds common's source folders to its own source sets instead of depending on a common jar. This keeps one mod, one jar, and makes runs and gametests work with no extra classpath wiring. A future `neoforge` module should do the same.
- Minecraft 26.x is unobfuscated (Mojang names everywhere), so there is no remapping step and common code is directly reusable on NeoForge.
