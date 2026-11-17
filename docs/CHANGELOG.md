# Changelog

All notable changes to Wynn-Photon are listed here. Each version names the Photon release its patches apply to.

## 0.7.0 (unreleased), for Photon v1.4

Brightness: dark places keep their detail. The cave check and overall brightness from roadmap step 6. Minimum
light is checked on Wynncraft; the cave check is not yet. Also the full texture audit (roadmap step 9) and its fixes,
dimmer emerald blocks and a faint enchanting-table light, not checked in game yet.

### Shader changes

- **Cave check by skylight alone.** Photon's `biome_cave` (cave fog, extra vignette, darker horizon and clouds)
  multiplied a height ramp below Y 63 by missing skylight at the eye. On Wynncraft the height ramp was always full
  near the sea, so roof and tree shade counted partly as cave, and caves above Y 63 (Sky Islands, Canyon of the
  Lost) never counted. It now uses eye skylight only: full cave at skylight 2 of 15 or less, none from 6 up, with
  Photon's smoothing kept.
- **Photonics sky fill.** With Photonics on, Photon swaps its sky light for Photonics' traced indirect light. That
  light is very weak in shade, forests and under overhangs, so those were much darker than without Photonics, and
  `SKYLIGHT_I` barely changed them. The Photonics path in `diffuse_lighting.glsl` now adds Photon's own sky light
  back, only as far as the traced light falls short of it.
- **Minimum light.** Photon's cave light fades out where a block sees the sky, so sky-lit places at night, such as
  dense cities, and cave roofs far from any light went nearly black. Every surface now gets a small neutral light
  (as strong as Photon's default cave light at the default 0.5×). It fades away smoothly as the other light gets
  brighter than it, so daylight and lit rooms don't change, and it keeps ambient occlusion and face shading. It works
  the same with and without Photonics.
- **Light blocks reach further.** The vanilla lightmap's Contribution Falloff now defaults to Linear (Photon's
  default is Squared), so the light-block fallback is softer at the source and reaches roofs and far walls.
- **Sky Light Strength works without Photonics.** Photon v1.4 reads `SKYLIGHT_I` only in its Photonics code, so the
  slider did nothing without Photonics. It now also scales Photon's own sky light in the Overworld, and with it the
  Photonics sky fill.
- **Lava cauldrons: only the lava glows.** Wynncraft draws every cauldron as a wooden tub, and the lava cauldron
  reuses those wood textures. Photon's lava formula lit any orange-brown pixel above 0.4 saturation, so the whole tub
  glowed, brighter than Wynn's dark lava itself. The gate is now 0.6 saturation. That removes all of the tub's glow and
  none of the lava's, Wynncraft's or vanilla's (from the full texture audit, roadmap step 9).
- **More Wynncraft wood.** Plain pistons and TNT are wooden crates, purpur is dark wooden planks, and water and
  powder-snow cauldrons are the same wooden tub as the cauldron. `piston`, `tnt`, `purpur_block`, `purpur_pillar`,
  `water_cauldron` and `powder_snow_cauldron` move to wood (`10029`). `piston_head`, `moving_piston`, `purpur_slab` and
  `purpur_stairs` move to transparent wood (`10030`).
- **Iron doors are wood.** Wynncraft draws them as cream painted doors with a window, and Photon's metal made them
  look like chrome. `iron_door` moves from transparent metal (`10018`) to transparent wood (`10030`).
- **Emerald blocks glow less.** Their surface glow is about a third of 0.5.0's, so the block matches the faint green
  light it casts. The light itself is unchanged in both light systems.
- **Enchanting tables get a faint purple light.** Wynncraft places them next to item identifiers. They get Photon's
  original purple (`(0.60, 0.10, 1.00) × 2`, Photonics `#9919ff` at intensity 43), on a new Wynn-only ID, `10068`.
  The table's blue inlay glows softly; its stone and gold runes don't. The table is out of Photonics' `"disabled"`
  override and has its own entry, and it lights in hand too. The brown Simply Light and Luminax lights (`10068`) are
  merged into orange (`10070`) to free the slot.

### Settings page

- **Minimum Light** (0-4×, default 0.5×) scales the minimum light. 0 turns it off. 1× was a bit too bright on
  Wynncraft.
- **Photonics Sky Fill** (0-2×, default 1×) scales the sky fill. 1× makes shade as bright as without Photonics, 0
  turns it off.
- **Enchanting Table Glow Strength** (0-3×, default 1×) scales the enchanting table's light and inlay glow. 0 turns
  them off.

### Tooling

- **Lighting test.** `gradlew lightTest` photographs the test world's light test spots under the option sets in
  `testworld/light_test.txt`, with nobody at the keyboard, and writes `build/light-test/report.html` with mean
  luminances. See [DEVELOPMENT.md](DEVELOPMENT.md).
