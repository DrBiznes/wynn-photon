# Changelog

All notable changes to Wynn-Photon are listed here. Each version names the Photon release its patches apply to.

## 0.1.0 (2026-11-16), for Photon v1.4

First release.

### Shader changes

- **Redstone torches light like Wynncraft's candle sconces.** Lit redstone torches (standing, wall and held in hand)
  now have their own light ID, `10079`. Their light is warm fire orange, slightly dimmer than a lantern, instead of
  red. This applies to both colored lighting (`light_colors.glsl`) and Photonics (`ph_lights.json`).
- Only the flame and the wax of the torch glow. The dark metal holder no longer does.
- Colored lights (`COLORED_LIGHTS`) are now on by default, so the sconce light shows without changing settings.
- Pink modded lights (Simply Light, Luminax) now use the magenta slot, which frees `10079` for the torches.

### Mod

- When the game starts, the mod finds an unmodified Photon v1.4 in `shaderpacks`, as a zip or a folder under any
  name. It then builds `photon_v1.4 + WynnPhoton_0.1.0` from it.
- If Iris was using Photon or an older Wynn-Photon pack, the mod switches Iris to the new pack and copies your
  shader settings over.
- Older Wynn-Photon packs are deleted when a new one is built. Folders the mod didn't create are never touched.
- Two settings in `config/wynn_photon.properties`: `selectPatchedPack` and `deleteOldVersions`.
