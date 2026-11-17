# Wynncraft resource pack survey: blocks and lights

Roadmap step 1. This note lists what the Wynncraft server resource pack does to vanilla blocks and lights, and which
Photon changes would make Photon light and shade them as Wynncraft draws them. It is research only. Nothing here is
built yet.

- **Pack surveyed:** `PRODUCTION_d2668821b984aff6699b135d829b9cd1603d8db5.zip`, "Wynncraft Resource Pack 2.2 /
  26.3, Nov. 06, 2026". It is cached in the "World of Wynncraft Voxy" profile under
  `downloads/32513eff-4d23-3c67-a8f4-2053d2ac77b7/`. (`options.txt` still names an older preloaded pack,
  `wynntils_preloaded/03ff36a5…`, from 2026-10-09. The server sends the newest one on join.)
- **Photon:** v1.4. "Today" means the 0.1.0 patched pack (`work/`), and equals the base pack except where noted.
- **Paths:** pack paths are relative to the pack root. Photon paths are relative to `shaders/`.

## How the pack was read

The zip's local file headers are zeroed out. Each one is 30 bytes of zeros with no name. The central directory has
the real names, offsets and compressed sizes, but it carries fake CRCs and an uncompressed size of `0x7fffffff`, so
`unzip` and `ZipFile` reject it. To read it, take the name, method, compressed size and local offset from each
central entry, then inflate the data at `offset + 30`. PNGs inside also have broken zlib Adler checksums. Inflate
their IDAT as raw deflate and skip the 2-byte zlib header. All 37,881 entries extract this way, with no duplicate
names.

The pack uses `pack.mcmeta` overlays. On 1.21.11 (pack format 75), `1_21_5`, `1_21_6`, `1_21_9` and `1_21_11` apply,
and `26_1`-`26_3` don't. None of the applying overlays touch blocks. `1_21_11/assets/minecraft/atlases/blocks.json`
empties the block atlas additions, so all `wynn/` and `skins/` textures live in the **items** atlas on 1.21.11.

### Which pack each block change comes from

The enabled packs, from lowest to highest priority, are: the Wynncraft server pack, WynnLanternRP, FancyProfsMats,
Gauntlet Cursor, Better Leaves, Wynnphony Tavern, FA+Player, WynnEdits Dark Theme, Wynnilla UI, Wynnic Fables &
Legends 2.3.1, and WMP Fruma Dam Daytime.

| Pack | Block content |
| --- | --- |
| **Wynncraft server pack** | 48 blockstates, 224 block models (104 under `block/variants/`, 16 under `block/redstone/`, 8 under `block/wall/`), 480 block textures. This includes the **redstone torch candle sconce**, the repeater candelabras and the comparator spikes. |
| **WynnLanternRP** | `lantern` and `torch` blockstates and models (a Blockbench lantern, a cross-plane torch) and `textures/block/torch.png`. It does **not** touch redstone torches. Its `lantern_hanging` model is missing, so hanging lanterns fall back to vanilla. |
| **Better Leaves** ("Bushy Leaves v0.1") | The six classic leaves: a cube plus crossed "bushy" planes (`models/block/leaves_cross*.json`). |
| Everything else | No blockstates, block models or block textures. These are UI, sounds, armor and CIT packs. |

So 0.1.0's sconce is server-pack content, not an add-on.

## Scope: what Wynn-Photon owns, what WynnIris owns

[WynnIris](https://github.com/clptvn/WynnIris) (the profile runs 1.2.2, an Iris 1.10.8 fork) brings Wynncraft's
resource-pack shader effects into **any** shader pack. It does this by rewriting the pack's entity, hand and text
programs as Iris compiles them. I read its source at commit `5bd60bc` ("release: WynnIris 1.2.2"), mostly
`common/src/main/java/net/irisshaders/iris/pipeline/transform/transformer/EntityPatcher.java` and
`VanillaCoreTransformer.java`. Its features, and how far they reach:

| WynnIris feature | Signal it reads | Where (WynnIris source) |
| --- | --- | --- |
| 32 item glints (tint, rainbow, glitch, chrome, …) | vertex colour G = 255, B = 0, R = id | `EntityPatcher.java:82-89`, `IRISW_APPLY_GLINT_FUNC` |
| Translucency | vertex colour G = 254, R = level | `EntityPatcher.java:91-96` |
| Emissive item/entity pixels | texture alpha 254 (not G 251): lifts albedo to the raw texture colour, scaled by the `wynncraftEntityEmissivity` setting (default 100 %) | `EntityPatcher.java:138-143, 1495-1502, 1577-1583` |
| Shadeless pixels | texture alpha 251 | `IRISW_SHADELESS_FORWARD`/`_DEFERRED`, `EntityPatcher.java:1000-1016` |
| Procedural skyboxes | texture G 251 + A 254 | `EntityPatcher.java:116-215`; `pathways/WynncraftSkyboxRenderer.java` |
| Player-skin remap and near-camera fade (Y-encoded models) | model Y ≥ 1024 | `EntityPatcher.java:1150-1195` |
| Neutralising effect and movement tints | vertex colour G = 240/235/58/59/60 | `VanillaCoreTransformer.java:460-480` |
| Text effects, movements and transitions | text colour codes | `pipeline/fallback/ShaderSynthesizer.java`, `pathways/WynncraftTransitionRenderer.java` |
| Mount armour overlay, entity brightness boost in dark skies | alpha 252/250; skybox id | `pathways/WynncraftMountArmorOverlay.java`, `EntityPatcher.java:1424-1425` |
| Biome and Mist Woods fog, text-display brightness floor, ambience packs | — | `pathways/WynncraftBiomeFogRenderer.java`, `mixin/entity_render_context/MixinTextDisplayRenderer.java`, `ambience/` |
| Photon-only fixes | pack name contains "photon" | `pipeline/IrisRenderingPipeline.java:142-149, 337-339, 738, 810` (VFX translucency into colortex13; cloud history reseed on colortex11/12) |
| Voxy and Photonics fixes | — | `pathways/VoxyLodDepth.java`, `pipeline/programs/SodiumPrograms.java` |

**Boundary rules for Wynn-Photon:**
1. **WynnIris owns everything drawn by entities, items, the hand and text.** That covers glints, translucency,
   emissive and shadeless item pixels, skyboxes, skins, movements, text and fog. Wynn-Photon must not detect any of
   these signals itself: alpha 251/252/254, vertex G 235/240/254/255, or Y ≥ 1024. Doing so would apply them twice,
   or fight WynnIris's rewrite of the same programs.
2. **Wynn-Photon owns terrain:** `block.properties`, `material.glsl` for block masks, block lights in
   `light_colors.glsl` and `ph_lights.json`, block waving, and Voxy. WynnIris doesn't touch any of these. The pack
   ships no terrain core shaders and no block texture carries a marker, so there is nothing to share.
3. **Leave Photon's entity, hand and text programs alone.** WynnIris finds its injection points in them by pattern
   (`EntityPatcher` "anchors", with a fallback when none are found). Edits there can break WynnIris silently, so they
   need a test with WynnIris running.
4. **Don't repurpose colortex11, 12 or 13.** WynnIris writes Photon's VFX translucency into colortex13 and clears the
   cloud history in colortex11/12.
5. **Keep "photon" in the patched pack's name** (`photon_v1.4 + WynnPhoton_<version>`). WynnIris only turns on its
   Photon fixes when the name contains it.
6. **Gaps belong upstream.** WynnIris's emissive lifts albedo only, so the pixel is still lit by Photon's lighting
   and gets no bloom or light. Item *movements* are neutralised but not animated in shader-pack programs. Report
   both to WynnIris; don't fix them in Photon.

## Photonics rules

The profile runs Photonics 0.3.4, which reads `ph_lights.json` from the shader pack. Every light change in this doc
must update **both** light systems: the LPV (`block.properties` + `light_colors.glsl`) and Photonics
(`ph_lights.json`). Two facts from the Photonics jar (`ShaderPackLights.class`, `LightRegistry.class`,
`assets/photonics/default_config.json`):

- **Groups work per state.** `*100xx` entries are resolved through Iris's block ID map (`ShaderPackLights` uses
  `IdMap.getBlockProperties`), so state-split lines such as `repeater:powered=true` work in Photonics too.
