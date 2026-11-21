# Wynn-Photon

Fabric 1.21.11 mod that patches the Photon v1.4 shader pack at launch, Euphoria Patcher style. The mod ships only
unified diffs (`patches/`), never Photon itself. See README.md for the user-facing description.

## Layout

- `patches/` holds one unified diff per changed Photon file, mirroring Photon's paths. It is committed.
- `work/` is the editable Photon: base + patches. It is gitignored.
- `.photon/` holds the downloaded Photon zip, its extracted copy (`base/`) and the `work.sha256` snapshot. It is
  gitignored.
- `bundle-format/` holds only `BundleFormat`: the layout of the jar's `wynn_photon/` folder and the marker file's
  name. It is compiled into both `buildSrc/` and the mod, so the build writes the bundle and the mod reads it with
  the same names. Keep it free of Gradle, Minecraft and library dependencies.
- `buildSrc/` holds the Gradle tasks `DownloadFileTask`, `ApplyPatchesTask`, `MakePatchesTask` and
  `GeneratePatchBundleTask`. Its `wynnphoton.gradle.patch` package is the unified-diff parser and applier for
  `patches/`; only the build uses it, and it must need nothing but the JDK, because the root build's `patchToolTest`
  task compiles it again to run its tests (`buildSrc/src/test`) with `gradlew build`. It uses java-diff-utils to
  generate diffs. `GeneratePatchBundleTask` applies `patches/` and has the ShaderPatcher library
  (`com.euphoriapatches:shaderpatcher`) turn the result into the one binary patch the jar ships,
  `wynn_photon/wynn-photon.patch`. The `wynn-photon.properties` marker is part of that patch.
- `src/main/java/com/wynnphoton/` is the mod. The installer runs from the `preLaunch` entrypoint and loads no
  Minecraft classes. Only `client/` (the `client` entrypoint and the install toasts) touches Minecraft; it gets
  results through `Notices`. Iris is reached by reflection (`IrisReloader`), so the mod runs without it.
  `PhotonInstaller` finds the base pack by SHA-256 of its `shaders/` files, has ShaderPatcher (nested in the jar)
  verify the whole pack and build the patched one, and updates `config/iris.properties`. The mod never applies
  unified diffs itself. `shaderpatcher_version` in `gradle.properties` sets the library version for both the mod
  and `buildSrc/`.
- `docs/CHANGELOG.md` has one entry per version. `docs/PROJECT_PLAN.md` holds the roadmap and brainstorm; research write-ups go in `docs/research/`.

## Making shader changes

1. `gradlew applyPatches` rebuilds `work/`. It refuses to overwrite edits that aren't in `patches/` yet; pass `-Pforce`
   only to discard them on purpose.
2. Edit files in `work/`, never `patches/` directly. Mark every edit with a `Wynn-Photon:` comment in the shader
   file.
3. `gradlew makePatches` regenerates `patches/` and checks the round trip.
4. `gradlew build` builds the jar and runs the tests, which patch the real downloaded Photon zip.
5. Add the change to `docs/CHANGELOG.md` under the upcoming version.

