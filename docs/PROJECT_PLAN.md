# Wynn-Photon project plan

Make Photon look right *for Wynncraft*. Wynncraft's resource pack redraws many vanilla blocks as something else, such
as redstone torches as candle sconces. Photon still lights and shades them as the vanilla block. Wynn-Photon fixes
that, one small labelled patch at a time.

## Principles

- **Wynncraft first.** Every change should be explained by what the Wynncraft resource pack (or a popular Wynn add-on
  pack) actually draws. "Photon would look nicer" alone is not enough.
- **Small, labelled patches.** Mark each edit with a `Wynn-Photon:` comment. Prefer moving a block to a different
  Photon category over writing new shader code. Small patches survive Photon updates.
- **Prove it in game.** Test each change with `installDevPack` in a real Wynncraft area before it ships. Note where it
  was checked.
- **Record it.** Every shipped change gets an entry in [CHANGELOG.md](CHANGELOG.md).
- **Terrain only. WynnIris owns the rest.** [WynnIris](https://github.com/clptvn/WynnIris) already brings
  Wynncraft's resource-pack shader effects into shader packs: glints, translucency, alpha-254/251 emissive and
  shadeless item pixels, skyboxes, skins, text effects and fog. Wynn-Photon changes blocks, block lights, block
  materials and Voxy. It doesn't touch Photon's entity, hand or text programs, colortex11-13, or the "photon" in the
  pack name. See "Scope" in [research/wynn-rp-blocks.md](research/wynn-rp-blocks.md).
- **Every light works with and without Photonics.** A light change updates both the colored-light volume
  (`block.properties` + `light_colors.glsl`) and Photonics (`ph_lights.json`). Removing a light that Photonics'
  defaults list, such as `comparator` or `repeater`, also needs a by-name entry in Photon's `"disabled"` override.
- **Don't redistribute anyone's assets.** Wynncraft's textures and models stay out of this repo. Research notes may
  describe them and name files, but never copy them.

## Status

- **0.1.0 (done):** the patcher mod, the patch workflow and the redstone torch → candle sconce light.
- **Survey (done, 2026-11-16):** [research/wynn-rp-blocks.md](research/wynn-rp-blocks.md). Its ranked shortlist is
  the build list for steps 2-4.
- **0.2.0 (done):** shortlist items 1-6. Candelabras, sconces, spike traps and vines are checked in game.
- **0.3.0 (done):** shortlist items 7-10, emeralds, nether bricks, obsidian, iron trapdoors, the dev client
  (`gradlew dev`) and the test world ([testworld/README.md](../testworld/README.md)). See [CHANGELOG.md](CHANGELOG.md).
  Not checked in game yet.
- **Texture scan (done, 2026-11-16):** the average colour of the server pack's 8-bit block textures was compared
  with Photon's hardcoded materials. It produced roadmap step 5. The full audit of all 459 textures (step 9) is done
  too, and its build list is in step 9.
- **0.4.0 (done):** roadmap step 5, the texture-scan fixes and the survey's optional leftovers. See
  [CHANGELOG.md](CHANGELOG.md). Not checked in game yet.
- **0.5.0 (done):** a Wynn-Photon settings page in the shader menu (emerald and chest glow toggles and strengths,
  candle and magma strengths) and faintly glowing chests. See [CHANGELOG.md](CHANGELOG.md). Not checked in game yet.
- **0.6.0 (done):** sea level 33, and a vanilla light fallback so Wynncraft's invisible light blocks light caves
  under Photonics, faded out on open ground by day (steps 6 and 7, first half). Sea level is checked on Wynncraft.
- **0.7.0 (done, not released yet):** brightness and the full texture audit. The cave check uses skylight alone,
  every surface gets a minimum light, light blocks reach further, `SKYLIGHT_I` works without Photonics, and Photon's
  sky light fills in under Photonics. Adds the lighting test (`gradlew lightTest`). From the audit: lava cauldron tubs
  stop glowing, pistons, TNT, purpur, cauldrons and the iron door shade as wood, emerald blocks glow less, and
  enchanting tables get a faint purple light.
- **Block work is done.** Steps 1-7 and 9 are closed.
- **Next:** region atmosphere (step 8) and mod and project polish (step 10). Nothing else is open.

### Done so far

- [x] Patcher mod, patch workflow, `patcher-core` shared by the build and the game (0.1.0)
- [x] Survey of the Wynncraft resource pack (roadmap step 1)
- [x] Candle sconce: redstone torches, standing, wall and in hand (0.1.0)
- [x] Lit candelabras → candle light; four-candle candelabra on its own brighter ID (0.2.0, 0.3.0)
- [x] Spike traps (comparators) → metal, no light (0.2.0)
- [x] Vine carrier split: chain, rope, dead leaves, cattails, dead plants, hanging crops (0.2.0)
- [x] Murky water material, `10097` (0.3.0)
- [x] Molten rock embers and light; dark cobblestone slabs out of wood (0.2.0)
- [x] Wrong lights removed: spawner, activator and detector rails, enchanting table (0.2.0, 0.3.0)
- [x] Voxy unsigned-wrap fix (0.2.0)
- [x] Two-block-tall crop sway (0.3.0)
- [x] Emerald block and ore glow (0.3.0)
- [x] Nether bricks → plain stone; obsidian less reflective; iron trapdoors not reflective (0.3.0)
- [x] Dev client (`gradlew dev`) and test world (0.3.0)
- [x] WynnIris boundary and material ID ranges settled
- [x] In-game check of the 0.3.0 changes in the test world and on Wynncraft
- [x] Redstone dust off; magma, glowstone and beacon lights retuned; quartz and iron block less reflective (0.4.0)
- [x] Survey leftovers: crate, cauldron and book stacks to wood; unlit candle props metal and wax (0.4.0)
- [x] In-game check of the 0.4.0 changes
- [x] Wynn-Photon settings page; chests glow faintly (0.5.0)
- [x] Sea level Y 33 (0.6.0, checked on Wynncraft)
- [x] Vanilla light fallback under Photonics, with Light Block Strength and a daytime fade on open ground (0.6.0)
- [x] In-game check of the light fallback: caves, paths by day and night, cave mouths
- [x] Cave check by skylight alone, minimum light, light-block reach, `SKYLIGHT_I` fix, Photonics sky fill (0.7.0)
- [x] Lighting screenshot test, `gradlew lightTest` (0.7.0)
- [x] Full texture audit (step 9) and its fixes: lava cauldron, more wood, iron door, emerald glow, enchanting table
  light (0.7.0)
- [ ] Region atmosphere (step 8)
- [ ] Mod and project polish (step 10)

## Roadmap

### 1. Survey the Wynncraft resource pack (done)

Result: [research/wynn-rp-blocks.md](research/wynn-rp-blocks.md). It has the block table, the lights table (LPV and
Photonics), findings on every brainstorm lead, the WynnIris boundary, and a ranked shortlist.

Agents read the Wynncraft server pack and list every block and light source whose look no longer matches its vanilla
material. For each one they propose a Photon change. The output is a research doc, not patches. We then pick what to
build.

- **Input:** the latest server pack Minecraft cached in the profile's `downloads/` folder. `downloads/log.json` maps
  each `rp-cdn.wynncraft.com/PRODUCTION_<hash>.zip` to its folder. The zip has a deliberately broken central
  directory, so `unzip` and `java.util.zip.ZipFile` choke on it. Read it by walking its local file headers instead.
- **What the pack overrides (as of the 2026-11-07 pack):** 48 vanilla blockstates, 480 block textures, 224 block
  models, about 4,000 item models, 869 skins, and 21 core shaders plus 41 shader includes.
- **Output:** `docs/research/wynn-rp-blocks.md`. It has one row per block or block state: what Wynncraft draws there,
  the Photon category it's in today, the proposed category or light, and a confidence level. Lights get their own
  table with proposed color and brightness.

### 2. Material remaps (done, 0.2.0 and 0.3.0)

From the survey: `comparator` spike traps → transparent metal; the vine carrier split (chain, rope, dead plants,
cattails, hanging crops, dead leaves); stone `petrified_oak_slab` halves unmapped; the Voxy unsigned-wrap fix in
`voxy_opaque.glsl:19`.

Move blocks between Photon's existing categories in `block.properties`. Examples: something Wynn draws as metal moves
to "Metals", something drawn as wood or foliage gets wood specular or subsurface scattering, and a fake ore drawn as
plain stone loses its glow. These changes need no shader code.

### 3. Light sources (done, 0.1.0 to 0.3.0; glowstone and beacon moved to step 5)

Give each remodelled light the color and strength of what Wynncraft actually draws, the way 0.1.0 handles the torch.
This covers colored lighting (`light_colors.glsl`), Photonics (`ph_lights.json`) and handheld light
(`item.properties`). Every light ships both halves. From the survey:
- lit candelabras (`repeater:powered=true`) → candle light;
- spike traps, spawners and wooden rails lose their wrong light;
- molten rock gets a dim ember light;
- the four-candle candelabra gets its own brighter light (0.3.0), and the enchanting table a faint purple one
  (0.7.0).

### 4. Wynn-only materials (done)

Some Wynn looks fit no Photon category, such as the torch's "only the flame glows" mask. These need new branches in
`material.glsl`. Photon's block ID ranges are full (10001-10096 are all used). The survey answered the ID range question: the mask
is 8 bits, but the colored-light volume caps usable IDs at 127. Use **10064-10077** for Wynn-only lights (the unused
Simply Light/Luminax slots) and **10097-10099, 10104-10127** for Wynn-only non-light materials. Candidates: the
molten-rock embers and the murky-water gloss.

### 5. Texture-scan fixes (done, 0.4.0)

From the 2026-11-16 texture scan, with the user's decisions. Each light change ships its LPV and Photonics halves,
and each block gets a test-world exhibit.

- [x] **Redstone dust stops glowing.** Builders use it as blood stains. `redstone_wire` sits on `10058` (redstone
  block), so it glows and casts red light at any power. It is also listed on `10080`, but Iris keeps the first match.
  Take it off `10058`. Photonics already disables `redstone_wire[power=0]`; check whether the other power levels need
  disabling too.
- [x] **Magma block: a little dimmer, not dim.** Wynncraft's magma is a dark block (`#53110c`), but it should still
  cast a good amount of light. Today's orange ×9 may be a bit bright. Ember-only glow with a molten-rock-strength
  light is too dark. Split it from fire and shroomlight onto its own ID, tune the light down slightly and make the
  surface glow match the dark texture.
- **Lava: tried and reverted.** A deep-red retune (hue window and light colour) was built for 0.4.0, then
  dropped: lava stays as Photon draws it.
- [x] **Quartz gets the obsidian fix.** Quartz is a white building stone in Wynncraft, but "gems" (`10013`) makes it
  a near-mirror. Cut its smoothness the way 0.3.0 did for obsidian.
- [x] **Iron block: a bit less reflective.** It stays metallic. Reduce it less than obsidian and quartz were reduced.
  **Gold stays as is.**
- [x] **Glowstone light.** Pale yellow (`#f5d79d`) instead of the lantern's orange. Needs its own ID, because `10035`
  is shared with lanterns and redstone lamps.
- [x] **Beacon light.** A pale, near-white teal (from Wynncraft's `#86d0cb`) instead of blue.
- **Decided, no change:** the redstone block keeps its glow and light.
- [x] **Optional survey leftovers:** sticky piston crate and cauldron to wood, daylight-detector book stacks to
  transparent wood, and unlit sconces and candelabras on `10104` (iron holder metal, wax weak SSS).
- **Answered:** Photonics' defaults light `redstone_wire` at power 1-15, so the whole block is in `"disabled"`. New
  IDs: magma `10067` (black modded lights merged into gray), glowstone `10054` (the enchanting table's slot), quartz
  `10098`, iron block `10099`.

### 6. Sea level, caves and brightness (done, 0.6.0 and 0.7.0)

Wynncraft's sea is at Y 33 everywhere, but Photon assumes 63. Caves sit far above it (Sky Islands, Canyon of the
Lost) and far below it (the jungles, Fruma), so no single height can mean "cave". Photon is also darker overall than
Wynncraft is meant to look.

**Done (0.6.0):**

- [x] Sea level measured: Y 33.
- [x] Listed what reads `SEA_LEVEL`: the air fog volume (it starts at `SEA_LEVEL - 24`), the block-light fog's
  underground switch (`lpv_fog.glsl`, from `SEA_LEVEL` down 20 blocks), and clouds and cloud shadows.
- [x] `SEA_LEVEL` defaults to 33. Checked on Wynncraft: the coast's fog and haze are much better.

**How Photon decides "cave" today:** `biome_cave` (`shaders.properties`) is `(63 - eyeAltitude) / 13`, clamped,
times missing skylight at the eye. It drives cave fog (`simple_fog.glsl`), extra vignette (`c19_color_grading.fsh`),
a darker horizon and border fog (`d4_deferred_shading.fsh`, `sky.glsl`) and the clouds. On Wynn the height part is
always at its maximum near the sea, so shade from a roof or tree counts partly as cave, and caves above Y 63 never
count. Cave *lighting* (`CAVE_LIGHTING_I`, `diffuse_lighting.glsl`) is separate: it uses each block's skylight only,
so it already works at any height.

**Cave check (0.7.0)**

- [x] Drop the height part of `biome_cave` and use eye skylight alone (full cave at skylight 2 of 15 or less, none
  from 6 up) so roofs and trees don't count. Photon's smoothing is kept.
- [x] Check in a surface town, under a big tree, at a cave mouth, in a deep cave (Fruma or the jungle) and in a high
  cave (Sky Islands or Canyon of the Lost).

**Overall brightness, without auto exposure (0.7.0)**

- [x] Minimum light (Wynn-Photon page, default 0.5; 1.0 was a bit too bright): a small light on every surface that fades out where other
  light is brighter. Built for dense cities at night and cave roofs. Checked on Wynncraft.
- [x] Light-block fallback falloff defaults to Linear, so it reaches further.
- [x] `SKYLIGHT_I` now scales Photon's own sky light too. Stock Photon reads it only under Photonics.
- [x] Photonics sky fill: with Photonics, shade was lit only by Photonics' weak traced indirect light, so
  `SKYLIGHT_I` did little. Photon's sky light now fills in where the traced light is weaker (Photonics Sky Fill,
  default 1.0).
- [x] Defaults settled. With minimum light and the sky fill, Photon's own `CAVE_LIGHTING_I`, `SKYLIGHT_I`,
  `BLOCKLIGHT_I`, `MANUAL_EXPOSURE_VALUE`, `GRADE_BRIGHTNESS` and `MOON_I` keep their defaults, and auto exposure
  stays off. They remain player settings, and `gradlew lightTest` measures any later change.

### 7. Invisible light blocks (done, 0.6.0 and 0.7.0)

Builders use `minecraft:light` all over the map. It has no geometry, so the colored-light volume's voxelisation
never sees it. Today it only lights through the vanilla lightmap.

- [x] Research: what Photon does with vanilla blocklight that has no voxel emitter. Without Photonics,
  `COLORED_LIGHTS_VANILLA_LIGHTMAP_CONTRIBUTION` mixes it in where the colored light is weak. With Photonics it was
  dropped completely, so light-block caves were black. 0.6.0 adds the same fallback to the Photonics path, plus a
  Light Block Strength setting for both.
- [x] Paths: every Wynncraft path is lined with light blocks, and the fallback lit them in bright pools at noon. On
  sky-lit blocks it now fades out by day (fading in from skylight 6, gone by 12) and stays in caves and at night (0.6.0).
- [x] Colour and "both halves": the fallback uses Photon's warm block-light colour, and it covers both light systems,
  so light blocks need no Photonics entry.
- [x] Closed: the fallback stays as built, with the Linear falloff from 0.7.0 and the day fade's skylight window
  (6-12 of 15).
- **Dropped (optional):** tracing light blocks in Photonics. The fallback covers them in both light systems.

### 8. Region atmosphere

Photon picks fog density and colour from the vanilla biome category (arid, snowy, taiga, jungle, swamp, temperate)
and from biome temperature and humidity. Wynncraft's regions don't have to match the biome the server assigns them.

- [ ] Record the biome the server assigns to each region. The wiki's region list: Aelumia Citadel, Auburn Forest,
  Avos Territory, Canyon of the Lost, Corkus Island, Corrupted Plains, Dark Forest, Dernel Jungle, Desert of Almuj,
  Detlas Suburbs, Elkurn Fields, Gateway Passage, Gylia Plains, Highlands, Ice Canyon, Industrial District, Kander
  Forest, Light Forest, Llevigar Plains, Lower and Upper Molten Heights, Maltic Plains, Mistwoods, Nemract Swamp,
  Nesaak Tundra, Nivla Woods, Olux Swamp, Pre-Light Forest, Rymek Mesa, Savannah, Silent Expanse, The Silent Road,
  Sky Islands, Time Valley, Troms Jungle, Wastelands.
- [ ] For each, note what Photon does today (fog, weather variation, sandstorm, aurora) and whether it fits.
- [ ] Map what WynnIris's biome fog and Mist Woods fog already do, so the two don't stack or fight.
- [ ] Propose changes: retuned per-category fog, or new categories keyed on Wynncraft's biomes. Write-up goes in
  `docs/research/`.
- [ ] Check whether rain ever falls on Wynncraft. If not, rain puddles, rainbows and sandstorms are dead code there.

### 9. Full texture audit (done, 2026-11-16)

Result: "Full texture audit" in [research/wynn-rp-blocks.md](research/wynn-rp-blocks.md).

- [x] Decode the ~150 textures the first scan skipped. All 459 block textures decode. Every PNG in the pack has
  broken checksums, which is why standard decoders fail.
- [x] Compare every texture with its vanilla counterpart's colour. New finds: lava cauldrons glow across the whole
  wooden tub; plain pistons, TNT and purpur are drawn as wood; the iron door and farmland may be wood; and the sponge
  is an animated rainbow block.
- [x] Check every block on a hue- or brightness-keyed Photon material. Only the lava cauldron is wrong. Glowstone
  (whole surface glows) and diorite (glossier) need an in-game look.
- [x] Add the results to `docs/research/wynn-rp-blocks.md`, with a ranked list.

**Built (0.7.0):**

- [x] Lava formula saturation gate 0.4 → 0.6, so the wooden lava-cauldron tub stops glowing and lava stays unchanged
  (0.7.0).
- [x] Wood: `piston`, `tnt`, `purpur_block`, `purpur_pillar`, `water_cauldron` and `powder_snow_cauldron` → 29;
  `piston_head`, `moving_piston`, `purpur_slab` and `purpur_stairs` → 30 (0.7.0).
- [x] Iron door → 30, a painted door rather than metal (0.7.0).
- [x] Also from this pass: emerald blocks glow less (light unchanged), and enchanting tables get a faint purple light
  on `10068` (0.7.0). This settles shortlist item 10.
- [x] Closed without changes: farmland, glowstone brightness, the sponge and diorite gloss stay as Photon draws them.

### 10. Mod and project polish

- [ ] Show an in-game notice when Photon is missing, not just a log line.
- [ ] A Mod Menu screen for the two config options.
- [x] A GitHub repo (`DrBiznes/wynn-photon`).
- [ ] CI that runs `gradlew build` on every push and pull request.
- [x] Publishing workflow: `.github/workflows/publish.yml` publishes to Modrinth and CurseForge on a GitHub release.
- [ ] Track new Photon releases (see README, "Updating to a new Photon release").

## Brainstorm

These ideas are not prioritized yet. Each one first needs checking against the actual pack.

**Custom block carriers.** *(Surveyed: only `vine` is a real carrier. The others are plain or unused.)* The pack overrides blockstates for `note_block`, `red_mushroom_block`,
`brown_mushroom_block`, `mushroom_stem`, `tripwire` and `tripwire_hook`. Those are the usual blocks servers use to add
custom blocks, one look per block state. Photon's `block.properties` can match block states, so each Wynn custom block
could get its own material.

**Repurposed vanilla blocks.** *(Surveyed: repeater = candelabra, comparator = spikes, old slabs = molten rock and dirt slabs.)* The pack also overrides the infested stone variants, the ores (`coal_ore`,
`emerald_ore`, `redstone_ore`), `iron_bars`, `cauldron`, `daylight_detector`, `sticky_piston`, `bedrock`,
`netherrack`, and old slab ids. Some of these are probably decoration. For example, `redstone_ore:lit` glows red in
Photon today; is that what Wynn draws?

**Plants.** *(Surveyed: 2-block-tall stacked crop quads may shear when they wave.)* The pack overrides crops (`wheat`, `carrots`, `potatoes`, `beetroots`), `vine`, `short_grass`,
`sunflower` and `cobweb`. Check that their waving and subsurface scattering still fit the new models.

**Emissive pixels.** *(Surveyed: alpha 254 = emissive and 251 = shadeless, on item textures only. WynnIris already handles them. Blocks carry no marker.)* Does the pack mark glowing pixels in its textures, for example with an alpha value its core
shaders look for? If it does, Photon could read the same marker and get emission for every Wynn glowing texture at
once. That could be the biggest single win.

**Lost core-shader effects.** *(Surveyed: WynnIris restores them. No terrain core shaders exist.)* The pack ships its own core shaders. Iris doesn't use core shaders while a shader pack
is on, so any world effect Wynn does in them disappears under Photon. List those effects and decide which ones Photon
should recreate.

**Item-model props.** *(Surveyed: every prop is `oak_boat` + `custom_model_data`. Iris matches only `item_model`, so this is out of scope and would be a WynnIris request.)* Wynn builds many decorations from item models on display entities and armor stands, about
4,000 of them. Photon's `item.properties` only matches vanilla item ids. Research whether Iris (or WynnIris) can match
item components or model ids. If not, those props can't get their own lights or materials.

**Entities and NPCs.** *(WynnIris's area.)* Use `entity.properties` for things like glowing eyes or lanterns that mobs and NPCs carry.

**Spell and particle light.** Spells could light their surroundings. This is hard and may need mod-side support;
WynnIris might be a better home for it.

**Voxy LODs.** *(Surveyed: materials apply at LOD, but LODs have no colored light. Fix the unsigned wrap.)* Photon has Voxy shaders (`voxy_opaque.glsl`, `voxy.json`). Make sure material and light changes also
apply to distant terrain, so lights don't pop in or change at the LOD boundary.

**Add-on packs.** *(Surveyed: only WynnLanternRP and Better Leaves touch blocks, and neither needs changes. The sconce is server-pack content.)* The user's stack includes WynnLanternRP, Wynnic Fables & Legends, Better Leaves and Wynnphony.
Find out which pack each remodel actually comes from (the torch model may come from WynnLanternRP). Decide whether
to support popular add-ons, possibly behind a shader option.

**labPBR.** The Wynn pack has no `_s`/`_n` maps for blocks. Photon's hardcoded specular is our main tool. A
companion labPBR resource pack for Wynn blocks is a much bigger alternative.

**Avoid overlap with WynnIris.** *(Done: see Principles and the survey's Scope section.)* WynnIris already adds Wynn-specific features, such as Mist Woods fog and ambience
packs. Check what it covers so the two don't duplicate or fight each other.

## Open questions

- ~~Which ID range is safe for Wynn-only materials?~~ 10064-10077 (lights), 10097-10099 and 10104-10127 (materials).
- Should Wynn-Photon support add-on packs, or only the official server pack? (Moot for now: no add-on needs a
  change.)
- ~~Can Iris target custom item models at all?~~ Only by `item_model`, not `custom_model_data`, and Wynn props use
  the latter. A WynnIris request if ever needed.
- ~~What are Wynncraft's sea and ground levels?~~ Y 33 everywhere.
- ~~Can either light system see `minecraft:light`?~~ No. The vanilla light fallback covers it in both (0.6.0).
- Which vanilla biome does the server assign to each region? (Step 8.)
- ~~Does Photonics' default `redstone` group override Photon's `ph_lights.json` group for `repeater[powered=true]`?~~
  No: candelabras light as candles in game.