- **Photonics doesn't use vanilla light levels.** Its light list comes only from its config. No call to the
  block-state luminance getter was found, so a repeater mapped to a light group should be ray-traced. This is
  inferred from the bytecode; check it in game.
- **Photonics' own defaults name several Wynn blocks as red redstone lights:** `repeater[powered=true]`,
  `comparator[powered=true]`, `redstone_torch[lit=true]` (`default_config.json:351-366`), and also
  `enchanting_table` (`:616-620`). So moving a block out of 10038 in `block.properties` is **not enough**:
  - **To remove a light**, also add the block by name to Photon's `"disabled"` override in `ph_lights.json`. Example:
    `"minecraft:comparator"`. Use `{ "value": ..., "priority": 100 }` the way Photon does for
    `redstone_wire[power=0]`.
  - **To recolour a light**, add its ID group to the new override, as 0.1.0 does with `CANDLE_SCONCE_ID`. Then check
    in game that it beats the Photonics default. If it doesn't, add a by-name entry with a priority.
- **Every Photonics light added here needs** a `#define <NAME>_ID` line, an override with `color`, `intensity` and
  `blocks`, and a radius/falloff only if the default (1.1, 1) looks wrong.

## Key facts that shape the proposals

- **The LPV picks emitters by ID only.** Any block mapped to 10032-10079 emits colored light, whatever its vanilla
  light level is (`include/lighting/lpv/floodfill.glsl:6`, `voxelization.glsl:69`). So Photon's light depends only on
  what `block.properties` says. Wynn's lit candelabras are repeaters, which have vanilla light level 0, and they
  already glow **red** today. Retracted spike traps (powered comparators) do too.
- **Waving is per-quad.** `is_top_vertex = uv.y < mc_midTexCoord.y` (`program/gbuffers_all_solid.vsh:159`). Several
  Wynn crops are two stacked 16-px quads (y −1..15 and 15..31). Each quad's top edge moves but the bottom edge of the
  quad above does not, so the plant shears at y = 15.
- **Iris block.properties can match exact block states**, for example `vine:east=true:north=false:...`. That is
  enough to give each "custom block" its own material.
- **Material mask:** 8 bits are stored, but **127 is the real ceiling**. The colored-light voxel map is `r8ui`
  (`shaders.properties:433`), and `voxelization.glsl:123` adds 128 to mark non-full blocks. `floodfill.glsl:104-105`
  treats ≥128 as transparent and masks the value with `&127`. The gbuffers pack the mask with `/255`
  (`program/gbuffers_all_solid.fsh:480-484`) and decode it with `uint(255*x)` (`program/d4_deferred_shading.fsh:339`).
  Details are under "Material ID range" below.

## Block table

**Photon today** gives the `block.properties` ID minus 10000 and what it does in `include/surface/material.glsl`.
"No category" means mask 0: default hardcoded specular, no SSS and no emission. **Confidence** is how sure we are
that the proposal matches what Wynn draws in game. None of these were checked in game yet.

Most evidence paths are under `assets/minecraft/` of the server pack. The abbreviations are `bs/` = `blockstates/`,
`m/` = `models/block/` and `t/` = `textures/block/`.

### Redrawn as something else (the important rows)

