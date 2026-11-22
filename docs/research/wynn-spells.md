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
   The mixin runs just before that call: if one of the layer's quads uses a known spell sprite
   (`wynn/spell/meteor_0_a` → 10106, `wynn/spell/meteor_1_a` → 10107), it sets that ID instead. Matching sprite names
   is exact and survives `custom_model_data` renumbering. Iris is reached by reflection, so the mod still runs
   without it. This is the same spot where WynnIris looks at a layer's sprites for its skybox signal.
2. **Glow** (`material.glsl`, masks 106 and 107). `get_material_mask` keeps a spell's item ID on any entity. Only
   saturated pixels glow: the rock's saturation is at most 0.19 in both variants, the fire's and cracks' at least
   0.31, so the cut is at 0.22-0.30.
3. **Light** (`include/misc/wynn_spells.glsl`). The entity vertex shaders add every spell vertex's position to
   `wynn_spell_img`, a 12×1 `r32i` image (four texels per spell kind) Iris clears every frame (`imageAtomicAdd`, in 1/16 blocks). Deferred shading
   takes the average per spell kind as a point light, with the player light's falloff. It works with and without
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
- **Open:** several meteors of one kind share one light at their middle. The regular Mage meteors (`meteor_0`,
  `meteor_1`) are only tested in the dev client; Wynncraft didn't draw them for Dark Wizard.

## Limits and open questions

- **Scope.** This edits Photon's entity vertex shaders (`gbuffers_all_solid.vsh`, `gbuffers_all_translucent.vsh`),
  which `wynn-rp-blocks.md` says to leave to WynnIris. The edits are small and away from WynnIris's anchors, and the
  test ran with WynnIris, but it needs checking on Wynncraft. Spell glow is arguably WynnIris's area too: it would
  be a natural WynnIris feature (it already reads sprites at this exact spot).
- **Other spells** need only their sprite names in `SpellTags` and a light color. Spells made of particles (most of
  them: Heal, Arrow Storm, Bash, ...) are drawn by the particle programs, not as item models, and would need
  another approach.
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
It gets its own ID, 10108, with a magenta light. Its rock is dark but up to 0.9 saturated, so the glow also needs a
lightness of at least 0.12-0.25; every fire pixel is at least 0.3 light. In the test world two of its parts, scaled up,
glow and light the pad magenta.

`meteor_1` (purple) exists in the pack but wasn't drawn for Dark Wizard on live.