- **Test world.** Seven light test spots: open ground, tree canopy, overhang, windowed room, light-block room,
  torch room and cave mouth.
- **Test world exhibits for the texture-audit fixes.** The dark hall has an 18th cell with a lava cauldron, and cell
  9's enchanting table is relabelled for its new light. The daylight row adds crates (piston, TNT), purpur, water and
  powder-snow cauldrons, and an iron door. The gallery now reaches x 110.
- `mod_version` is 0.7.0.

## 0.6.0 (2026-11-16), for Photon v1.4

Wynncraft's sea level and the light from invisible light blocks: the first half of roadmap steps 6 and 7. Sea
level is checked on Wynncraft.

### Shader changes

- **Sea level is Y 33.** Wynncraft's sea is at Y 33, but Photon's `SEA_LEVEL` defaults to 63. The air fog volume
  starts at `SEA_LEVEL - 24`, so Wynn's coast sat below it. The block-light fog switched to its underground look
  between Y 63 and 43, which covered most of the surface. Clouds and cloud shadows also measure height from
  `SEA_LEVEL`. The default is now 33.0, and 33.0 is added to the option list. The cave check (`biome_cave` in
  `shaders.properties`) still used its own hardcoded 63; 0.7.0 replaces it.
- **Vanilla light fallback under Photonics.** With Photonics on, Photon dropped vanilla block light completely and
  lit blocks only with the lights Photonics traces. Wynncraft lights many caves and rooms with invisible
  `minecraft:light` blocks, which Photonics can't see, so those places were black even at block light 10. The
  Photonics path in `diffuse_lighting.glsl` now mixes the vanilla lightmap back in where Photonics' light is weak.
  It uses the same formula as the colored-light volume (`lpv/blocklight.glsl`), and it follows the same Vanilla
  Lightmap Contribution toggle and falloff option.
- **The fallback fades out in open daylight.** Every Wynncraft path is lined with light blocks, which vanilla barely
  shows during the day, and the fallback drew bright pools on them at noon. On blocks with skylight above about 6
  of 15 it now fades out during the day (`1 - time_midnight`), fully above about 12 of 15. Caves keep it all day and
  everything keeps it at night. The fade applies with and without Photonics.

### Settings page

- **Light Block Strength** (0-3×, default 1×) scales the vanilla light fallback, with and without Photonics.

### Tooling

- **Test world.** The dark hall has a 17th cell with two invisible light blocks (levels 15 and 10).
- `mod_version` is 0.6.0.

## 0.5.0 (2026-11-16), for Photon v1.4

A Wynn-Photon settings page in the shader menu, like Euphoria Patches' page for Complementary, and faintly glowing
chests. Not checked in game yet.

### Settings page

- **Wynn-Photon has its own page** in Photon's shader menu, next to Post-Processing. Its options are in a new
  Wynn-Photon section of `settings.glsl`, with names and tooltips in `lang/en_US.lang`:
  - Emerald Glow, on by default, and Emerald Glow Strength (0.25-3.00×).
  - Chest Glow, on by default, and Chest Glow Strength (0.25-3.00×).
  - Candle Light Strength (0-2×) for the sconces and candelabras.
  - Magma Glow Strength (0-2×) for the magma block and molten rock.
- Each strength scales the block's glow and the light it casts, in the colored-light volume (`light_colors.glsl`) and
  in Photonics. Photonics lights are scaled in `photonics/modifiers/light_modifier.glsl` by the light's block ID, which
  Photonics passes as `light.blockId`, so `ph_lights.json` keeps fixed values.

### Shader changes

- **Chests glow faintly**, so they are easier to spot in dark caves. `chest` and `trapped_chest` leave transparent wood
  (`10030`) for a new Wynn-only ID, `10105`. They keep the wood material and get a faint glow, a little brighter on the
  pale latch and trim. Photonics: a new `chest` light (`#ffd9a6`, 21). Chests are block entities, which Photon leaves
  out of the colored-light volume, so without Photonics they glow but cast no light.

### Tooling

- **Test world.** The dark hall has a 16th cell with a chest and a trapped chest, and the daylight row ends with them
  too.
- Tests check the chest ID and that every option on the Wynn-Photon page is defined, named in `en_US.lang` and, for
  strengths, a slider.

## 0.4.0 (2026-11-16), for Photon v1.4

Roadmap step 5: fixes from the texture scan, which compared the server pack's block textures with Photon's hardcoded
materials, plus the survey's optional leftovers. Not checked in game yet.

### Shader changes