| Vanilla block / state | What Wynn draws | Photon today | Proposed change | Conf. | Evidence |
| --- | --- | --- | --- | --- | --- |
| `redstone_torch:lit=true`, `redstone_wall_torch:lit=true` | Candle sconce. A cream wax candle with a flame cross on a dark iron holder. | 79, Wynn candle sconce (0.1.0) | Keep. 0.1.0 is right, and the server pack is the source. | high | `m/redstone_torch.json` → `m/redstone/lit_candle.json`; `m/redstone/lit_candle_on_wall.json`; textures `t/variants/candle_on`, `candle_flame` (3 colors: `#f24000`, `#ff8f00`, `#ffeab4`), `candle_on_wall` |
| `redstone_torch:lit=false`, `redstone_wall_torch:lit=false` | An unlit candle. Plain wax (`t/variants/candle`, `#e1ddc4`) on the holder. | No category | Optional: weak SSS (15) for the wax. | med | `m/redstone/candle.json`, `candle_on_wall.json` |
| `repeater:powered=true` (delay 1-3) | **Lit candelabra.** A dark iron stand and pillar with one lit candle 1-1.75 blocks up, using the same `candle_on`/`candle_flame` textures as the sconce. | 38, redstone components. Red light ×5, and the flame and wax glow red. | Move to **79** (sconce material and light). The pillar textures (`#5f5343`, `#625746`) stay dark under the sconce mask. | high | `m/repeater_{1,2,3}tick_on.json` → `m/redstone/lit_candelabra_{1,2,3}.json` |
| `repeater:delay=4:powered=true` | Lit **four-candle** candelabra (4 flames on arms). | 38 | Move to 79 too. For a brighter light, give it its own light ID (see the lights table). | high | `m/repeater_4tick_on.json` → `m/redstone/lit_candelabra_4.json` |
| `repeater:powered=false` | Unlit candelabra (1 or 4 candles). | 80, transparent (no material) | Optional: the iron stand as transparent metal (18). The wax is small. Low value. | med | `m/redstone/candelabra_{1..4}.json` |
| `comparator` (all states) | **Floor spike trap.** A 2-px iron base plate with eight thin crossed spike planes. `mode=subtract` is the "blood" version (rust-red base, `#82594c`). `powered=true` shows only the base plate (spikes retracted). | `powered=true` → 38: **red light ×5** and red emission. `powered=false` → 80. | Move all comparator states to **18** (transparent metal), **no light**. Nothing in the textures glows: the brightest pixel is L 0.49. | high | `m/comparator*.json` → `m/redstone/comparator_{spike,blood_spike}{,_base}.json`; `t/variants/comparator_*` |
| `petrified_oak_slab:type=double` (old wooden double slabs) | **Molten rock.** Near-black stone (`#2e110d` average) with sparse red-orange embers that pulse in a 2-frame, 20-tick interpolated animation. About 2-12 % of pixels are hot (`#bd1006`, `#e80b01`). Four random variants. | 30, wood transparent: wood specular, no glow | Emissive with ember light. Cheapest: move to **40** (magma/fire: medium orange, light ×9; probably too strong for a block that is mostly black). Better: a Wynn-only "embers" ID, where only saturated red-orange pixels glow, with a dim red light (see the lights table). | med-high | `bs/petrified_oak_slab.json`; `m/variants/molten_rock{,_0,_1,_2}.json`; `t/variants/molten_rock*.png(.mcmeta)` |
| `petrified_oak_slab:type=bottom/top` | **Dark cobblestone slabs**. | 30, wood | Remove from 30 (no category, plain stone). | high | `m/variants/dark_cobblestone_{half,upper}_slab.json` |
| `vine` (5 single-face states + `up`-only variants) | Vine (a flat 0.8-px plane, `#306a22`). | 14, strong SSS | Keep. | high | `bs/vine.json` multipart |
| `vine:east=false,north=true,south=true,west=false` (`up` either) | **Iron chain** (crossed planes, black and grey `#575757`-`#909090`). | 14, strong SSS: a black chain glows translucent in the sun | **18** (transparent metal), or 80 to match vanilla `chain`. | high | `bs/vine.json` → `m/variants/chain.json`; `t/variants/chain.png` |
| `vine:east=true,north=false,south=false,west=true` (`up` either) | **Rope** (cross, fibre brown `#7a5e3d`). | 14 | **15** (weak SSS). | high | → `m/variants/rope.json` |
| `vine:east=true,north=true,south=false,west=true` (`up` either) | **Dead leaves** (a full cube, grey-brown, 63 % opaque). | 14 | **5** (leaves: SSS, leaf specular, and leaf waving if wanted), or 15 if waving looks wrong on these cubes. | med | → `m/variants/dead_leaves.json` |
| `vine:east=true,north=true,south=true,up=false,west=false` and `...,up=true,west=false` | **Murky water.** A single up-face at y = 14, grey-brown `#3a3832`, animated (frametime 2). | 14, strong SSS, rough | Wynn-only **"still water" material**: dielectric, roughness about 0.05, `ssr_multiplier = 1`, no SSS. Mask 1 (real water) is wrong, because this is cutout terrain, not the water pass. | med | → `m/variants/murky_water.json`; `t/variants/murky_water.png.mcmeta` |
| `vine:east=true,north=true,south=true,up=false,west=true` | **Dead grass and flowers.** 10 random cross plants (dead grass ×5, tulip, orchid, houstonia, daisy, rose). | 14 (no waving) | **2** (small plants: waving, SSS and sheen). | high | → `m/variants/dead_*.json` |
| `vine:` all five `false` | **Cattails** (2-block-tall crossed planes, y −16..16, 3 variants). | 14 | **2**. The planes span y −16..16 as single quads, so they wave cleanly. | high | → `m/variants/cattails{,_0,_1}.json` |
| `vine:` all five `true` | **Hanging crops** (crossed planes, y −16..16). | 14 | **2**, or **4** (tall-plant upper) so the whole quad sways. | med | → `m/variants/hanging_crops.json` |
| `vine:` other two-to-four-side combos (12 states) | Nothing (`block/blank`: no elements). | 14 | None needed. | high | `m/blank.json` |
| `daylight_detector` (all states) | **Stacks of books** on an oak base (3 models × 4 rotations). | No category. Photon lists `daylight_sensor` in 80, a typo that matches nothing. | Optional: 29 (wood) for the planks. Low value. | med | `bs/daylight_detector.json` → `m/variants/stack_of_books_{0,1,2}.json` |
| `sticky_piston` | A box in all-brown wood textures (`#5f4932`-`#735838`), likely a crate. | No category | 29 (wood). | med | `m/variants/sticky_piston{,_base}.json`; `t/variants/piston_*` |
| `brick_slab` | **Dirt slabs** (bottom and top, 3 variants). `type=double` is the **vanta block** (pure `#000000`). | No category | None. Mask 0 is fine for both. | high | `bs/brick_slab.json`; `t/variants/vanta_block.png` |
| `cauldron` | 4 custom cauldron models with an animated blue water plane, keyed `level=0..3`. | No category | **Probably broken on 1.21.11**: plain `cauldron` has no `level` property. Check in game before touching it. | low | `bs/cauldron.json`; `m/variants/cauldron_{0..3}.json` |
| `enchanting_table` | **Dark stone table/lectern** (top slab, stand, base; grey-blue `#495154`, near-black sides) with no glowing parts. The floating book still renders (its texture is retextured). | 54: no material branch, **purple light ×2** | Keep the material. Change the light to warm candle-like or none (see the lights table). Check what Wynn uses it for. | med | `m/enchanting_table.json`; `t/variants/enchanting_table_*`; `textures/entity/enchanting_table_book.png` |
| `spawner` | Near-black cage (`#070e14` average, 52 % opaque, no pink). | 47, pink glow: purple light ×2.5 | Move to **18** (metal cage), no light. | med | `t/mob_spawner.png` |
| `activator_rail:powered=true`, `detector_rail:powered=true` | Activator rail: two brown wooden beams with no ties (`m/activator_rail*.json`, texture `#714f3a`); Wynn uses it as wooden track. The detector rail's "on" texture has no red at all. | 38: red light ×5 | Move to **80** (no light). | med | `m/activator_rail_active_*.json`; `t/detector_rail_on.png` |
| `powered_rail:powered=true` | Rail with some red and gold studs (32 hot pixels). | 38 | Keep. | med | `t/powered_rail_on.png` |
| `tripwire`, `tripwire_hook` | Invisible (`block/blank`). | 80 / 30 | None. | high | `bs/tripwire*.json` |
| `note_block` | All 800 states forced to the single plain `note_block` model, a dark brown wood (`#523e29`). **Not** a custom block carrier. | 29, wood | None. | high | `bs/note_block.json` |
| `red_mushroom_block`, `brown_mushroom_block`, `mushroom_stem` | Only the all-faces-out and all-faces-in states draw (the cap is red `#c43331` with white spots). The other 62 face combos show `block/error`. **Not** used as custom block carriers. | 14 / 14 / 15 | None. | high | `bs/*mushroom*.json`; `m/error.json` |