`gradlew installDevPack` copies `work/` into the user's shaderpacks folder, which is set in `local.properties`, for
testing in game.
`gradlew dev` starts a Loom dev client (Sodium, WynnIris, Photonics, Voxy, all in `run/`) straight into the test
world, with `work/` junction-linked as the selected shader pack, so edits show after pressing R. Never delete
`run/shaderpacks/photon_v1.4 + WynnPhoton-dev` recursively: it is a link to `work/`. Dev-only mods are
`modLocalRuntime` from Modrinth's Maven; pin a Modrinth version ID when a version number is shared across Minecraft
versions (Voxy, Mod Menu). Wynncraft's CDN refuses downloads outside the game, so the world copies the pack Minecraft
cached in an instance's `downloads/`.
`gradlew lightTest` photographs the test world's light test spots under the option sets in
`testworld/light_test.txt`, unattended, and writes `build/light-test/report.html` with mean luminances (see "The
lighting test" in `docs/DEVELOPMENT.md`). Its harness is the dev-only `lighttest` source set.
`gradlew installTestWorld` also installs the "Wynn-Photon Test" gallery world and selects the dev pack in Iris (see
`testworld/README.md`). Add an exhibit there for every new block change.

## Rules and gotchas

- Gradle runs on Java 25, because Loom 1.19 requires it (`gradle/gradle-daemon-jvm.properties`). The mod compiles
  with `release = 21`.
- In a sandboxed shell, Gradle can fail with "Unable to establish loopback connection". Run it with
  `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/nonexistent-udsock`. Never put this in the project.
- Patch files must keep LF line endings and trailing whitespace (`.gitattributes`, `.editorconfig`). A blank context
  line is a single space.
- Only text files can be patched. The jar's ShaderPatcher patch could carry binary changes, such as textures, but
  `patches/` and `makePatches` are text-only, so they are not supported yet.
- ShaderPatcher compares the whole base pack, not just `shaders/`. A Photon copy with anything added, changed or
  removed is rejected (`Rejection.EDITED_OUTSIDE_SHADERS` when only files outside `shaders/` differ).
- Bump `mod_version` in `gradle.properties` for each release. The patched pack's folder name includes it.
- Block and item IDs: `10079` was Photon's pink light slot. It is now the Wynncraft candle sconce (lit redstone
  torch and repeater candelabra), and pink was merged into `10078`. `10077` (purple) is now Wynncraft molten rock, and
  purple was merged into `10078` too. `10075`/`10076` are emerald ore/block, and light blue and blue were merged
  into `10074`. `10073` (green, merged into `10072`) is the four-candle candelabra, and `10097` is murky water.
  `10067` (black, merged into `10066`) is the magma block, `10068` (brown, merged into `10070`) is the enchanting
  table, `10054` (the old enchanting table) is glowstone, `10098` is
  quartz, `10099` is the iron block, `10104` is unlit candle props and
  `10105` is chests. A non-light Wynn ID that light should pass
  through must also be listed in `is_transparent_block` in `voxelization.glsl`, as `10104` is.
  Before taking another ID, check `block.properties`, `item.properties`,
  `light_colors.glsl` and `ph_lights.json`.
- Wynn-Photon changes terrain only: blocks, block lights, block materials and Voxy. WynnIris (an Iris fork) already
  handles Wynncraft's entity, item, text and sky effects: glints, translucency, alpha-254/251 emissive and shadeless
  pixels, skyboxes, fog. Don't edit Photon's entity, hand or text programs, don't reuse colortex11-13, and keep
  "photon" in the pack name. See "Scope" in `docs/research/wynn-rp-blocks.md`.
- Every light change updates both the colored-light volume (`block.properties` + `light_colors.glsl`) and Photonics
  (`ph_lights.json`). Photonics' built-in defaults list `repeater`, `comparator`, `redstone_torch` and
  `enchanting_table` by name. To remove one of those lights, also add the block to the `"disabled"` override in
  `ph_lights.json`.
- `docs/research/wynn-rp-blocks.md` holds the survey and the ranked build list.
- Wynn-Photon's own shader options live in the Wynn-Photon section of `settings.glsl` and on the `screen.wynn_photon`
  page in `shaders.properties`, named in `lang/en_US.lang`; strengths (`_I`) go in `sliders` too. Renaming or
  removing an option (ours or Photon's) needs a rule in `src/main/resources/wynn_photon/settings_conversions.txt`
  so players keep their settings. A strength that
  scales a light also needs a `case` in `photonics/modifiers/light_modifier.glsl`, keyed by block ID.

## Local setup

The user plays in the Modrinth profile "World of Wynncraft Voxy", which uses WynnIris, an Iris fork. Its
`shaderpacks/photon_v1.4_personal` is the hand-edited pack the first patches were taken from.
