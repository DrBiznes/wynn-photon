# Spell lights (experimental)

Can Wynn-Photon make Wynncraft's spells glow and light up their surroundings? Yes, for spells drawn as models. This
branch (`experimental/spell-lights`) does it for Mage's Meteor as a test. Researched against the Wynncraft server pack
cached on 2026-11-21 (`PRODUCTION_aeac8911…`), the newest one, which includes the class-reskin update.

## How Wynncraft draws spells

- **Spell models are items on display entities.** The pack has 13 named spell models in
  `models/item/wynn/spell/`: `meteor_0`/`meteor_1`, `ice_snake_0`/`_1`, `trap_0`/`_1`, `angel_0`/`_1`, `totem_0`/`_1`
  and the three Shaman masks (`mask_coward`, `mask_fanatic`, `mask_lunatic`). Their textures are in
  `textures/wynn/spell/`.
- They hang off two items. `minecraft:potion` dispatches on `custom_model_data` `floats[0]`, 4030-4042 (meteor_0 is
  4039, meteor_1 4031). `minecraft:diamond_sword` dispatches on damage, and `stone_shovel` holds the totems. All the
  models use the `head` display transform (scale 3), so they are drawn as item displays in head mode, or on an armor
  stand's head.
- **The animation is the server's.** The models are static. Wynncraft moves them with the display entities'
  interpolated `transformation` (or teleports), and adds particles. The only motion in the files is animated
  textures: the meteor's flame (`meteor_*_a.png`) is 5 frames at 2 ticks.
- **Meteor** is 8 cubes of rock plus one flat element 30 px long, textured with the flame (`#1`), trailing behind it.
  `meteor_0` is grey rock with orange fire. `meteor_1` is dark teal rock with glowing purple cracks and a purple
  flame. Which class skin or archetype uses `meteor_1` isn't known yet.
- **Reskins.** The update added about 10,000 cases to `oak_boat` (71,709 now, 61,174 in the block survey), all
  hashed: `models/w<n>.json` with hashed textures, and the hash names reshuffle every release. Reskinned spell
  effects are probably among them, but they can't be told apart by name. A pack diff between releases doesn't help
  either, because of the reshuffle.
- **No spell texture is marked emissive.** None of `textures/wynn/spell/` uses alpha 254, so even with WynnIris the
  meteor isn't full-bright today. (The pack is also obfuscated: a scrambled zip, local file headers that don't match
  the directory, and PNGs with broken zlib checksums. Read it from the central directory and ignore the checksums.)

## Why the shader can't do this alone

- Iris tells the shader the rendered item's ID (`currentRenderedItemId`), but only from the item and its
  `item_model` component. Every spell is a `potion` or `diamond_sword`, the same as thousands of other models, and
  `item.properties` can't match `custom_model_data` (see "Item-model props" in `wynn-rp-blocks.md`).
- Photon's colored-light volume voxelizes terrain only (`is_voxelized` in `voxelization.glsl` checks the render stage;
  `shadow_entities` skips it).
- **With Photonics, the light volume isn't used at all**: Photon takes block light from Photonics' traced lights
  (`PHOTONICS_DIFFUSE` in `diffuse_lighting.glsl`). Photonics only knows blocks: its mixins all hook chunk meshing.
  So an entity light written into the volume would be invisible in the profile's own setup.

## What this branch does

1. **The mod tags the spell model** (`client/SpellTags.java`, `client/mixin/ItemLayerSpellMixin.java`). In 1.21.11
   an item model is drawn one layer at a time by `ItemStackRenderState.LayerRenderState.submit`. Iris (and WynnIris)
   set the item ID at its head, and Iris copies it into the item node when `submitItem` is called (`MixinItemSubmit`).
   The mixin runs just before that call: if one of the layer's quads uses a texture in the spell table
   (`src/main/resources/wynn_photon/spells.txt`: by name, or by a hash of its pixels), it sets that spell kind's ID
   (10128-10159) instead. Matching sprite names
   is exact and survives `custom_model_data` renumbering. Iris is reached by reflection, so the mod still runs
   without it. This is the same spot where WynnIris looks at a layer's sprites for its skybox signal.
2. **Glow** (`material.glsl`, masks 128-159). `get_material_mask` keeps a spell's item ID on any entity. Each spell
   kind has a cut on saturation and lightness that picks its fire out of its rock (`wynn_spell_glow_cut` in
   `wynn_spells.glsl`). For the named meteors the rock's saturation is at most 0.19, the fire's and cracks' at least
   0.31, so the cut is at 0.22-0.30.