### Retextured or re-modelled, same material

These blocks keep their vanilla meaning, and Photon's category still fits.

| Block(s) | What Wynn draws | Photon today | Proposed | Conf. | Evidence |
| --- | --- | --- | --- | --- | --- |
| `wheat`, `carrots`, `potatoes`, `beetroots` | Other crops by age: cream, hops, sorghum, wheat, jute, heather, dernic, root, corn. Some are **2-block-tall stacked quads**. | 2, small plants | Keep 2. Fix the shear: for models with stacked quads, check in game whether the seam opens. If it does, move those ages to 3 (tall plants lower) or 0. Affected: `wheat:age=1,2,4,6`; `carrots:age=1,2`; `potatoes:age=1,2`; `beetroots:age=1,2`. Jute and heather are single tall quads and fine. | med | `m/variants/crop_*.json`, `wheat_corn.json`, `wheat_0.json` (elements at y −1..15 and 15..31) |
| `short_grass`, `sunflower` (lower), `cobweb` | Extra random variants. | 2 / 3 / 15 | Keep. | high | `bs/short_grass.json`, `sunflower.json`, `cobweb.json` |
| `iron_bars` | Thicker custom bars (2 px posts and rails) with an `iron_bars_top` edge texture, grey `#7f7b71`. | 18, transparent metal | Keep. | high | `m/bars_*.json` |
| `infested_*` (6 blocks) | Darker stone, cobble and brick variants. | No category | Keep. | high | `bs/infested_*.json` |
| `stone`, `cobblestone`, `mossy_*`, `cracked_stone_bricks`, `dirt`, `grass_block`, `netherrack`, `sandstone`, `sand`, `red_sand`, `bedrock`, `mycelium` | Random texture or rotation variants. | No category, 7 or 9 for sand | Keep. | high | `bs/*.json` |
| `coal_ore`, `emerald_ore` | Wynn-grey stone with small specks (emerald `#6cd454`). | No category | Keep. Don't add emission: vanilla doesn't glow either. | high | `t/*_ore.png`, `t/variants/*_ore_0.png` |
| `redstone_ore` (lit and unlit share one look) | Grey stone with 13 small red and pink specks. | Unlit: no category. `lit=true`: 38 (red light ×5, red specks glow). | Keep. It matches vanilla, which lights up when touched. The plan's question, answered: the texture is the same, and only the specks would glow. | med | `bs/redstone_ore.json`, `t/redstone_ore.png` |
| `jack_o_lantern`, `pumpkin`, `carved_pumpkin` | Retextured. The jack o' lantern is a warm cream face (`#ffe3ad`). | 42 / 9 / none | Keep. | high | `m/jack_o_lantern.json`, `t/jack_o_lantern.png` |
| `fire` | Taller fire (22.4 px planes), orange `#ec8e39`. | 40 | Keep. | high | `m/fire_*.json` |
| `bookshelf`, `ladder`, `oak_fence*`, `birch_fence*` (log-column walls), `oak_fence_gate`, doors, `flower_pot`, `potted_cactus`, `chiseled_red_sandstone` | 3D remodels with the same materials. | 29 / 30 / 80 / 9 | Keep. | high | `m/*.json` |
| `glowstone`, `redstone_lamp:lit`, `furnace:lit`, `magma_block`, `lava`, `beacon`, `end_portal_frame`, `brewing_stand` | Retextured with the same idea. Glowstone is pale yellow and animated. The beacon is teal. | 35 / 35 / 36 / 40 / 39 / 44 / 45 / 41 | Keep the materials. See the lights table for color tweaks. | high | `t/*.png` |
| Leaves (Better Leaves) | A cube plus crossed bushy planes. | 5 | Keep. Leaf waving moves the extra planes too. | high | Better Leaves `models/block/leaves_cross.json` |
| `lantern`, `torch` (WynnLanternRP) | A Blockbench lantern; a cross-plane torch. | 35 / 36 | Keep. | high | WynnLanternRP `models/block/*.json` |

**Inert files.** `grass_path.json`, `web.json`, `cobblestone_monster_egg.json`, `brick_double_slab.json`,
`wood_old_slab.json` and `wood_old_double_slab.json` use pre-1.13 names. 1.21.11 ignores them.

