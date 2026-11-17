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
  Next: check them in the test world, then tune lighting and the rest of the pack for Wynncraft.

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

### 2. Material remaps (cheap wins, next)

From the survey: `comparator` spike traps → transparent metal; the vine carrier split (chain, rope, dead plants,
cattails, hanging crops, dead leaves); stone `petrified_oak_slab` halves unmapped; the Voxy unsigned-wrap fix in
`voxy_opaque.glsl:19`.

Move blocks between Photon's existing categories in `block.properties`. Examples: something Wynn draws as metal moves
to "Metals", something drawn as wood or foliage gets wood specular or subsurface scattering, and a fake ore drawn as
plain stone loses its glow. These changes need no shader code.

### 3. Light sources

Give each remodelled light the color and strength of what Wynncraft actually draws, the way 0.1.0 handles the torch.
This covers colored lighting (`light_colors.glsl`), Photonics (`ph_lights.json`) and handheld light
(`item.properties`). Every light ships both halves. From the survey:
- lit candelabras (`repeater:powered=true`) → candle light;
- spike traps, spawners and wooden rails lose their wrong light;
- molten rock gets a dim ember light;
- the four-candle candelabra and the enchanting table follow once checked in game. Also look for Wynn props that should glow but don't, and vanilla lights that Wynncraft turned
into something that shouldn't glow.

### 4. Wynn-only materials

Some Wynn looks fit no Photon category, such as the torch's "only the flame glows" mask. These need new branches in
`material.glsl`. Photon's block ID ranges are full (10001-10096 are all used). The survey answered the ID range question: the mask
is 8 bits, but the colored-light volume caps usable IDs at 127. Use **10064-10077** for Wynn-only lights (the unused
Simply Light/Luminax slots) and **10097-10099, 10104-10127** for Wynn-only non-light materials. Candidates: the
molten-rock embers and the murky-water gloss.

### 5. Mod and project polish

- Show an in-game notice when Photon is missing, not just a log line.
- A Mod Menu screen for the two config options.
- A GitHub repo with CI that runs `gradlew build`.
- Publishing, which needs SixthSurge's written permission for Modrinth. Asked on Discord 2026-11-16, waiting for a
  reply.
- Track new Photon releases (see README, "Updating to a new Photon release").

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
- Does Photonics' default `redstone` group override Photon's `ph_lights.json` group for `repeater[powered=true]`?
  Check in game with the candelabra change.
