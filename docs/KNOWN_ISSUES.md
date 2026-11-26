# Known issues and decisions

Short notes per feature area: bugs, pitfalls, failed approaches and why things are the way they are.
New threads working on this repo: read this first, and add a short note here whenever a PR is merged.

## Build and CI

- CI (`.github/workflows/build.yml`) runs only on non-draft PRs that touch code, on `v*` tags and on a manual "Run workflow". Pushes build nothing. One job on `ubuntu-latest`, 8 minute timeout, a newer run cancels an older one on the same branch, and the Gradle cache is only saved by runs on `main`.
- Getting a jar: Actions > Build > Run workflow (the jar is attached to the run for 3 days), or push a `v*` tag (creates a GitHub release with the jar). PR runs only check the build and keep no jar.
- Keep the GitHub Actions budget at $0 (Settings > Billing and plans > Budgets) so nothing can ever be charged. There are no scheduled runs, CodeQL or larger runners, on purpose.
- `./gradlew build -x sourcesJar` compiles, runs the JUnit tests in `common` and the server gametests in `fabric` (Loom's `configureTests` hooks `runGameTest` into `check`). A warm build takes about 20 s in the cloud. The `Failed to connect ... minecraftservices` stack traces in its log are the sandbox having no internet, harmless.
- Java 25 is required by Loom 1.19 and Minecraft 26.x. A Java 21 JVM fails at plugin resolution ("Dependency requires at least JVM runtime version 25"); set `JAVA_HOME` to a 25 JDK.
- Maven Central sometimes answers 429 (Too Many Requests) to cloud machines on a cold cache. Retrying a few minutes later works.
- The in-game check is `./gradlew :fabric:runClientGameTest`: `ShotgunClientGameTest` reloads, fires, aims, fits the scope, goes third person, left-handed, sprints and opens the bench, asserting the mechanics and saving screenshots to `fabric/build/run/clientGameTest/screenshots`. It needs a display, so it isn't part of `build` or CI. In the cloud OpenGL fails under Xvfb ("Couldn't find matching GLX visual") and the game falls back to Vulkan through lavapipe: `VK_ICD_FILENAMES=/opt/lavapipe/lvp_icd.json XDG_RUNTIME_DIR=<a 0700 dir> xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew :fabric:runClientGameTest` (about 2 minutes). Sound fails there (no audio device); harmless, but sounds can't be checked in the cloud.
- In that test each wait-and-screenshot step advances about one tick more than it waits, so a series of screenshots drifts ahead of the tick counts in their names (`reload_t12` shows roughly tick 16).

## Multi-loader layout

- `common` uses Fabric Loom only to get a vanilla Minecraft classpath (no Fabric API; the loader is `compileOnly` for Mixin annotations). Any `net.fabricmc` import in `common` is a mistake even if it compiles; loader calls go through `TffPlatform` / `TffClientPlatform`, found with `ServiceLoader` and implemented in `fabric`.
- `fabric` adds common's source folders to its own source sets instead of depending on a common jar. That keeps one mod in one jar, and runs and gametests work with no extra classpath wiring. A future `neoforge` module should do the same.
- Porting to NeoForge: add a `neoforge` module like `fabric`, implement the two platform interfaces (registration, synced datapack registries, payloads, creative tab, menu type; key mappings, entity renderers, item model types and properties, asset reload listener, menu screens), call `Tff.init()` / `TffClient.init()` from its entrypoints, and reuse the client mixin config `tff.client.mixins.json`. Minecraft 26.x is unobfuscated (Mojang names everywhere), so there is no remapping.
- `common` has no Fabric access wideners, so vanilla members Fabric widens (the item model property id mappers, `MenuType`'s constructor, `CreativeModeTab.Output`) are out of reach there. Build the value directly or go through the platform interfaces.

## Guns and ammo (core)

- Definitions are synced datapack registries: `tff:gun`, `tff:ammo` and `tff:attachment` (`data/<ns>/tff/gun/*.json` and so on). The item only stores the definition key (`tff:gun` component), so stats can be tuned by datapack without code. Format: [GUNS.md](GUNS.md).
- Per-stack state is the `tff:gun_state` component (rounds, ammo type, cached capacity, shot and reload times). It is marked `ignoreSwapAnimation`, otherwise every shot would replay the re-equip dip. Timing fields are network-only; a world saved mid-reload just loses the reload.
- The server decides everything (fire rate, ammo, reloads, hits). The shooter's client runs the same checks to predict its own shots and reloads and plays their sounds, smoke, recoil and animation at once; the server sends those effects to everyone else and skips the shooter. The fire packet carries the client's yaw and pitch, like vanilla's use-item packet.
- Timing slack: a shot may arrive 1 tick early (`COOLDOWN_TOLERANCE`), and a shot in the last 3 ticks of a magazine reload finishes the reload instead of being refused (`RELOAD_TOLERANCE`), because the client starts, and so ends, its reload a little before the server. Gametests cover the reload edge from both sides.
- Firing input is taken at the head of `Minecraft#handleKeybinds` (mixin): attack clicks are consumed there, and `continueAttack` (mining) is cancelled while a gun is held. Vanilla throws away attack clicks while an item is being used, and aiming is "using" the gun, so the mixin must run first.
- Aiming down sights is using the item with `ItemUseAnimation.NONE` plus a `minecraft:use_effects` component (half walk speed, no sprint). Zoom is a mixin on `AbstractClientPlayer#getFieldOfViewModifier`; the third-person poses reuse vanilla's `CROSSBOW_HOLD` / `BOW_AND_ARROW` through a mixin on `AvatarRenderer#getArmPose`.
- Shotgun pellets: 26.x invulnerability frames live in `LivingEntity.damageCooldownTime`. The `tff:bullet` damage type is tagged `minecraft:bypasses_cooldown`, so every pellet and every rapid-fire round counts.
- Bullets have no renderer (`NoopRenderer`): they move several blocks per tick. They are never saved and vanish after the ammo's `lifetime`.
- Key codes in 26.x are SDL scancodes: use `InputConstants.KEY_R`, never GLFW numbers.
- Per-round reloads (tube magazines) load one round after `ticks`, then one every `round_ticks`, and a shot interrupts them. The gametest gun `tff_test:test_tube` covers them; no shipped gun uses them yet.

## Prediction and animation timing

- `ClientClock` is client ticks plus the frame's partial tick. Animations run on it, so they stop with the game and never jump when the server corrects the world time.
- `LocalGun` tracks only the main-hand gun; switching slots resets it. A shot the server never confirms is forgotten after a short window; a reload the server doesn't start, or stops early, fades out instead of snapping.
- `RemoteGuns` animates other players' guns from the synced state: a new `lastShot` is a shot, the reload runs on the world clock. Only bones that also move in third person animate there (see view-only bones below), and the player model's arms keep vanilla's poses.

## First-person rendering

- The `tff:gun` item model draws every part as an ordinary item layer and the arms through the player renderer's own first-person arm (`AvatarRenderer#renderRightHand`), so skins, slim arms, sleeves, resource packs and shader packs all behave as with vanilla items. The hand pass keeps vanilla's fixed 70 degree field of view and its own depth, so guns never clip into walls.
- While a gun is held in first person both arms belong to the gun, so the off-hand item isn't drawn.
- Arms: the middle of the hand sits on the arm bone's pivot; with no rotation the arm points straight ahead. Arm size is the display scale times the bone scale, and 1 is vanilla's size (the shotgun uses scale 0.85 and bone scale 1.18). Minecraft arms are chunky and only 12 pixels long, so keep the cut shoulder end below the bottom of the screen; the preview tool shows where it lands.
- Aiming: the gun is shifted sideways and up so that the `sight` point sits on the crosshair in the settled aim pose. The `aim` clip only squares the gun up and sets how close it comes. A fitted scope part's own `sight` replaces the gun's.
- Left-handed players get the whole thing mirrored (model space and arms), from the right-handed transforms.
- View-only bones (`root`, `gun`, `right_arm`, `left_arm`, and any bone with `"view_only": true`) only move in first person. Everywhere else they sit exactly on their parent. The shotgun's shell is view-only, otherwise it would float next to the player in third person while the hand that carries it in first person isn't there.
- Camera recoil goes through `LocalPlayer#turn`, like mouse movement, so the crosshair shows where the next shot goes. It is applied per frame (smooth at any frame rate): a quick kick, part of which settles back.
- Muzzle flash: a part with `"flash": true`, moved to the `muzzle` point and shown for `flash_ticks` (2) after each shot, turned by a quarter turn and sized a little differently every shot (quarter turns keep the pixel art on the grid). The shooter's smoke starts where the muzzle was drawn on screen; everyone else's starts at a computed muzzle position.
- Re-equip dip: vanilla dips the item whenever the stack changes. Shots don't trigger it (`ignoreSwapAnimation`) and neither does starting to aim (mixin), but fitting or removing a scope does, like enchanting an item would.

## Attachments and bench

- Guns list the attachments they accept per slot (`"attachments": {"scope": [...]}`); only the `scope` slot exists so far. A fitted attachment is stored as a key in the `tff:attachments` component, so a renamed scope item comes back unnamed when removed.
- An attachment is drawn by a part of the gun's own item model with a matching `attachment` field, so it is ordinary item geometry and follows the bone it sits on (the shotgun's scope opens with the barrel). Parts can also be `hidden_by` an attachment (iron sights under a scope).
- A scope with an `overlay` texture (and an optional `reticle` drawn over it) gives a spyglass-style view while aiming in first person (`ScopeView`): the hands and gun are hidden by setting vanilla's own spyglass render flag, the overlay is a plain HUD blit laid out like vanilla's spyglass overlay, the crosshair is hidden, and mouse speed follows the zoom. No render-to-texture, so shader packs see the same thing as with a spyglass.
- Several attachments may share one item; the bench fits the first one the gun accepts in that slot.
- The bench's scope slot mirrors the gun's component. `ScopeSlot#set` updates the gun before calling super (super triggers the mirror), and `ScopeSlot#remove` detaches before handing the item over. Getting either order wrong duplicates scopes; the bench gametests cover take, shift-click in and out, removing the gun and closing.
- 26.x blocks have no `codec()`, and `MenuType`'s constructor and `CreativeModeTab.Output` are not public in vanilla, so menu types and the creative tab come from `TffPlatform`.
- Bench textures are original pixel art generated by `tools/textures/gen_bench.py` (re-run it after editing); the bottom face reuses vanilla spruce planks.
- Gametests needing a player with a network connection (anything that hands items back, like closing a menu) must use the deprecated `makeMockServerPlayerInLevel`; `makeMockServerPlayer` has a null connection.

## Test shotgun (`shotgun` namespace)

- Lives in `io.github.trini3.tff.testgun` plus `assets/shotgun` and `data/shotgun`; it is example content shipped with the framework for now. The model, textures and sounds come from Trini3's Simpleguns resource pack. `tools/models/gen_shotgun_parts.py` splits the pack's single model into the moving parts (barrel, body, lever, hammer, a 3D shell) without changing any element; `shotgun.json` stays whole and holds the display transforms.
- Hold: FPS style. The gun lies parallel to the view axis (display rotation 0), so it points at the crosshair from the lower right, the way most shooters and gun mods hold a long gun. The grip sits at about (0.62, -0.42, -0.85) blocks from the eye, scale 0.85; arms are vanilla-sized, right hand on the grip, left hand under the fore-end. This replaced the old angled bow/crossbow-style hold.
- Aiming: the sight line runs over the hammer (`sight` 8, 7.9, 9.7 on the `gun` bone), since the single centred hammer would otherwise block the bead. The stock shows at the bottom of the screen and the right arm drops out of view.
- Reload (36 ticks): the gun tilts, the lever turns and the barrel breaks open with the left hand on the fore-end (the left arm is a child of the barrel bone, so it rides along); the spent shell pops back and flicks off to the right; the hand drops out of view and comes back with a fresh shell while the gun rolls its breech toward it; the hand pushes the shell in, goes back to the fore-end and snaps the barrel shut; the gun settles and the hammer cocks. Sound cues: open at tick 4, shell in at 19 (vanilla item frame sound, pitched up), close at 26, cock at 31.
- Only one shell is shown going in even though the gun holds two: the pack model has a single bore.
- The left hand's reload keyframes were placed in camera terms (hand beside the shell so the shell stays visible, the arm's cut end kept below the screen, the open barrel left uncovered) and converted to barrel-space numbers by scratch scripts; the animation file holds the result. To change the reload, edit the file and check frames with `tools/preview/viewmodel.py --reload-sheet 12`, then in game.
- Tried and dropped: the hand under or behind the shell (Minecraft's arm is about five times wider than the shell, so it covered the shell and the breech); loading without rolling the gun (the arm covered the open barrel); the old 36 generated per-tick keyframe models (stepped motion, no hands); the old angled hold.
- Third person: the barrel and lever animate, the shell stays in the barrel (view-only) and the player model's arms keep vanilla's crossbow pose, so there is no hand movement during the reload.
- Sprinting turns the gun down and to the left, still above the hotbar.
- Buckshot is 8 pellets of 2 damage (0.5 beyond 24 blocks): one close hit kills a pig. Tune in `data/shotgun/tff/ammo/buckshot.json`.
- The scope is a vanilla spyglass fitted at the bench (zoom 0.35, about 3x, versus the spyglass's 10x). Its view is vanilla's own spyglass texture, so resource packs that retexture the spyglass apply, with a three-post reticle on top; the reticle and mounted-scope textures come from `tools/textures/gen_scope.py`.