## Lights table

Each row has two halves, and both must ship together (see "Photonics rules"):
- **LPV:** the `block.properties` ID plus its `light_colors.glsl` entry (index = ID − 32, RGB × strength). These
  apply with and without Photonics.
- **Photonics:** `ph_lights.json`, with a `#ff…` colour and an intensity on Photon's scale (lantern 300, 0.1.0's
  sconce 257, redstone components 107).

"Disabled" means the block goes into Photon's `ph_lights.json` `"disabled"` override (`#000000`, intensity 0) by
name. That is needed because Photonics' own defaults list it as a light.

| Light (block/state) | What Wynn draws | Photon today (LPV / Photonics) | Proposed LPV | Proposed Photonics | Conf. |
| --- | --- | --- | --- | --- | --- |
| Candle sconce (`redstone_torch:lit`, wall) | One candle flame | 79: `(1.00,0.45,0.12)×12` / `candle_sconce`: `#ff7219` (medium orange), 257 | Keep. | Keep. Check that it beats Photonics' default `redstone` group, which lists `redstone_torch[lit=true]`. | high |
| Lit candelabra, 1 candle (`repeater:powered=true`, delay 1-3) | One candle flame | 38: `(1.00,0.18,0.10)×5`, red / `redstone_components` `#ff2d19`, 107; also Photonics default `redstone` | ID 79: `(1.00,0.45,0.12)×12`. | Joins `CANDLE_SCONCE_ID` automatically once it is on 79. Check that it beats the Photonics default `repeater[powered=true]` entry; if not, add `{ "value": "minecraft:repeater[powered=true]", "priority": 100 }` to `candle_sconce`. | high |
| Lit candelabra, 4 candles (`repeater:delay=4:powered=true`) | Four flames | Same as above | Own ID from 64-77: `(1.00,0.45,0.12)×14`. Or keep it on 79 if spending an ID isn't worth it. | New override `candelabra`: `#ff7219`, ≈300, its own `#define CANDELABRA_ID`. | med |
| Spike traps (`comparator`, all states) | Retracted or raised iron spikes, no light | `powered=true` → 38, red ×5 / red, plus Photonics default `comparator[powered=true]` | Out of the light range (→ 18). | **Disabled:** `"minecraft:comparator"`. | high |
| Activator/detector rail, `powered=true` | Wooden track, no red | 38, red ×5 / red (via the 10038 group) | → 80. | No change needed: leaving 10038 drops them from `REDSTONE_COMPONENTS_ID`, and Photonics' defaults don't list rails. | med |
| Molten rock (`petrified_oak_slab:type=double`) | Dark rock with pulsing embers | 30, none / none | Wynn-only ID from 64-77: `(1.00,0.30,0.08)×3`, a dull ember glow (a third of magma's 9). | New override `molten_rock`: `#ff4c14`, ≈60-80, `#define MOLTEN_ROCK_ID`. | med |
| Spawner | Dark cage | 47: `(0.60,0.10,1.00)×2.5` / `pink_glow` `#9919ff`, 53 | Out of the light range (→ 18). | Drops out of `PINK_GLOW_ID` automatically. Photonics' defaults list only `trial_spawner`, so it doesn't need disabling. | med |
| Enchanting table | Dark stone table, no glow | 54: `(0.60,0.10,1.00)×2` / `enchanting_table` `#9919ff`, 43, plus a Photonics default entry | None, or warm `(1.00,0.57,0.30)×2` if it is used as a lit lectern. Check in game. | If no light: **Disabled** `"minecraft:enchanting_table"`. If warm: recolour the `enchanting_table` define to `#ff914c`. | low |
| Glowstone | Pale yellow (`#f2d18e`, about `(1.00,0.86,0.59)`) | 35: `(1.00,0.55,0.27)×14` / `strong_golden_light` `#ff8c44`, 300 | Optional. A paler golden light such as `(1.00,0.75,0.45)×14` needs its own ID, because 35 is shared with lanterns and redstone lamps. | Matching new override, ≈`#ffbf73`, 300. | low |
| Beacon | Teal (`#63d1c6`) | 44: `(0.45,0.73,1.00)×14` / `beacon` `#72baff`, 300 | Optional: `(0.45,1.00,0.90)×14`. | Recolour `beacon` to ≈`#72ffe6`. | low |
| Jack o' lantern, fire, lava, magma, lantern, torch, redstone lamp, furnace, `redstone_ore:lit`, `powered_rail:powered` | As vanilla | Unchanged | Keep. | Keep. | high |

## Findings on the brainstorm leads

**Custom block carriers: only `vine` is one.**
- `note_block` forces all 800 states to one model.
- The mushroom blocks use only two states and show `block/error` for the other 62.
- `tripwire` and `tripwire_hook` are invisible.
- The real carrier is **`vine`**. Its multipart maps exact state combos to chain, rope, dead leaves, murky water,
  cattails, hanging crops and ten dead plants. Every one is a separate `block.properties` line.
- The other "repurposed" blocks are `repeater` (candelabras), `comparator` (spikes), `daylight_detector` (books),
  `petrified_oak_slab` (molten rock and dark cobble slabs), `brick_slab` (dirt slabs and the vanta block) and
  `sticky_piston` (crate).

**Repurposed vanilla blocks.**
- `redstone_ore:lit` looks the same as unlit, which matches vanilla, so keep it.
- `coal_ore` and `emerald_ore` are decorative ores, and Photon doesn't make them glow.
- `iron_bars`, `cauldron`, `bedrock` and `netherrack` keep their meaning (but see the cauldron caveat).
- The old slab IDs are the molten rock and the dirt slabs.

**Plants.**
- SSS fits all the plants.
- Waving fits the single-quad ones, but the 2-tall stacked crop quads shear (see the block table).
- Dead plants, cattails and hanging crops currently sit on the vine's strong SSS with no waving.

**Emissive pixels: yes, but only on item models, not blocks.**
- `shaders/include/item.glsl:47-48` defines `SHADELESS_ALPHA = 251` and `EMISSIVE_ALPHA = 254`.
  `include/render/fragment/item.glsl:71-72` tests the texture alpha for them.
- **Alpha 254** makes a pixel full-bright: no lightmap, no face shading, but the tint is kept.
- **Alpha 251** drops face shading only.
- Only `rendertype_item_entity_translucent_cull` checks these. That is the program for item displays, item frames,
  dropped items and held items.
- A pixel scan of every PNG (alpha values other than 0 and 255):
  - **Block textures carry no marker.** Their only partial alphas are glass (127), pane tops (96-103), water
    (192/196), slime (191) and ice (136, 155-183). So there is nothing for terrain to pick up.
  - **Item textures:** 872 of the 3,160 hashed `item/w*.png` prop textures have alpha-254 pixels (about 1.17 M
    pixels). 64 of them are procedural-skybox textures that use the same marker (G 251 + A 254).
  - Also 48 `wynn/` textures, for example `wynn/housing/furniture/statue_light_guild.png`, `wynn/prop/soul_flame.png`,
    `wynn/jigsaw/bonny/flame.png` and `jigsaw/wisp.png`, and 224 `skins/` textures.
- **For Wynn-Photon: nothing to do. WynnIris owns this.** WynnIris already reads alpha 254 and 251 in Photon's
  entity programs (see "Scope"). A Photon alpha test on top would double-apply the effect.
- WynnIris only lifts albedo, so under Photon a 254 pixel is still shaded by scene light and gets no bloom or light.
  Real emission for props would be a WynnIris feature request, not a Photon patch.

**Lost core-shader effects.**
- The pack ships **no terrain core shaders**, so blocks lose nothing under Iris.
- On 1.21.11, the programs that apply are the ones in `1_21_5/assets/minecraft/shaders/core/`: `entity`, `gui`,
  `position_color`, `position_tex_color`, `rendertype_item_entity_translucent_cull`, `rendertype_text` and
  `rendertype_text_see_through`. The root `core/*.json` and `pipeline/` files are 1.21.4 leftovers.
- With a shader pack on, Iris replaces these programs in the world. **WynnIris puts almost all of their effects
  back** into the shader pack's own programs:
  - alpha-254 emissive and alpha-251 shadeless pixels;
  - glints and translucency;
  - neutralised effect and movement tints;
  - Y-encoded skin remap and near-fade;
  - procedural skyboxes;
  - text effects and transitions, plus a text-display brightness floor;
  - fog.
- The only gap found is **item movement animation** (`movement.glsl`: slide, orbit, warp…). WynnIris neutralises the
  tint but doesn't animate in shader-pack programs. Only its text fallback animates. That is a WynnIris request too.
- **Wynn-Photon has nothing to recreate here.** Every lost effect is on entities, items or text.

**Item-model props.**
- Wynn's ~25,000 world props are all **`oak_boat`** items. `items/oak_boat.json` is a `range_dispatch` on
  `custom_model_data` with 61,174 cases, pointing at the hashed models `models/w<n>.json`. Those models use hashed
  textures `textures/item/w<hash>.png` and contain no `light_emission` elements.
- Gear and ingredients dispatch on `custom_model_data` in `items/potion.json` and the tool items.
- WynnIris 1.2.2 (Iris 1.10.8) picks the rendered item's ID from the **`minecraft:item_model` component**, falling
  back to the registry ID. A BlockItem uses its block's ID instead. Nothing matches on `custom_model_data`. This was
  read from the WynnIris bytecode: `ItemStackStateLayerMixin`, `IdMap.parseItemIdMap`.
- Wynn props set `custom_model_data`, not `item_model`, so in Iris **every prop is just `oak_boat`**. They cannot get
  per-prop lights or materials from `item.properties`.
- Also, Photon only uses the item ID for entities when the entity ID is 10100 (`item`, `include/vertex/utility.glsl:13`).
  It does not do this for `item_display`.
- **Decision: leave props alone in Wynn-Photon.** Per-prop matching would need a `custom_model_data` matcher in
  Iris, which is WynnIris's code. Changing how Photon's entity programs pick masks risks breaking WynnIris's
  injection there. If per-prop lights are ever wanted, ask WynnIris for a `custom_model_data` matcher first.

**Entities and NPCs.** These are WynnIris's area: the entity emissive, the skin remap, the mount armour overlay and
the dark-sky entity boost. No `entity.properties` changes are proposed.

**Voxy LODs.**
- Voxy gets the same `block.properties` ID. Photon's Voxy path runs `material_from`, so the hardcoded specular,
  emission and SSS **apply at LOD** (`program/voxy_opaque.glsl`, `d4_deferred_shading.fsh:350`).
- The LOD path has no colored LPV light (the volume is only 64-256 blocks) and no waving. A remapped light glows as
  a surface at distance, but casts no light, which is the same as today's lanterns.
- **Photon bug:** `voxy_opaque.glsl:19` computes `max(customId - 10000u, 0u)` in unsigned math. An unmapped block
  (customId 0) wraps around and becomes mask **255**, not 0. Near terrain then gets default specular and the same
  block at LOD gets a fully rough material. That covers most Wynn terrain: stone, dirt, the infested variants and so
  on. Fix: `customId >= 10000u ? customId - 10000u : 0u`. It is cheap and worth a patch.
- Distant Horizons ignores block IDs (`dh_terrain.vsh:49-85`).

**Add-on packs.** Only WynnLanternRP and Better Leaves touch blocks, and neither needs a Photon change. The sconce,
candelabras and spikes are all server-pack content.

**labPBR.** Confirmed: the server pack has no `_n` or `_s` maps.

**WynnIris overlap.** Covered in "Scope" at the top. Every block, light and Voxy proposal in this doc is outside
what WynnIris touches. The two prop and emissive ideas that would have overlapped are dropped.

## Material ID range

- **Bits:** the gbuffers and Voxy store 8 bits (0-255). The colored-light voxel map caps usable IDs at **127**: a
  full block with ID ≥128 would pass light, and `id & 127` in 32-79 would even emit.
- **Taken:**
  - 0-30 misc, 32-63 lights, 64-79 modded light colors (now 78 = magenta+pink, 79 = sconce).
  - 80 transparent, 81-96 tinting glass (96 is also where leaves are remapped in the light volume).
  - 100-103: item, boat, lightning bolt, dragon beam.
  - Code overrides use 4, 32, 40 and 47.
- **Wynn-only lights:** reuse **10064-10077**. These are the Simply Light and Luminax color slots, mods Wynncraft
  never uses. That gives 14 slots without touching the 48-entry light array, `program/lpv/setup.csh`, the
  `light_data` image size or the emitter range checks. Each new light needs:
  - an entry in `block.properties`;
  - a branch in `material.glsl` *before* the 64-79 glass block (the way 79 does it);
  - its `light_colors.glsl` entry;
  - a define and an override in `ph_lights.json`.
- **Wynn-only non-light materials:** **10097-10099** and **10104-10127** (27 slots). Each needs a new `if` in
  `material.glsl`. Nothing else, and no Voxy change.

## Ranked shortlist

All of these are terrain-only: they stay inside Wynn-Photon's side of the WynnIris boundary. Each light item ships
its LPV half and its Photonics half together (see the lights table).

1. **Candelabras become candles.** Move `repeater:powered=true` to 79, so it gets warm flame light instead of red,
   using the same textures as the sconce.
   - LPV: one `block.properties` line.
   - Photonics: it joins `CANDLE_SCONCE_ID` automatically. Check that it beats Photonics' default
     `repeater[powered=true]`.
   - *Wynn's most common decorative light is currently red.*
2. **Spike traps stop glowing.** Move all `comparator` states to 18 and out of 10038.
   - Photonics: add `"minecraft:comparator"` to the `disabled` override, since Photonics' defaults list it.
3. **Vine carrier split.** Chain → 18, rope → 15, dead plants, cattails and hanging crops → 2, dead leaves → 5. These
   are exact-state lines in `block.properties` and need no shader code.
4. **Fix the Voxy unsigned wrap** (`voxy_opaque.glsl:19`), so unmapped Wynn terrain looks the same at LOD as up close.
   A one-line patch.
5. **Molten rock glows.** Give `petrified_oak_slab:type=double` a Wynn-only ember material and a dim ember light in
   the 64-77 range: `light_colors.glsl` plus a new `ph_lights.json` override. Also unmap `type=bottom/top`, which are
   stone slabs and not wood.
6. **Remove the wrong lights from the spawner (47), the activator rail and the detector rail.** Check in game first
   that Wynn places them. Photonics follows automatically, since its defaults don't list these.
7. **Murky water material.** A Wynn-only glossy, reflective material for the vine murky-water state.
8. **Four-candle candelabra light** (`repeater:delay=4:powered=true`) on its own, brighter ID, with a matching
   Photonics override.
9. **2-tall crop shear.** Check in game, then remap the affected ages if the seam shows.
10. **Enchanting table light.** Once it's known what Wynn uses it for, recolour it or disable it, in both the LPV and
    Photonics.

**Not for Wynn-Photon (WynnIris requests instead):** real emission and bloom for alpha-254 prop pixels, item
movement animation, and `custom_model_data` item matching.

Each item still needs the in-game check from the plan's principles: `installDevPack` and a Wynncraft area where the
block appears.

## Full texture audit (roadmap step 9, 2026-11-16)

The first texture scan (roadmap step 5) read only the 8-bit textures. This audit reads all of them and compares each
one with vanilla 1.21.11 and with the Photon material its block is on today (0.6.0 patches applied). It is research
only. Nothing here is built yet.

### Method

- **Same pack** as the survey (`PRODUCTION_d2668821…`, 2026-11-06), read the same way through the central directory.
- **Decoding:** every block PNG in the pack has wrong chunk CRCs and a wrong zlib Adler checksum, so Pillow rejects
  all of them, not just the palette ones. A small decoder that ignores both checksums reads all 459. It was checked
  against Pillow on all 1,111 vanilla block textures, with identical pixels.
- **Formats:** 267 8-bit palette, 151 palette at 1, 2 or 4 bits (the ~150 the first scan skipped), 41 RGB, RGBA or
  grey.
- **Texture → block:** every 1.21.11 blockstate (the pack's override where it has one, else vanilla), through its
  models' parents and texture variables. Each block state then gets its Photon ID from `block.properties`, first match
  wins, tags expanded from the vanilla tag files. This reproduces the 0.2.0 vine split exactly, which is a good check
  that the mapping is right.
- **Vanilla comparison:** the average colour of opaque pixels, as a CIELAB distance (ΔE), lightness change and hue
  change. Animated textures are averaged over all frames.
- **Photon comparison:** Photon's material formula from `material.glsl`, run per pixel for the block's ID on both the
  Wynn and the vanilla texture: average emission, share of glowing pixels, and average smoothness. Note that Photon's
  `rgb_to_hsl` is really HSV, so its "lightness" is the brightest channel.
- **Visual check:** side-by-side sheets of the ~100 largest changes, kept outside the repo. The tables below
  describe textures and do not copy them.
- **Tinted foliage:** vanilla leaves, grass, ferns, vines and lily pads are grey and tinted per biome. Wynn ships
  them pre-coloured green. That makes them top the ΔE list (ΔE 45-53), but it means nothing for Photon, which keys
  leaves on brightness only.

Of the 459 textures, 336 have a vanilla file of the same name and none is byte-identical to vanilla. 123 exist only
in the pack: 116 under `variants/` and 7 pre-1.13 names. 26 are used by no 1.21.11 blockstate. Most are pre-1.13 names
the game no longer loads (`grass_path_*`, `mob_spawner`, `purpur_pillar_side`, `quartz_pillar_side`, `stone_slab_*`,
`trip_wire_hook`, `farmland_wet`, `oak_sign`), plus textures the pack's own models replaced (`comparator*`,
`enchanting_table_*`).

### Redrawn as something else (new)

| Block | What Wynncraft draws | Photon today | Proposed | Confidence |
|---|---|---|---|---|
| `lava_cauldron` | The wooden tub from 0.4.0's `cauldron` change. The pack retextures `cauldron_*` as brown wood, and the vanilla lava, water and powder-snow cauldron models use those textures. | 39 (lava). Wood is orange-brown at about 0.45-0.5 saturation, so the lava formula (`step(0.4, s)`) lights the whole tub. The tub glows *more* than Wynn's dark lava does: emission 0.20-0.29 on 75-100 % of tub pixels, against 0.14 on 35 % of lava pixels. | Raise the lava formula's saturation gate from 0.4 to 0.6. That removes 100 % of the tub's glow and 0 % of the lava's, for Wynn's lava and vanilla's alike, so "lava stays as Photon draws it" still holds. No new ID needed. | high |
| `water_cauldron`, `powder_snow_cauldron` | The same wooden tub. | 0 (no category) | 29 (wood), next to `cauldron`. | high |
| `piston`, `piston_head`, `moving_piston` | The same wooden crate as `sticky_piston` (all-plank faces, `#745838`). Only the sticky piston was moved in 0.4.0. | 0 | `piston` → 29; `piston_head`, `moving_piston` → 30 (non-full). | high |
| `tnt` | A wooden crate with "TNT" painted on the side and red-orange sticks on top. | 0 | 29 (wood). | high |
| `purpur_block`, `purpur_pillar`, `purpur_slab`, `purpur_stairs` | Dark wooden planks (`#625138`). The pillar's top is a grey-blue round end. | 0 | Block and pillar → 29, slab and stairs → 30. | med-high |
| `iron_door` | A cream, painted-looking door with a dark window (`#a29a8b` average), not bare iron. | 18 (transparent metal): albedo-tinted metal at smoothness 0.81, so it can read as chrome. | Check in game. If it is a painted wooden door, move it to 30 (transparent wood). | med |
| `farmland` | Vertical wooden boards with nail heads (`#624928`). | 0 | Check in game what Wynn uses farmland for. If it is floorboards or planter boxes, move it to 29. | med-low |
| `sponge`, `wet_sponge` | **Animated rainbow block.** Five interpolated frames, 48 ticks each, cycling yellow → green → teal → magenta → orange. The wet sponge is a darker version. This answers the brainstorm lead. | 0 | Find where Wynn places it. If it reads as magical, give it a faint emission with no light, since the colored-light volume can't follow a changing colour. Otherwise no change. | low |
| `dragon_egg` | A mossy green texture on the vanilla egg shape. | 0 | No change. | low |
| `brick_slab` | Bottom and top: dirt slabs (survey). **Double: `vanta_block`, pure black `#000000`.** | 0 | No change. Pure black reflects nothing and needs no material. | high |

### Hue- and brightness-keyed materials

Every block on a Photon material whose look depends on the texture's colour, with the Wynn texture under that
formula:

- **Lights:**
  - Redstone lamp, torch, lit furnace, jack o'lantern, fire, beacon, end portal frame eye: within ±0.08 of
    vanilla's emission.
  - Powered rail: dimmer but still red in the centre.
  - Observer (powered, 38): the back still has a red dot, so its red glow and light fit.
  - Redstone block: unchanged.
  - Brewing stand: glows on 22 % of pixels instead of 32 %. No change.
  - Chorus flower: barely glows on Wynn's darker texture. Rare in Wynn. No change.
- **Glowstone (54):** Wynn's flat pale texture glows on 100 % of its pixels at 0.67, against vanilla's 0.24 on 44 %.
  0.4.0 changed only its light colour. Check in game that it doesn't look like a flat white panel. If it does, scale
  its emission down.
- **Lava (39):** emission 0.14 on 35 % of pixels, against vanilla's 0.41 on 100 %, because Wynn's deep red sits outside
  the formula's orange hue window. This is the 0.4.0 retune that was tried and reverted. No change. The lava
  cauldron row above is separate.
- **Magma (67), molten rock (77), emerald (75, 76), candles (73, 79, 104):** match their 0.2.0-0.5.0 tuning.
- **Gems (13):** diamond and lapis blocks are within 0.08 of vanilla's smoothness.
- **Metals (12, 18, 99):** gold is a little darker, which is fine (the plan says gold stays as is). Anvils and hoppers
  are darker, so anvils are less shiny (-0.17). That fits Wynn's dark iron.
- **Sand and red sand (7, 9):** Wynn's sand is darker, so it is 0.06-0.17 less glossy. Red sand and red sandstone
  drop to almost matte (0.04-0.10). No change. It follows the texture.
- **Granite and diorite (10):** lighter than vanilla, so 0.11-0.15 *more* glossy. Diorite, a near-white rock, hits the
  formula's 0.5 cap. Low priority: check in game whether diorite looks wet.
- **Ice (8):** Wynn's ice is greyer and darker. Its smoothness change is negligible, because the formula is a
  fourth-power curve.
- **Copper (27, 28):** the pack retextures no copper.
- **Default blocks (0):** Photon gives unmapped blocks smoothness up to 0.33 by brightness. Wynn's darker stone,
  cobblestone, stone bricks and netherrack lose 0.05-0.11 of it. That needs no change.

### Ranked list

1. **Lava cauldron tub stops glowing.** Change the lava formula's saturation gate in `material.glsl` from 0.4 to 0.6.
   This is one number. It leaves lava untouched and keeps the cauldron's lava light.
2. **Pistons and TNT become wood.** `piston` and `tnt` → 29; `piston_head` and `moving_piston` → 30. This finishes
   0.4.0's sticky-piston crate.
3. **Water and powder-snow cauldrons become wood** (29), matching `cauldron`.
4. **Purpur becomes wood.** Block and pillar → 29, slab and stairs → 30.
5. **Iron door:** check in game. If it is painted wood, move it to 30.
6. **Farmland:** check in game. If it is boards, move it to 29.
7. **Glowstone brightness:** check in game. Scale its emission down if it reads as a flat panel.
8. **Sponge:** find it in game, then decide whether it gets a faint glow.
9. **Diorite gloss:** check in game.

Items 2-4 are `block.properties` lines with no shader code. None of them are lights, so they need no Photonics or
`light_colors.glsl` change. Each still needs a test-world exhibit and the in-game check.

**Outcome (0.7.0):** items 1-5 are built, the iron door as transparent wood (30). Items 6-9 are closed without
changes. Built alongside them: emerald blocks glow about a third as bright with the same light, and the enchanting
table gets a faint purple light on `10068` (shortlist item 10).
