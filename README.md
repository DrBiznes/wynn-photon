# Wynn-Photon

A Fabric mod for Minecraft 1.21.11 that patches the [Photon](https://modrinth.com/shader/photon-shader) shader pack
with tweaks for Wynncraft. It works like [Euphoria Patcher](https://github.com/EuphoriaPatches/EuphoriaPatcher):
the mod ships only *patches*, never Photon itself. When the game starts, it finds an unmodified copy of Photon in
your `shaderpacks` folder and builds a patched copy next to it.

## Current changes

See [docs/CHANGELOG.md](docs/CHANGELOG.md) for the full history.

- **Redstone torches are Wynncraft candle sconces.** Lit redstone torches (standing, wall and held) get their own
  light ID (`10079`). They cast warm fire-orange colored light, a little under a lantern, instead of red.
  Only the flame and the wax glow; the metal holder does not. Colored lights are on by default so this is visible.
  The pink modded-light slot that `10079` used is merged into magenta.

## Using it

1. Install the mod jar (`build/libs/wynn-photon-<version>.jar`) along with Iris.
2. Put **`photon_v1.4.zip`** from [Modrinth](https://modrinth.com/shader/photon-shader/versions) in `shaderpacks`.
   Leave it zipped and unedited. An unzipped, unedited folder also works, under any name.
3. Start the game. The mod creates `shaderpacks/photon_v1.4 + WynnPhoton_<version>/`. If Iris was using Photon or an
   older Wynn-Photon pack, it switches Iris to the new pack and copies your shader settings over.

Patched packs contain a `wynn-photon.properties` marker. A new mod version, or changed patches, rebuilds the pack and
deletes the older Wynn-Photon packs. A folder without the marker is never touched. Your edited Photon copies are
ignored, because only a byte-for-byte match of Photon v1.4's `shaders/` files counts as the base pack.

The mod writes its settings to `config/wynn_photon.properties`:

| Key | Default | Effect |
| --- | --- | --- |
| `selectPatchedPack` | `true` | After building a new pack, switch Iris to it (only if Iris was using Photon or an older Wynn-Photon pack). |
| `deleteOldVersions` | `true` | After building a new pack, delete older Wynn-Photon packs. |

The game log shows what happened. Search it for `Wynn-Photon`.

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
3. To try them in game, `gradlew installDevPack` copies `work/` to
   `<shaderpacksDir>/photon_v1.4 + WynnPhoton-dev`. Select it in Iris and press `R` to reload after each install.
   Set `shaderpacksDir` in `local.properties` (gitignored) using forward slashes:
   ```properties
   shaderpacksDir=C:/Users/you/AppData/Roaming/ModrinthApp/profiles/<profile>/shaderpacks
   ```
4. `gradlew makePatches` writes your edits back into `patches/`. It re-applies the new patches with the same code the mod
   uses in game and fails if they don't reproduce `work/` exactly. Commit `patches/`.
5. `gradlew build` produces the jar and runs the tests. The tests patch the real Photon zip.

`applyPatches` won't overwrite `work/` if it has edits that aren't in `patches/` yet. Run `makePatches` first, or pass
`-Pforce` to discard them.

Only text files can be patched for now. Adding new text files and deleting files both work.

### Updating to a new Photon release

1. Make sure `work/` is saved (`gradlew makePatches`), then update `photon_pack_name`, `photon_display_name`,
   `photon_url` and `photon_sha512` in `gradle.properties`. The URL and SHA-512 are listed by
   `https://api.modrinth.com/v2/project/photon-shader/version`.
2. Run `gradlew applyPatches -Pforce`. Hunks still apply if their context lines are unchanged, even if they moved. A
   hunk whose context changed fails with the file name and hunk number. Fix it by hand in `work/`. Applying the old
   patch to the new base by hand is easiest.
3. `gradlew makePatches`, then bump `mod_version`.

## How it works

- `patcher-core/` holds the patch format and applier. Both the Gradle tasks (`buildSrc/`) and the mod compile it,
  so the patches are applied by the same code in the build and in game.
- `generatePatchBundle` packs `patches/` into the jar under `wynn_photon/`. It adds a manifest and the SHA-256 of
  every file in Photon's `shaders/` folder, which the mod uses to recognize the base pack.
- The mod runs from Fabric's `preLaunch` entrypoint. The pack and `iris.properties` are ready before Iris reads them,
  and no Minecraft classes are loaded.

## License

Photon is © SixthSurge under the [Photon Shaders License Agreement](https://github.com/sixthsurge/photon/blob/main/LICENSE).
It allows modifying Photon and redistributing modified portions. It does not allow redistributing them on
platforms that pay creators (such as Modrinth or CurseForge) without SixthSurge's written permission. The patches
in this repository contain portions of Photon. Ask before publishing this mod on those platforms.

The license for this mod's own code has not been chosen yet.