- **Redstone dust no longer glows.** Wynncraft builders use it as blood stains. It sat on the redstone block's ID
  (`10058`), so it glowed and cast red light at any power. It now shades as a plain transparent block (`10080`).
  Photonics: Photonics' own defaults light `redstone_wire` at power 1-15, so the `disabled` override now names the whole
  block instead of only `power=0`.
- **Magma is redder, a little dimmer, and only its cracks glow.** Wynncraft's magma is near-black rock with saturated
  red cracks (average `#53110c`). It moves off the fire and shroomlight ID (`10040`) onto its own, `10067`. Only the
  cracks glow, and the light changes from orange `(1.00, 0.45, 0.10) × 9` to red-orange `(1.00, 0.33, 0.10) × 7`.
  Photonics: a new `magma_block` light (`#ff541a`, 150, down from 193).
- **Quartz is polished stone, not a mirror.** Wynncraft uses quartz as a white building stone. The quartz blocks,
  pillars, bricks, slabs and stairs leave the gems (`10013`) for a new Wynn-only material, `10098`: smoothness cut to
  45 %, like obsidian in 0.3.0, and a stone-like reflectance.
- **The iron block is a little less reflective.** It leaves the metals (`10012`) for a new Wynn-only material,
  `10099`. It stays metal, with its smoothness cut to 70 %. Gold, netherite and the rest of the metals are unchanged.
- **Glowstone casts pale yellow light.** Wynncraft's glowstone is pale yellow (`#f5d79d`), but it shared the lantern's
  orange light (`10035`). It gets its own ID, `10054`, the enchanting table's freed slot, with a pale yellow light at
  the lantern's strength, `(1.00, 0.80, 0.52) × 14`. Its glow is unchanged. Held glowstone lights the same way.
  Photonics: a new `glowstone` light (`#ffcc85`, 300).
- **The beacon casts a pale teal light.** Wynncraft's beacon is teal (`#86d0cb`). The light changes from blue
  `(0.45, 0.73, 1.00)` to a near-white teal, `(0.78, 1.00, 0.96)`, same strength. Photonics: `beacon` is now
  `#c7fff5`.
- Photonics: the beacon, glowstone and magma groups also list their blocks by name with priority 100, so they
  beat Photonics' own defaults for those blocks.
- **Unlit candle props get materials.** Unlit sconces (`redstone_torch:lit=false`, wall too) and unlit candelabras
  (`repeater:powered=false`) get a new Wynn-only material, `10104`. The dark iron holder and stand are metal, and the
  cream wax gets weak SSS. They still let colored light through, as they did before.
- **Wooden props shade as wood.** Wynncraft draws the sticky piston as a wooden crate and the cauldron as a wooden
  tub, and both join wood (`10029`). The daylight detector, which Wynncraft draws as stacks of books on an oak base,
  joins transparent wood (`10030`). (Photon lists `daylight_sensor`, which matches nothing.) The cauldron's own
  textures are brown wood too, so this holds even if Wynncraft's `level`-keyed cauldron models don't load on 1.21.11.
- Black modded lights (Simply Light, Luminax) now use the gray slot, which frees `10067` for magma.

### Default settings

- **Cloud scale defaults to 9.0×, was 10.0×.** Photon's `CLOUDS_SCALE` default in `settings.glsl` changes, so it can
  still be set to any value in the shader menu.

### Tooling

- **Test world.** The dark hall has four new cells: redstone dust (powered and unpowered), magma, glowstone and the
  beacon. The daylight row adds quartz, the iron block next to gold, redstone dust, magma and the unlit candle props.
  The gallery now reaches x = 96.

## 0.3.0 (2026-11-16), for Photon v1.4

The rest of the survey's ranked shortlist (items 7-10), more Wynncraft material fixes, and a dev client with a test
world. Not checked in game yet, except that the dev client starts and loads the test world.

### Shader changes

- **The four-candle candelabra is brighter.** `repeater:delay=4:powered=true` gets its own ID, `10073`, with the
  sconce's look and a lantern-strength light, `(1.00, 0.45, 0.12) × 14`. Photonics: a new `four_candle_candelabra`
  light (`#ff7219`, 300). The sconce's Photonics entries now name delays 1-3 only.
- **Murky water is glossy.** The vine state Wynncraft draws as murky water gets a new Wynn-only material, `10097`: a
  smooth, reflective puddle surface with no SSS.
- **Two-block-tall crops no longer split when they sway.** Several Wynncraft crop ages are two stacked quads in one
  model (y -1..15 and 15..31). Photon only moved each quad's top edge, so the seam at y = 15 opened up. Small plants
  now sway by each vertex's height in its block (from Iris's `at_midBlock`), in both the gbuffers and the shadow pass,
  so stacked quads move together. Vanilla plants look the same as before.
