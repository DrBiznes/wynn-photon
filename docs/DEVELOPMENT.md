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
- The harness is the `lighttest` source set (`src/lighttest/`). Only the `lightTest` run loads it, and it is never
  part of the mod jar.

### Updating to a new Photon release

1. Make sure `work/` is saved (`gradlew makePatches`), then update `photon_pack_name`, `photon_display_name`,
   `photon_url` and `photon_sha512` in `gradle.properties`. The URL and SHA-512 are listed by
   `https://api.modrinth.com/v2/project/photon-shader/version`.
2. Run `gradlew applyPatches -Pforce`. Hunks still apply if their context lines are unchanged, even if they moved. A
   hunk whose context changed fails with the file name and hunk number. Fix it by hand in `work/`. Applying the old
   patch to the new base by hand is easiest.
3. `gradlew makePatches`, then bump `mod_version`.

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
