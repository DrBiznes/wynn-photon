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
- **Don't redistribute anyone's assets.** Wynncraft's textures and models stay out of this repo. Research notes may
  describe them and name files, but never copy them.

## Status

- **0.1.0 (done):** the patcher mod, the patch workflow and the redstone torch → candle sconce light.

## Roadmap

### 1. Survey the Wynncraft resource pack (next, in its own session)

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

### 2. Material remaps (cheap wins)

Move blocks between Photon's existing categories in `block.properties`. Examples: something Wynn draws as metal moves
to "Metals", something drawn as wood or foliage gets wood specular or subsurface scattering, and a fake ore drawn as
plain stone loses its glow. These changes need no shader code.

### 3. Light sources

Give each remodelled light the color and strength of what Wynncraft actually draws, the way 0.1.0 handles the torch.
This covers colored lighting (`light_colors.glsl`), Photonics (`ph_lights.json`) and handheld light
(`item.properties`). Also look for Wynn props that should glow but don't, and vanilla lights that Wynncraft turned
into something that shouldn't glow.

### 4. Wynn-only materials

Some Wynn looks fit no Photon category, such as the torch's "only the flame glows" mask. These need new branches in
`material.glsl`. Photon's block ID ranges are full (10001-10096 are all used), so we first need a Wynn-Photon ID range.
Before picking one, check how many bits the material mask has in the gbuffers and in the Voxy shaders.

### 5. Mod and project polish

- Show an in-game notice when Photon is missing, not just a log line.
- A Mod Menu screen for the two config options.
- A GitHub repo with CI that runs `gradlew build`.
- Publishing, which needs SixthSurge's written permission for Modrinth. Asked on Discord 2026-11-16, waiting for a
  reply.
- Track new Photon releases (see README, "Updating to a new Photon release").

## Brainstorm

These ideas are not prioritized yet. Each one first needs checking against the actual pack.

**Custom block carriers.** The pack overrides blockstates for `note_block`, `red_mushroom_block`,
`brown_mushroom_block`, `mushroom_stem`, `tripwire` and `tripwire_hook`. Those are the usual blocks servers use to add
custom blocks, one look per block state. Photon's `block.properties` can match block states, so each Wynn custom block
could get its own material.

**Repurposed vanilla blocks.** The pack also overrides the infested stone variants, the ores (`coal_ore`,
`emerald_ore`, `redstone_ore`), `iron_bars`, `cauldron`, `daylight_detector`, `sticky_piston`, `bedrock`,
`netherrack`, and old slab ids. Some of these are probably decoration. For example, `redstone_ore:lit` glows red in
Photon today; is that what Wynn draws?

**Plants.** The pack overrides crops (`wheat`, `carrots`, `potatoes`, `beetroots`), `vine`, `short_grass`,
`sunflower` and `cobweb`. Check that their waving and subsurface scattering still fit the new models.

**Emissive pixels.** Does the pack mark glowing pixels in its textures, for example with an alpha value its core
shaders look for? If it does, Photon could read the same marker and get emission for every Wynn glowing texture at
once. That could be the biggest single win.

**Lost core-shader effects.** The pack ships its own core shaders. Iris doesn't use core shaders while a shader pack
is on, so any world effect Wynn does in them disappears under Photon. List those effects and decide which ones Photon
should recreate.

**Item-model props.** Wynn builds many decorations from item models on display entities and armor stands, about
4,000 of them. Photon's `item.properties` only matches vanilla item ids. Research whether Iris (or WynnIris) can match
item components or model ids. If not, those props can't get their own lights or materials.

**Entities and NPCs.** Use `entity.properties` for things like glowing eyes or lanterns that mobs and NPCs carry.

**Spell and particle light.** Spells could light their surroundings. This is hard and may need mod-side support;
WynnIris might be a better home for it.

**Voxy LODs.** Photon has Voxy shaders (`voxy_opaque.glsl`, `voxy.json`). Make sure material and light changes also
apply to distant terrain, so lights don't pop in or change at the LOD boundary.

**Add-on packs.** The user's stack includes WynnLanternRP, Wynnic Fables & Legends, Better Leaves and Wynnphony.
Find out which pack each remodel actually comes from (the torch model may come from WynnLanternRP). Decide whether
to support popular add-ons, possibly behind a shader option.

**labPBR.** The Wynn pack has no `_s`/`_n` maps for blocks. Photon's hardcoded specular is our main tool. A
companion labPBR resource pack for Wynn blocks is a much bigger alternative.

**Avoid overlap with WynnIris.** WynnIris already adds Wynn-specific features, such as Mist Woods fog and ambience
packs. Check what it covers so the two don't duplicate or fight each other.

## Open questions

- Which ID range is safe for Wynn-only materials?
- Should Wynn-Photon support add-on packs, or only the official server pack?
- Can Iris target custom item models at all?