3. **Light** (`include/misc/wynn_spells.glsl`). The entity vertex shaders add every spell vertex's position to
   `wynn_spell_img`, a 128×1 `r32i` image Iris clears every frame (`imageAtomicAdd`, in 1/16 blocks): 16 slots of 8
   texels, the slot picked by a hash of the spell kind and the vertex's 8-block world cell, so casts apart get their
   own light. Deferred shading takes each slot's average as a point light, with the player light's falloff, after
   merging slots of one kind less than 6 blocks apart (one spell across a cell edge). It works with and without
   Photonics, because it is added next to the player light in both paths. It casts no shadows.

`WYNN_SPELL_LIGHT_I` (Wynn-Photon page, "Spell Lights (Experimental)") scales both; 0 turns them off.

## Tested

`gradlew lightTest -Pcases=testworld/spell_test.txt` photographs the two still meteors that `spells.mcfunction`
puts north of the light test area (`function wynn_photon_test:meteor_fall` drops one in a loop, animated with
interpolation like Wynncraft's). In the dev client both meteors glow, only on the flame and cracks, and light the
stone pad orange and purple, with Photonics on and off. At the first strength (3 torches) the scene was 3× brighter
at midnight (6× up close) and the light tinted the whole pad, which was too much. At 1.5 torches, the default now, it
is about 2.2-2.4× and pools around each meteor.

**On live Wynncraft (2026-11-22), Dark Wizard's Meteor glows and lights its surroundings, and looks great**, with
the profile's own setup (WynnIris 1.2.2, Photonics 0.3.4, Voxy). See "Found on live" below for how it was found. What
that settles and what is still open:

- **Draw order: fine.** Iris draws spell parts in the solid entity pass, before deferred shading, so the light of the
  same frame is there. (Had they been drawn in the translucent pass, after deferred shading, only the glow would have
  shown.)
- **A rig of moving parts works.** The light sits at the average of every tagged part's vertices, which on live is
  close enough to the fire.
- **WynnIris** compiled the edited entity programs and kept its own effects.
- **Open:** the named meteors (`meteor_0`, `meteor_1`) are only tested in the dev client; Wynncraft didn't draw
  them for Dark Wizard. Mage's own Meteor is a rig too (see "The survey from the server pack"). Several meteors of
  one kind used to share one light at their middle; since the light slots ("What this branch does") each cast gets its own.

## The survey from the server pack (2026-11-22)

Done offline, before any playtest, against the 2026-11-21 pack (`aeac8911…`), with the 2026-10-08 one (`03ff36a5…`,
before the reskin update) to compare. The scripts are in `tools/spell-survey/survey.py`.

**How.** Every `oak_boat` model (`custom_model_data`) is mapped to its textures and their pixel hashes, the same CRC-32
the mod's spell table uses. The hashes are checked against the live log: Dark Wizard's Meteor comes out `09101c92`.
Then two signals:

- **Twins.** A reskin's spell is the base class's rig with its texture recoloured: the same elements, the same UVs,
  another picture. Grouping textures whose models have identical geometry (texture slot names left out) finds such
  pairs. 85 groups have at least one texture that is new since October.
- **New art.** 575 textures are new since October (93 went). The `custom_model_data` numbers can't be compared
  between packs (only 64 of 71,709 kept their model), but the pixel hashes can.

**What it found.**

- **Mage's live Meteor is a rig too**, not the named `meteor_0` model: `custom_model_data` 20388-20391, texture
  `57f6d84a`, the same four parts as Dark Wizard's 20392-20395 with orange fire. It was already in the October pack.
  So base classes use rigs as well, and the named `wynn/spell/` models are probably leftovers. It is in the spell table
  now (kind 2), glowing on its fire and cracks only (saturation 0.45-0.60 and lightness 0.25-0.40; its rock is dark
  red-brown up to 0.4 saturated). Dark Wizard's softer cut would light its rock.
- **The Meteor has more parts**, right after its rig, each in an orange and a pink version: a fire trail (20396/20398),
  sparks (20400-20409, two textures on the same models), a ring (20411/20413), squares (20424-20449) and a beam
  (20450/20451).
- **Each reskin recolours its class's art one way**, which sorts most twin groups by class (a guess until the
  playtest names them):

  | Class (reskin) | Recolour | Twin groups (`custom_model_data` of the base version) |
  | --- | --- | --- |
  | Mage (Dark Wizard) | orange and cyan to pink and magenta | Meteor 20388-20451; icicles 33001 (5 parts, Ice Snake?); beams 32173, 18078, 31950; a cross 32175; a streak 23957; sparks 13615; glyph pages 33676-33715 |
  | Archer (Hunter) | green and yellow to indigo and purple | arrows 1576-1635 (six base colours, two new); a star 1578; arcs 1594 (34 parts); 1586; glyphs 13475; waves 22726 |
  | Warrior (Knight) | red and orange to grey | 4084 (12 parts); a frame 4108; a fire burst 4218; 4248; 35087 (18 parts); 35249 |
  | Assassin (Ninja) | green and yellow to cyan; indigo to pink | sparkle crosses 34215 (28 parts); slashes 34620 (78 parts); 34602, 34610, 34753 |
  | Shaman (Skyseer) | green to teal, cyan or gold | circles 33722; arcs 33728; 28892; 33759 |