- **The enchanting table loses its purple light.** Wynncraft draws it as a dark stone table with a slate-blue top and
  nothing that glows. It leaves the light range (`10054`). Photonics: `minecraft:enchanting_table` joins the
  `disabled` override, and Photon's `enchanting_table` light group, now empty, is removed. (An empty group makes
  Photonics reject the pack's whole light list.)
- **Emeralds glow faintly.** The emerald block gets its own ID, `10076`. It keeps the gem shine, glows a faint green
  and casts a faint green light, `(0.45, 1.00, 0.35) × 4`. Emerald ore (`10075`, deepslate included) glows only in
  its green specks and casts a very faint light (`× 1.5`). Photonics: new `emerald_block` (`#73ff59`, 86) and
  `emerald_ore` (`#73ff59`, 32) lights.
- **Nether bricks are no longer glossy.** Wynncraft draws nether bricks as dark stone bricks for town streets.
  Photon gave them obsidian's smooth, reflective specular (`10011`). The bricks, their cracked and chiseled variants,
  and their stairs and slabs now shade as plain stone.
- **Obsidian is much less reflective.** Wynncraft uses it as floor stone (Magma Heights) as well as volcanic rock.
  It keeps a faint, rough sheen: its smoothness is cut to 45 % of Photon's.
- **Iron trapdoors are no longer reflective.** They move from transparent metal (`10018`) to plain transparent blocks
  (`10080`).
- Light blue and blue modded lights (Simply Light, Luminax) now use the cyan slot, which frees `10075` and `10076` for
  the emeralds. Green modded lights now use the lime slot, which frees `10073` for the four-candle candelabra.

### Tooling

- **Dev client.** `gradlew dev` starts a Loom dev client with Sodium, WynnIris, Photonics, Voxy, Fabric API and Mod
  Menu, straight into the test world. `work/` is linked in as the selected shader pack, so edits show after pressing
  R. The mod also builds its own patched pack there from the unmodified Photon zip. Everything lives in `run/`.
- **Test world.** A creative superflat world, "Wynn-Photon Test", whose datapack builds a gallery of every block
  Wynn-Photon changes: a daylight row for materials and a dark hall with one light per cell. It carries the
  Wynncraft server pack that Minecraft cached when you joined Wynncraft. `gradlew installTestWorld` puts it in your
  own instance instead. See `testworld/README.md`.
- **Consistency tests.** The tests now check that every Photonics light group has blocks and that no block entry
  sits on two `block.properties` lines (Iris keeps the first).

## 0.2.0 (2026-11-16), for Photon v1.4

Items 1-6 of the survey's ranked shortlist (`docs/research/wynn-rp-blocks.md`). The candelabras, sconces, spike traps
and vine blocks are checked in game.

### Shader changes

- **Lit candelabras light like candles.** Powered repeaters, which Wynncraft draws as lit candelabras, move from the
  red redstone light (`10038`) to the candle sconce (`10079`): warm fire-orange light, and only the flame and wax
  glow.
- **Photonics:** the sconce group now lists lit redstone torches, wall torches and powered repeaters by name with
  priority 100, so it beats Photonics' own red `redstone` defaults for those blocks.
- **Spike traps stop glowing.** All comparator states, which Wynncraft draws as floor spike traps, are now
  transparent metal (`10018`) with no light. Photonics: `minecraft:comparator` is added to the `disabled` override.
- **Vine custom blocks get their own materials.** Wynncraft draws some vine states as other things, and each now has
  its own material: the iron chain becomes transparent metal (`10018`), the rope gets weak SSS (`10015`), the dead
  leaves are leaves (`10005`), and cattails, dead grass and flowers, and hanging crops become small plants (`10002`).
  Real vines and the murky-water state stay strong SSS (`10014`).
- **Molten rock glows.** Old wooden double slabs (`petrified_oak_slab:type=double`), which Wynncraft draws as molten
  rock, get a new ID, `10077`. Only the red-orange embers glow, and they cast a dim ember light,
  `(1.00, 0.30, 0.08) × 3`. Photonics: a new `molten_rock` light, `#ff4c14`, intensity 64.
- The single slabs (`petrified_oak_slab` top and bottom) are Wynncraft dark cobblestone slabs. They lose the wood
  material and now shade as plain stone.
- **Wrong lights removed.** The spawner (a dark cage in Wynncraft) is now transparent metal with no pink light.
  Powered activator and detector rails (wooden track) lose the red redstone light.
- **Voxy:** unmapped blocks now get material 0 at LOD, as they do up close. Photon's unsigned subtraction wrapped them
  around to 255, so distant stone, dirt and most other Wynn terrain shaded differently from nearby terrain.
- Purple modded lights (Simply Light, Luminax) now use the magenta slot, which frees `10077` for molten rock.

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
