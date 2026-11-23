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
- `common` has no Fabric transitive access wideners, so vanilla members Fabric widens (e.g. `ConditionalItemModelProperties.ID_MAPPER`, the 3-arg `DamageSources#source`) are private there. Build the value directly or go through `TffPlatform` / `TffClientPlatform`.
- Running a client in the cloud: OpenGL fails under Xvfb ("Couldn't find matching GLX visual"), but Vulkan through lavapipe works: `VK_ICD_FILENAMES=/opt/lavapipe/lvp_icd.json XDG_RUNTIME_DIR=<0700 dir> xvfb-run -a ./gradlew :fabric:runClient`. Sound fails (no ALSA device); harmless.

## Guns and ammo (core)

- Definitions are synced datapack registries `tff:gun` and `tff:ammo` (`data/<ns>/tff/gun/*.json`, `data/<ns>/tff/ammo/*.json`). The item only stores the definition key (`tff:gun` component), so stats can be tuned by datapack without code.
- Per-stack state is the `tff:gun_state` component (rounds, ammo type, cached capacity, shot and reload times). It is marked `ignoreSwapAnimation`, otherwise every shot would replay the hand re-equip bob. Timing fields are network-only; a world saved mid-reload just loses the reload.
- The server is authoritative for fire rate, ammo and reload. The client gates fire rate locally only to avoid spamming packets, and applies recoil locally (camera kick). The fire packet carries the client's yaw/pitch, like vanilla's use-item packet.
- Firing input is taken at the head of `Minecraft#handleKeybinds` (mixin) and `continueAttack` is cancelled while a gun is held. Vanilla throws away attack clicks while an item is being used, and aiming is "using" the gun, so the mixin must run first.
- Aiming down sights = using the item with `ItemUseAnimation.NONE` (no vanilla first-person motion) plus a `minecraft:use_effects` component (half walk speed, no sprint). Zoom is a narrow mixin on `AbstractClientPlayer#getFieldOfViewModifier`; the third-person pose reuses vanilla `CROSSBOW_HOLD` / `BOW_AND_ARROW` via a mixin on `AvatarRenderer#getArmPose`.
- Shotgun pellets: 26.x i-frames live in `LivingEntity.damageCooldownTime`, not `Entity#setInvulnerableTime` (tried first; only one pellet of six landed). The `tff:bullet` damage type is instead tagged `minecraft:bypasses_cooldown`, so every pellet and every rapid-fire round counts.
- Bullets have no renderer (NoopRenderer): they move several blocks per tick. They are never saved and despawn after the ammo's `lifetime`.
- Key codes in 26.x are SDL scancodes: use `InputConstants.KEY_R` (21), never GLFW numbers (82).

## Attachments and bench

- Attachments are a third synced registry, `tff:attachment` (`data/<ns>/tff/attachment/*.json`); guns list what they accept per slot (`"attachments": {"scope": [...]}`). Only the `scope` slot exists so far. A fitted scope is stored as a key in the `tff:attachments` component, so a renamed scope item comes back unnamed when removed.
- Scopes are drawn by the gun's own item model (`minecraft:composite` + the `tff:attachment` select property), so they are ordinary item geometry and work with shader packs. There is deliberately no screen-space scope overlay or render-to-texture sight yet.
- The bench's scope slot is a mirror of the gun's component, rebuilt in `slotsChanged`. `ScopeSlot#set` updates the gun before calling super (super triggers the mirror), and `ScopeSlot#remove` detaches before handing the item over. Getting either order wrong duplicates scopes; the bench gametests cover take, shift-click in/out, removing the gun and closing.
- 26.x blocks have no `codec()`, and `MenuType`'s constructor and `CreativeModeTab.Output` are not public in vanilla, so menu types and the creative tab are created through `TffPlatform`.
- Bench textures are original pixel art generated by `tools/textures/gen_bench.py` (re-run it after editing); the bottom face reuses vanilla spruce planks.
- Gametests needing a player with a network connection (anything that hands items back, like closing a menu) must use the deprecated `makeMockServerPlayerInLevel`; `makeMockServerPlayer` has a null connection.

## Test shotgun (`shotgun` namespace)

- Lives in `io.github.trini3.tff.testgun` plus `assets/shotgun` and `data/shotgun`; it is example content shipped with the framework for now. Models, textures and sounds come from Trini3's Simpleguns resource pack; the scope textures are generated by `tools/textures/gen_scope.py`.
- The break-action reload is 36 generated keyframe models (`models/item/reload/`, one per reload tick) built by `tools/models/gen_shotgun_reload.py` from the pack's base model: the barrel (and a fitted scope) hinges open, the spent shell is pulled and flies off, a fresh one slides in, the barrel closes. Edit the script, not the generated JSON, then re-run it; it also rewrites `items/shotgun.json`. Frames step once per tick (item models can't interpolate) and are tied to the 36-tick reload, so changing `reload.ticks` needs `FRAMES` changed too. Only one shell is shown going in even though the gun holds two, since the pack model has a single bore. Third person shows the barrel opening but no hand tilt. The open/close sounds play at the start and end of the reload, not exactly on the barrel movement.
- Left-hand display transforms are copies of the right-hand ones: the game mirrors them itself.
- The idle model's third-person transform was made for vanilla's arm-down `ITEM` pose; with the framework's `CROSSBOW_HOLD` pose it pointed straight up. Third-person contexts therefore use `shotgun_held` (idle geometry, `shotgun_loaded`'s third-person transform), selected with `minecraft:display_context`.
- Aim models (`shotgun_aiming`, `shotgun_aiming_scoped`) only override the first-person transform: x -8.96 cancels vanilla's 0.56-block hand offset, y lines the hammer/scope top up with the crosshair, z -4 keeps the stock from filling the screen. Tuned from cloud screenshots, not by eye in a real client.
- Scope geometry is added per gun state with `scope_on_<state>` models that set `parent` to that state's gun model, so they inherit its display transforms; a composite model renders each part with its own transforms, so a single shared scope model would float.
- Buckshot is 8 pellets x 2 damage: one close-range hit kills a pig and does most of a player's health. Tune in `data/shotgun/tff/ammo/buckshot.json`.
- In-game check: `./gradlew :fabric:runClientGameTest` (with the Xvfb/lavapipe variables above) runs `ShotgunClientGameTest`: reload, fire, aim, scope and bench, with screenshots in `fabric/build/run/clientGameTest/screenshots`. It is not part of `build`/CI. Sound cannot be checked in the cloud.