- **Candidates.** The Mage and Dark Wizard groups are in `spells.txt` as candidates (`?` lines), with three new kinds:
  4 for Mage's cyan ice and light, 5 for Dark Wizard's magenta energy, 6 for Mage's orange energy. Only the debug build
  uses them, so a playtest shows them glowing and its log says which spell each belongs to.

What the pack can't say: which spell a group belongs to, which groups are spells at all (some may be weapons, mobs or
cosmetics recoloured the same way), and which parts the server shows together. The playtest's captures answer that.

## Survey tooling (debug build)

With a file named `wynn-photon-spell-debug` in the config folder, the mod (`client/SpellDebug.java`):

- logs every spell Wynntils sees cast, with the class and whether it is the reskin (Wynntils' `SpellEvent.Cast`,
  reached by reflection; without Wynntils, captures still start on their own);
- **captures** what follows: every display entity and armor stand within 24 blocks, when it appears (its full data:
  item and `custom_model_data`, transformation, interpolation and teleport times), changes and goes, and the textures
  drawn on item models meanwhile, with their pixel hashes and spell table entries. A capture starts on a cast, or when
  a display entity appears within 12 blocks, and ends 3 seconds after the last change;
- writes each capture as a datapack function, `wynn_spell_capture:c<n>/start`, which replays it tick by tick where it
  is run, for the dev client and the screenshot tests;
- uses the spell table's candidates.

Everything goes to `wynn-photon-spells/` in the game folder (`spells-<time>.log` and `datapack/`).

## Limits and open questions

- **Scope.** This edits Photon's entity vertex shaders (`gbuffers_all_solid.vsh`, `gbuffers_all_translucent.vsh`),
  which `wynn-rp-blocks.md` says to leave to WynnIris. The edits are small and away from WynnIris's anchors, and the
  test ran with WynnIris, but it needs checking on Wynncraft. Spell glow is arguably WynnIris's area too: it would
  be a natural WynnIris feature (it already reads sprites at this exact spot).
- **Other spells** need only their texture names or pixel hashes in `SpellTags` and a light color. Every spell is
  modelled (confirmed in play), so all of them can work the way Meteor does. Particles a spell adds on top are drawn
  by the particle programs and can't be tagged.
- **Reskinned spells** have hashed texture names that change every release. They are matched by a hash of their
  pixels instead (below), but each one has to be found first, by casting it with the debug log on.
- **The plan for the other spells** is in [SPELL_LIGHTS_PLAN.md](../SPELL_LIGHTS_PLAN.md).

## Found on live: Dark Wizard's Meteor

The first live test, as Dark Wizard (the Mage reskin), showed no light: Wynncraft never drew `wynn/spell/meteor_*`.
A debug log (a file named `wynn-photon-spell-debug` in the config folder makes the mod log every new texture drawn on
item models near the camera) found the reskin's texture: `item/w-672617585612035381`, a 128×512 animated sheet of
dark purple rock and magenta fire. It is used by four tiny `oak_boat` models, `custom_model_data` 20392-20395, each a
fraction of a block: **reskinned spells are rigs of small parts that the server scales and moves**, one display entity
per part.

Hashed names change every release, so the mod matches this texture by a CRC-32 of its pixels (`SpellTags.pixelHash`,
`0x09101c92`), which survives renaming as long as the art stays the same. The debug log prints each texture's hash.
It got its own ID, 10108 (now 10131, spell kind 3), with a magenta light. Its rock is dark but up to 0.9 saturated, so the glow also needs a
lightness of at least 0.12-0.25; every fire pixel is at least 0.3 light. In the test world two of its parts, scaled up,
glow and light the pad magenta.

`meteor_1` (purple) exists in the pack but wasn't drawn for Dark Wizard on live.
