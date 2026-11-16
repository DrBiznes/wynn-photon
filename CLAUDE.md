# Wynn-Photon

Fabric 1.21.11 mod that patches the Photon v1.4 shader pack at launch, Euphoria Patcher style. The mod ships only
unified diffs (`patches/`), never Photon itself. See README.md for the user-facing description.

## Layout

- `patches/` holds one unified diff per changed Photon file, mirroring Photon's paths. It is committed.
- `work/` is the editable Photon: base + patches. It is gitignored.
- `.photon/` holds the downloaded Photon zip, its extracted copy (`base/`) and the `work.sha256` snapshot. It is
  gitignored.
- `patcher-core/` is the patch format, parser and applier. It is compiled into both `buildSrc/` and the mod, so the
  build and the game apply patches with the same code. Keep it free of Gradle, Minecraft and library dependencies.
- `buildSrc/` holds the Gradle tasks `DownloadFileTask`, `ApplyPatchesTask`, `MakePatchesTask` and
  `GeneratePatchBundleTask`. It uses java-diff-utils to generate diffs.
- `src/main/java/com/wynnphoton/` is the mod. It runs from the `preLaunch` entrypoint and loads no Minecraft classes.
  `PhotonInstaller` finds the base pack by SHA-256 of its `shaders/` files, builds the patched pack, writes the
  `wynn-photon.properties` marker and updates `config/iris.properties`.
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

## Rules and gotchas

- Gradle runs on Java 25, because Loom 1.19 requires it (`gradle/gradle-daemon-jvm.properties`). The mod compiles
  with `release = 21`.
- In a sandboxed shell, Gradle can fail with "Unable to establish loopback connection". Run it with
  `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/nonexistent-udsock`. Never put this in the project.
- Patch files must keep LF line endings and trailing whitespace (`.gitattributes`, `.editorconfig`). A blank context
  line is a single space.
- Only text files can be patched. Binary changes, such as textures, are not supported yet.
- Bump `mod_version` in `gradle.properties` for each release. The patched pack's folder name includes it.
- Block and item IDs: `10079` was Photon's pink light slot. It is now the Wynncraft candle sconce (lit redstone
  torch), and pink was merged into `10078`. Before taking another ID, check `block.properties`, `item.properties`,
  `light_colors.glsl` and `ph_lights.json`.
- Photon's license does not allow publishing derivatives on Modrinth or CurseForge without SixthSurge's written
  permission. Don't set up publishing to those platforms unless the user confirms permission was granted.

## Local setup

The user plays in the Modrinth profile "World of Wynncraft Voxy", which uses WynnIris, an Iris fork. Its
`shaderpacks/photon_v1.4_personal` is the hand-edited pack the first patches were taken from.
