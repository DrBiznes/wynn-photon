# Changelog

Every version so far patches Photon v1.4.

## 0.8.0

Initial public release.

**Added**
- Clear install errors. When the Wynn-Photon pack can't be built, the log and a new `shaderpacks/_0WYNN_PHOTON_ERRORS.txt` say what was found, what is needed and how to fix it, with the download link. They tell apart a missing Photon, a broken or half-downloaded zip, Photon unzipped into an extra folder, a newer or older Photon, an edited Photon, and a folder already using the pack's name. The file is deleted once the pack is built.
- No restart needed: after a failed install, Wynn-Photon watches the shaderpacks folder while the game runs. Drop in the Photon zip and the pack is built as soon as the download finishes. If Iris was using Photon or an older Wynn-Photon pack, it switches to the new pack and reloads; otherwise the pack shows up in the shader pack menu.
- Toasts in the top-right corner when the Wynn-Photon pack is installed or updated, or when it can't be built. `showNotifications` in `config/wynn_photon.properties` turns them off.

**Changed**
- Your shader settings carry over to a new Wynn-Photon version even when an option is renamed or removed. They also carry over from your last Wynn-Photon pack when Iris was using a different pack.
- The shaderpacks folder is created if it doesn't exist yet.
- Wynn-Photon's own code is now under the MIT licence. Photon's parts of the patches stay under Photon's licence.

## 0.7.0

Dark places keep their detail, and more Wynncraft blocks shade as what Wynncraft draws.

**Added**
- Minimum Light: every surface gets a small light that fades away where other light is brighter, so dense cities at night and cave roofs far from any light no longer go nearly black. Daylight and lit rooms don't change. "Minimum Light" on the Wynn-Photon page (0-4×, default 0.5×) sets it; 0 turns it off.
- Photonics Sky Fill: with Photonics, shade, forests and overhangs were much darker than without it, because Photonics' traced light is weak there. Photon's own sky light now fills in where the traced light falls short. "Photonics Sky Fill" (0-2×, default 1×) sets it; 0 turns it off.
- Enchanting tables cast a faint purple light, and their blue inlay glows softly. "Enchanting Table Glow Strength" (0-3×, default 1×) sets it; 0 turns it off.

**Changed**
- The cave check (cave fog, extra vignette, a darker horizon and clouds) now goes by how much sky you can see, not by height. Before, roof and tree shade near the sea counted partly as a cave, and high caves such as the Sky Islands and Canyon of the Lost never did.
- Light blocks reach further: the vanilla light's falloff now defaults to Linear, so the light from Wynncraft's invisible light blocks is softer at the source and reaches roofs and far walls.
- Pistons and TNT shade as wooden crates, purpur as dark wooden planks, and water and powder-snow cauldrons as the same wooden tub as the cauldron.
- Iron doors shade as Wynncraft's cream painted doors instead of chrome.
- Emerald blocks glow about a third as much as before, to match the faint light they cast.

**Fixed**
- "Sky Light Strength" now works without Photonics. Photon only read it with Photonics on.
- Lava cauldrons: only the lava glows now, not the whole wooden tub.

## 0.6.0

Wynncraft's sea level, and light from Wynncraft's invisible light blocks with Photonics.

**Added**
- "Light Block Strength" on the Wynn-Photon page (0-3×, default 1×) scales the light from invisible light blocks, with and without Photonics.

**Changed**
- Sea level is Y 33, Wynncraft's sea level, instead of Photon's 63. Fog, haze, block-light fog, clouds and cloud shadows along the coast now sit where they should.
- On open ground the light from invisible light blocks fades out by day, so Wynncraft's paths no longer have bright pools of light on them at noon. Caves keep it all day, and everything keeps it at night.

**Fixed**
- With Photonics, caves and rooms lit only by Wynncraft's invisible light blocks were black. Photonics can't see those blocks, so their vanilla light is now mixed back in where Photonics' light is weak.

## 0.5.0

A Wynn-Photon settings page, and faintly glowing chests.

**Added**
- A Wynn-Photon page in the shader menu, next to Post-Processing: Emerald Glow and Chest Glow (each with a strength, 0.25-3×), Candle Light Strength (0-2×) for sconces and candelabras, and Magma Glow Strength (0-2×) for magma and molten rock. Each strength scales both the glow and the light the block casts, with and without Photonics.
- Chests and trapped chests glow faintly, a little brighter on the latch and trim, so they're easier to spot in dark caves. With Photonics they also cast a faint light.

## 0.4.0

Fixes from comparing Wynncraft's block textures with Photon's materials.

**Changed**
- Magma is redder and a little dimmer, and only its cracks glow, to match Wynncraft's near-black magma.
- Quartz shades as polished white building stone instead of a mirror.
- The iron block is a little less reflective. Gold and the other metals are unchanged.
- Glowstone casts a pale yellow light instead of the lantern's orange, held in hand too.
- The beacon casts a pale teal light instead of blue.
- Unlit sconces and candelabras shade as an iron holder with wax candles.
- Sticky pistons and cauldrons shade as wood (Wynncraft's crate and tub), and daylight detectors as wooden book stacks.
- Clouds default to 9× scale instead of 10×.

**Fixed**
- Redstone dust no longer glows or casts red light. Wynncraft builders use it as blood stains.

## 0.3.0

Brighter candelabras, glowing emeralds and more Wynncraft material fixes.

**Changed**
- The four-candle candelabra casts a brighter, lantern-strength light.
- Emerald blocks glow faintly green and cast a faint green light. Emerald ore glows only in its green specks.
- Murky water has a smooth, glossy surface.
- Nether bricks, which Wynncraft uses as dark street bricks, are no longer glossy.
- Obsidian keeps only a faint, rough sheen.
- Iron trapdoors are no longer reflective.

**Fixed**
- Two-block-tall crops no longer split apart at the middle when they sway.

**Removed**
- The enchanting table's purple light. Wynncraft draws it as a stone table with nothing that glows. (0.7.0 brings back a fainter one.)

## 0.2.0

Candelabras, spike traps, vine blocks and molten rock look like what Wynncraft draws.

**Changed**
- Lit candelabras light like candles, with a warm orange light where only the flame and wax glow.
- Wynncraft's vine blocks each get their own material: the iron chain is metal, the rope is soft, dead leaves are leaves, and cattails, dead plants and hanging crops sway like small plants.
- Molten rock glows in its embers and casts a dim ember light.
- Dark cobblestone slabs shade as stone instead of wood.

**Fixed**
- Spike traps no longer glow red. They shade as metal.
- Spawners and wooden activator and detector rails lose their wrong lights.
- Voxy: distant terrain shades the same as nearby terrain. Most of it shaded differently at a distance before.

## 0.1.0

First version.

**Added**
- Wynn-Photon builds `photon_v1.4 + WynnPhoton_<version>` at launch from an unmodified Photon v1.4 in your shaderpacks folder, as a zip or a folder under any name. If Iris was using Photon or an older Wynn-Photon pack, it switches to the new pack and copies your shader settings. Older Wynn-Photon packs are deleted, and folders Wynn-Photon didn't create are never touched.
- Two settings in `config/wynn_photon.properties`: `selectPatchedPack` and `deleteOldVersions`.
- Redstone torches light like Wynncraft's candle sconces: a warm orange light where only the flame and wax glow, standing, on walls and held in hand.

**Changed**
- Colored lights are on by default, so the sconce light shows without changing settings.
