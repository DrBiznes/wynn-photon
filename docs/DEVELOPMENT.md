# Developing Wynn-Photon

How Wynn-Photon is built, tested and patched. The user-facing description is in the [README](../README.md).

## Making shader changes

Building needs **Java 25** to run Gradle (Loom 1.19 requires it). Gradle downloads it automatically if needed
(see `gradle/gradle-daemon-jvm.properties`). The mod itself targets Java 21.

```
.photon/        downloaded photon_v1.4.zip and its extracted copy (base/)   gitignored
patches/        one unified diff per changed Photon file                      committed
work/           editable Photon = base + patches                              gitignored
```

1. `gradlew applyPatches` downloads Photon (hash-checked), then rebuilds `work/` from it plus `patches/`.
2. Edit files in `work/`. Label your edits with a `Wynn-Photon:` comment so they're easy to find.
3. To try them in game, run `gradlew dev` (see [the dev client](#the-dev-client) below) and press `R` in game to
   reload after each edit. Or `gradlew installDevPack` copies `work/` to `<shaderpacksDir>/photon_v1.4 + WynnPhoton-dev`
   in your own Minecraft instance. Select it in Iris and press `R` after each install. Set `shaderpacksDir` in
   `local.properties` (gitignored) using forward slashes:
   ```properties
   shaderpacksDir=C:/Users/you/AppData/Roaming/ModrinthApp/profiles/<profile>/shaderpacks
   ```
4. `gradlew makePatches` writes your edits back into `patches/`. It re-applies the new patches with the same code the
   build uses to make the jar's patch, and fails if they don't reproduce `work/` exactly. Commit `patches/`.
5. `gradlew build` produces the jar and runs the tests. The tests patch the real Photon zip.

`applyPatches` won't overwrite `work/` if it has edits that aren't in `patches/` yet. Run `makePatches` first, or pass
`-Pforce` to discard them.

Only text files can be patched for now. Adding new text files and deleting files both work.

### The dev client

`gradlew dev` starts Minecraft 1.21.11 with Sodium, WynnIris, Photonics, Voxy, Fabric API, Mod Menu and this mod, and
opens the **Wynn-Photon Test** world: a creative superflat gallery of every block Wynn-Photon changes. Everything it
uses lives in `run/` (gitignored), so it never touches your own Minecraft.

- `work/` is linked into `run/shaderpacks` as `photon_v1.4 + WynnPhoton-dev` and is selected in Iris. Edit `work/`,
  then press `R` in game. On Windows the link is a directory junction, so no admin rights are needed.
- The unmodified Photon zip is in `run/shaderpacks` too, so the mod also builds its own patched pack on launch, as it
  does for players. Switch to it in Iris's shader menu to check that `patches/` matches `work/`.
- The world needs **Wynncraft's resource pack**, which can't be downloaded outside the game. The task copies the one
  Minecraft cached when you last joined Wynncraft. It looks in `wynncraftInstanceDir` (set it in `local.properties`
  to your Minecraft instance folder), then in the instance holding `shaderpacksDir`, then in the default
  `.minecraft`. Without one, the world still works but the blocks look vanilla. Minecraft asks to load the pack when
  the world opens.
- `gradlew dev -Pfresh` starts the world over. See [testworld/README.md](../testworld/README.md) for the gallery and its
  commands.

### The lighting test

`gradlew lightTest` measures how bright the pack renders, with nobody at the keyboard. It starts the dev client,
photographs seven fixed spots in the test world (open ground, tree canopy, stone overhang, a room with one window, a
room lit by an invisible light block, a room lit by a torch, and a cave mouth) at noon and at midnight under a list
of shader option sets, closes the game and writes the report:

```
build/light-test/report.html     tables of mean luminance and of option-to-option ratios, plus every shot
build/light-test/luminance.csv   mean luminance of every shot
build/light-test/ratios.csv      the comparison table as data
build/light-test/*.png           the shots, named <spot>__<time>__<set>.png
```

- The cases are in `testworld/light_test.txt`: the camera spots, the times, the option sets (shader options as
  `KEY=value`; everything else is the pack's default) and the comparisons the report tabulates. The spots themselves
  are built by `testworld/gen_gallery.py`.
- The full list is 154 shots and takes about 25 minutes. `gradlew lightTest -Ponly=<regex>` shoots only the file
  names that match, for example `-Ponly=cave_mouth` or `"-Ponly=__noon__(on|off)\.png"`.
- `gradlew lightTestReport` rebuilds the report from the shots already taken.
- Leave the game window visible while it runs: a minimized window renders nothing. Don't run it while `gradlew dev`
  is open, because both use `run/`.
- Brightness is the mean luminance of the picture as displayed (after tone mapping), as a percentage of white. The
  cameras look at the ground and walls, not at the sky. The option set `on_again` repeats `on`, so the
  `on_again / on` column shows the noise of the measurement. Auto exposure must stay off for the numbers to mean
  anything; it is off by default.
- The test writes the dev pack's option file (`run/shaderpacks/photon_v1.4 + WynnPhoton-dev.txt`) for each option set
  and puts your own options back at the end. If the game is killed half way, your copy is in
  `build/light-test/options-before-test.txt`.
- The harness is the `lighttest` source set (`src/lighttest/`). Only the screenshot test runs load it, and it is never
  part of the mod jar.

### The sky and map tests

Two more screenshot tests use the same harness, case file format (see the top of `testworld/light_test.txt`) and
report, at 1600x900:

- `gradlew skyTest` photographs the sun and the moon (`testworld/sky_test.txt`): close-ups at a 30° field of view
  from sunrise to sunset and through all eight moon phases, wide views with Photon's clouds, rain and thunder, and the
  ground of a desert, snowy plains, swamp and jungle (the spot's `biome=` fills the ground around it first). Spots
  aimed at `sun` or `moon` look straight at it, using the game's sun and moon angle and the pack's
  `sunPathRotation`, so it sits in the middle of the picture; the report shows that middle at 1:1 beside each shot.
  It compares the cube, the square, Photon's round sun and moon and the vanilla ones. About 70 shots, 8 minutes.
  Results go to `build/sky-test/`.
- `gradlew mapTest` photographs the Wynncraft map as Voxy LODs (`testworld/map_test.txt`), the way players see it
  with Voxy: Mount Wynn from above, a sunset behind it, the sun and moon over the map, and the Realm of Light. Results
  go to `build/map-test/`. It runs in the "Wynn-Photon Map" world, which `installMapWorld` makes, as WynnVista's Voxy
  fixture does: a superflat world of air in spectator mode, whose Voxy storage (`voxy/<world id>/storage`) is a copy
  of a Wynncraft Voxy cache. The cache is `voxyCache` in `local.properties`, or else the biggest Wynncraft world in
  the `.voxy` folder of the instance holding `shaderpacksDir`. Close Minecraft before the first copy; it is copied
  once, and again only with `-Pfresh`. `runMapTest` switches `ingest_enabled` off in `run/config/voxy-config.json`
  for the run and puts the file back afterwards (a copy waits in `build/map-test/` until then), so the empty chunks
  around the camera never overwrite the LODs; the harness refuses to shoot if it finds ingestion on. Don't open the
  map world in `gradlew dev`, which ingests. The run uses a render distance of 2.
- After an Iris reload, Voxy rebuilds its renderer before Iris's new pipeline exists and falls back to its non-shader
  pipeline (`using pipeline 'NormalRenderPipeline'` in the log), which draws the LODs and the sky wrong until the
  chunks are reloaded. The harness reloads the chunks (as F3+A does) after every option set's reload. Light test
  results from before this fix may have shot every set after the first with it.
- `-Pcases=<file>` runs a test with another case file, for a one-off check.
- On Windows, `gradlew.bat` breaks a `-Ponly` regex that holds `|`. Set it as an environment variable instead:
  `ORG_GRADLE_PROJECT_only='sun_close__(noon|sunset)' gradlew skyTest` (Git Bash).

### Updating to a new Photon release

1. Make sure `work/` is saved (`gradlew makePatches`), then update `photon_pack_name`, `photon_display_name`,
   `photon_url` and `photon_sha512` in `gradle.properties`. The URL and SHA-512 are listed by
   `https://api.modrinth.com/v2/project/photon-shader/version`.
2. Run `gradlew applyPatches -Pforce`. Hunks still apply if their context lines are unchanged, even if they moved. A
   hunk whose context changed fails with the file name and hunk number. Fix it by hand in `work/`. Applying the old
   patch to the new base by hand is easiest.
3. `gradlew makePatches`, then bump `mod_version`.

### The cloud distance for WynnVista

[WynnVista](https://modrinth.com/mod/wynnvista) draws its world effects, such as the Mount Wynn smoke plume, over the
finished image and reads Photon's clouds history to put them behind the clouds. Photon's own apparent distance
(`colortex12.x`) does not serve for that: along a cloud's edge the filtering mixes in the huge distance stored for
"no cloud", and over terrain it is the distance to only the part of the cloud in front of the terrain, which the
history carries into the sky pixels beside it. An effect standing there was taken to be behind a cloud that was not in
front of it, differently each frame.

`program/d2_clouds_upscaling.fsh` therefore keeps a second distance for effects in `colortex12.a`, and
`include/buffers.glsl` makes that buffer RGBA16F. It is the only change to Photon's cloud programs, and nothing
Photon reads is changed, so the pack's own picture is the same.

- The contract with WynnVista (2.2.4 and later): the alpha is the distance to the cloud in blocks, 0 where there is
  none and on terrain. WynnVista uses it whenever the buffer has an alpha channel, so don't give that channel another
  meaning.
- A sky pixel's distance changes only in the frames in which that very pixel is marched, and not by a march that
  `d1_clouds` cut short at terrain beside the pixel. A march that finds a cloud is blended into the distance kept, or
  taken as it is when none is kept or the two are more than 1.5 times apart (another cloud). After a march that finds
  none, the distance is dropped once the clouds image kept for the pixel has let go of the cloud as well.
- A sky pixel that holds no distance, while the clouds image kept for it has a cloud (lets less than 0.9 through),
  takes the distance of the march beside it, unless terrain cut that march short. The pixels are marched in turn,
  each once in 16 frames, and until a pixel's own turn WynnVista left the cloud out there, so the effect showed
  through it in specks. It cannot make a distance jump, since there is none to change. In WynnVista's fixture
  (2026-11-21) this took the cloud pixels WynnVista's reader of the time left out from 69 to 38 a frame; WynnVista
  now also looks further out for a distance, which takes them to 0.
- Cost: one read of four texels of the history and eight of the depth buffers a pixel, in a pass that already runs
  over the whole view. In WynnVista's fixture client (1920x1080, Voxy, RTX 4070, 2026-11-21) the GPU time of the
  frame before WynnVista's effects was 4.05 and 4.08 ms with the change and 4.01 and 4.22 ms without, two runs each:
  no difference beyond what one run differs from the next by, about 0.1 ms. `colortex12` holds one component more:
  at most 8 MB at 1920x1080 for its two textures.
- To test it, use WynnVista's fixture client with `work/` copied in as the shader pack: `lod_fixture.py run --iris
  --shaderpack ... --burst ... --dump-clouds ...` saves consecutive frames and this buffer with them (see "Shader pack
  clouds" in WynnVista's `docs/WORLD_EFFECTS.md`).

### The scene distance for WynnVista

WynnVista cuts its world effects off at the terrain using the depth buffer. With TAA every frame's depth is shifted by a
part of a pixel, while the image WynnVista draws over is the blend of many frames. Along the edge of a block a pixel is
therefore the block in one frame's depth and what is behind it in the next, and an effect cut by that depth was there
one frame and gone the next over an image that stood still: the dark void under the Sky Islands shimmered along every
block edge in front of it.

`program/wynn_scene_distance.fsh` is a pass of our own (`composite90` in `world0`, `world-1` and `world1`, after all of
Photon's) that does to the scene's distance what `c4_taa_exposure` does to its colour: it carries last frame's value to
where the pixel was, keeps it within what the nine pixels around hold now, and blends this frame's in. Along an edge the
result lies between the distance of the block and of what is behind it, by how much of the pixel each covers. WynnVista
works the block's share out from that and draws that much of the effect in front of it, the same in every frame.

- The program, its vertex stage and six stubs are all new files, and the buffer's format is declared in the program's
  own header comment. Nothing Photon draws reads it. The only hooks in Photon's files are for its switch, "WynnVista
  Effect Edges" on the Wynn-Photon page (`WYNN_VISTA_EDGES`, on by default): the option and its decoy in
  `settings.glsl`, its name in `lang/en_US.lang`, and in `shaders.properties` its place on the page and the three
  `program.<world>/composite90.enabled` lines. Switched off, the pass does not run and WynnVista draws its effects as
  with stock Photon.
- The contract with WynnVista (2.2.5 and later): `colortex20` is R16 and holds `log2(1 + blocks to the scene) / 16`, 1 for sky. WynnVista
  takes the pack to provide it whenever `colortex20` exists with that format and the view's size, so don't use that
  buffer for anything else. A value that does not lie between the nearest and the furthest distance around a pixel is
  ignored there, so a frame in which the pass did not run does no harm.
- A frame's share of the result is 5% (`blend_weight`). Photon's own TAA takes 10% to 35%: with that much, a pixel half
  covered by a block followed which frames hit the block instead of settling on a half. What is carried over from
  before the camera moved is dealt with by the clamp, not by the blend, so the low share leaves no trail.
- What it leans on in Photon, and so what a new Photon release can break: the names `screen_to_view_space`,
  `view_to_scene_space` and `reproject_scene_space` (`include/utility/space_conversion.glsl`), `lod_depth_tex` and
  `is_lod_terrain` (`include/misc/lod_mod_support.glsl`), `taau_render_scale` and `hand_depth` (`include/global.glsl`),
  the `TAA` option, and the custom uniforms `view_res`, `view_pixel_size` and `taa_offset`. A rename fails loudly when
  the pack is compiled. It also needs `composite90` and `colortex20` to stay unused by Photon, which uses
  `composite` to `composite22` and `colortex0` to `colortex16` today; if Photon takes either, move ours and tell
  WynnVista the new buffer (`FogModels.Photon.resolvedDistanceBuffer` there).
- It needs an Iris with more than 16 colour buffers. WynnIris 1.2.2 has 32; Photon itself uses `colortex16` with Voxy.
- Cost: the buffer is 2 bytes a pixel, and Iris keeps two textures for a buffer a pass both reads and writes: 8 MB at
  1920x1080, 33 MB at 3840x2160. The pass reads the depth of nine pixels (and the LOD mod's) and the history once. In
  WynnVista's fixture client (1920x1080, Voxy, RTX 4070, 2026-11-24) its GPU time could not be told from none: the
  frame before WynnVista's effects took 4.90 ms with the pass and 5.34 ms with its stubs set aside, medians of 22
  batches of 100 frames each, in runs that range over 0.5 ms by themselves.
- To test it, use WynnVista's fixture client with `work/` linked in as the shader pack (see "Effect edges under a
  shader pack" in WynnVista's `docs/WORLD_EFFECTS.md`).

## How it works

- `patches/` holds unified diffs, which only the build reads. Their parser and applier are the
  `wynnphoton.gradle.patch` package in `buildSrc/`, with tests in `buildSrc/src/test`. Gradle doesn't run buildSrc's
  tests itself, so the root build's `patchToolTest` task compiles that package again and runs them as part of
  `gradlew build`. The package must need nothing but the JDK.
- `bundle-format/` holds `BundleFormat`, the layout of the jar's `wynn_photon/` folder. Both `buildSrc/` and the mod
  compile it, so the build writes the bundle and the mod reads it with the same names.
- `generatePatchBundle` applies `patches/` to the base pack, adds the `wynn-photon.properties` marker, and has
  [ShaderPatcher](https://github.com/EuphoriaPatches/ShaderPatcher) diff the Photon zip against the result. The jar
  gets that one binary patch, `wynn_photon/wynn-photon.patch`, a manifest, and the SHA-256 of every file in Photon's
  `shaders/` folder. ShaderPatcher checks that the patch reproduces the patched pack before it writes it.
- In game, the mod's own scan finds copies of Photon by those hashes and explains the ones it can't use.
  ShaderPatcher, nested in the mod jar, then checks the whole base pack against the patch, applies it, checks the
  result and moves the finished folder into place. It compares the whole pack, so a copy with files added, changed
  or removed outside `shaders/` is rejected too.
- `shaderpatcher_version` in `gradle.properties` sets the library version for both sides: `buildSrc/` generates the
  patch with it and the mod nests the same version to apply it.
- `patchedPackZip` writes the patched pack as a ready-to-use shader pack zip,
  `build/distributions/photon_v1.4_WynnPhoton_<version>.zip`, for test setups (such as other mods' dev clients) that
  skip the patcher. It has no `wynn-photon.properties` marker, so the mod never treats it as one of its own packs. The
  publish workflow attaches it to the GitHub release only: the Photon licence doesn't allow the patched pack on
  Modrinth or CurseForge.
- The mod runs from Fabric's `preLaunch` entrypoint. The pack and `iris.properties` are ready before Iris reads them,
  and no Minecraft classes are loaded.

## License

Photon is © SixthSurge under the [Photon Shaders License Agreement](https://github.com/sixthsurge/photon/blob/main/LICENSE).
It allows modifying Photon and redistributing modified portions. It does not allow redistributing them on
platforms that pay creators (such as Modrinth or CurseForge) without SixthSurge's written permission. The patches
in this repository contain portions of Photon. Ask before publishing this mod on those platforms.

Wynn-Photon's own code is under the [MIT License](../LICENSE). That does not cover the portions of Photon in the
patches.
