# Spell lights plan

Make Wynncraft's spells glow and light up the world around them under Photon. The prototype, Mage's Meteor, works on
live Wynncraft (2026-11-22, branch `experimental/spell-lights`). How it works and what was learned is in
[research/wynn-spells.md](research/wynn-spells.md). This is the plan for the rest.

## Goal

- **First product: the four base spells of each class**, the ones every player has, in both the base class and its
  reskin. That's 20 spells, 40 looks:

  | Class (reskin) | Spell 1 | Spell 2 | Spell 3 | Spell 4 |
  | --- | --- | --- | --- | --- |
  | Mage (Dark Wizard) | Heal | Teleport | Meteor | Ice Snake |
  | Warrior (Knight) | Bash | Charge | Uppercut | War Scream |
  | Archer (Hunter) | Arrow Storm | Escape | Arrow Bomb | Arrow Shield |
  | Assassin (Ninja) | Spin Attack | Dash | Multihit | Smoke Bomb |
  | Shaman (Skyseer) | Totem | Haul | Aura | Uproot |

  Names are Wynntils' (`SpellType`, `ClassType`). Ships as one Wynn-Photon release once enough of them are done (see
  "Milestones").
- **Much later: every spell.** Ability-tree nodes and archetype spells (Shaman's masks, Archer's traps, ...), and every
  new reskin Wynncraft adds.
- **Skipping is fine.** Some spells have nothing that should glow or give light: a dash, a dodge, a smoke cloud.
  Those get recorded as "skip" with the reason, and nothing else.

## What we know

From the Meteor prototype and the 2026-11-21 server pack:

- **Every spell is modelled** (confirmed by the user from playing): items on display entities, which the mod can
  recognise by their textures and tag for the shader, the way it does Meteor. Particles may still be added on top,
  but they aren't the spell itself, so the model approach covers every spell.
- **Only a few spells have named models**, `models/item/wynn/spell/` (Meteor, Ice Snake, Totem, Trap, the angel and
  the Shaman masks), with stable texture names. Most spells, base classes included, are hashed rigs: Mage's own
  Meteor is one (20388-20391), the twin of Dark Wizard's, found in the pack survey.
- **The pack survey (2026-11-22)** sorted the recoloured rig pairs by class and put the Mage and Dark Wizard ones in
  the spell table as candidates; see "The survey from the server pack" in
  [research/wynn-spells.md](research/wynn-spells.md).
- **Reskins are rigs.** Dark Wizard's Meteor is four tiny `oak_boat` models (`custom_model_data` 20392-20395) that the
  server scales up and moves, one display entity per part. Their textures have hashed names (`item/w<n>`) that change
  every release, so the mod matches them by a hash of their pixels.
- **The animation is the server's.** Models don't move on their own. The server moves the display entities
  (interpolated transformations) and adds particles. Only textures animate on their own (`.mcmeta`).
- **We can't cast spells offline.** The dev client can show a spell's parts standing still, but not cast it. Every
  spell needs a live playtest.

## The survey (with playtesting)

For each spell: what it draws, how it moves, and whether it should glow or give light. The user plays, and the
analysis is done from the logs and the cached server pack.

**Tooling** (done, in the debug build: the `wynn-photon-spell-debug` file in the config folder; see "Survey tooling"
in [research/wynn-spells.md](research/wynn-spells.md)):

- **Texture log.** Every new texture drawn on item models near the camera, with its size, animation, distance, pixel
  hash and spell table entry, and again in each capture.
- **Cast markers.** Every cast Wynntils sees, with the spell, the class and base or reskin, from Wynntils'
  `SpellEvent.Cast` by reflection. Without Wynntils, captures start when a display entity appears near the player.
- **Animation capture.** After a cast, every display entity and armor stand near the player: when it appears (item,
  `custom_model_data`, transformation, interpolation and teleport times), changes and goes. Each capture is also
  written as a replayable datapack function, for the dev client and the screenshot tests.
- **Candidates.** The spell table's `?` lines, parts the pack survey found but nobody has seen cast, glow in the debug
  build only.

**A playtest session** goes like this:

1. Install the debug build, create an empty file named `wynn-photon-spell-debug` in the instance's `config` folder,
   and keep Wynntils on (it names the casts). Stand somewhere quiet at night, away from mobs and NPCs (their display
   entities start captures too).
2. For each class, cast its four spells, at least 5 seconds apart (a capture ends 3 seconds after the last change), as
   the base class and then the reskin.
3. Send `wynn-photon-spells/` from the instance folder (the log and the replay datapack), with anything that looked
   odd. Note which Mage candidates glowed and whether they looked right.

**The analysis** for each spell, in [research/wynn-spells.md](research/wynn-spells.md):

- what it is made of: which textures (names and pixel hashes), models, items and `custom_model_data`, and how many
  parts;
- how it moves: still, a rig, a projectile, how long it stays;
- what should glow and what colour its light should be, or why it is skipped.

The result is a survey table like this one (Meteor filled in):

| Spell | Class | Art | Glow | Light | Status |
| --- | --- | --- | --- | --- | --- |
| Meteor | Mage | rig of 4 `oak_boat` parts (20388-20391), texture hash `57f6d84a` | fire and cracks | orange | in the table, dev client only |
| Meteor | Dark Wizard | rig of 4 `oak_boat` parts (20392-20395), texture hash `09101c92` | fire | magenta | live, done |
| Meteor's trail, sparks, ring, squares, beam | Mage, Dark Wizard | 20396-20451, pairs of hashes | energy | orange, magenta | candidates |
| Ice Snake? | Mage, Dark Wizard | icicle rigs, 5 parts (33001, 33006) | ice | cyan, magenta | candidates |
| Heal? Teleport? | Mage, Dark Wizard | beams (32173/32174, 18078/18087, 31950), a cross (32175), a streak (23957) | energy | cyan or orange, magenta | candidates |
| The other 16 | | twin groups sorted by class in the research doc | | | survey |

## Implementation

What a spell needs once it is surveyed:

- **Model spells:** an entry in the spell table (texture name or pixel hash → spell kind), a glow rule (which pixels
  glow, like the Meteor's saturation and lightness cut) and a light (colour, strength). The dev test world gets an
  exhibit of its parts, and `testworld/spell_test.txt` a shot of it.

Changes to the prototype before more spells go in:

- **Move the spell table to a data file** (done): `src/main/resources/wynn_photon/spells.txt`, texture names and pixel
  hashes → spell kind. The shader keeps one table of kinds (glow cut, glow strength, light colour and strength) in
  `include/misc/wynn_spells.glsl`.
- **Reserve an ID range for spells** (done): 10128-10159, 32 spell kinds. The gbuffer stores the material mask in 8
  bits, so masks up to 255 survive; nothing else in Photon reads masks above 127 on entities.
- **Several casts at once** (done): 16 light slots, picked by spell kind and 8-block world cell; same-kind slots less
  than 6 blocks apart merge back into one light, so a spell across a cell edge isn't lit twice.
- **Lights that come and go.** Spells appear and vanish in a frame. A short fade, from the previous frame's light,
  would look less abrupt. Needs a small history image.

### Particles

Every spell is a model, so particles are only extras. Iris draws them in `gbuffers_textured`, with no item ID, so
they can't be tagged. Photon already makes some particles glow by colour; leave them at that unless a spell looks
wrong without its particles lit.

## Where the feature lives: WynnIris or Wynn-Photon

The prototype is split in two. The **mod** finds spell models and tags them for Iris. The **pack** turns the tag into
glow and light. Either half could live in WynnIris.

| | Build it into Wynn-Photon | Upstream to WynnIris |
| --- | --- | --- |
| Tagging spells | Our mixin, plus a reflection call into Iris's `CapturedRenderingState`, which can break when Iris or WynnIris changes | WynnIris already looks at item sprites at the same spot (for skyboxes); tagging would be a natural addition there |
| Glow | Our patch of Photon's material code | WynnIris lifts emissive pixels today, but without bloom or light. Real emission needs the pack |
| Light | Our patch of Photon's lighting, with and without Photonics | WynnIris would have to inject lighting into any shader pack. Hard, and pack by pack |
| Who benefits | Only Wynn-Photon players | Every pack on WynnIris could use the tags |
| Speed | We control it and release when ready | Depends on the WynnIris maintainer's time and interest |
| Project rules | Must change: "terrain only", "leave entity programs alone" (see below) | Unchanged for the tagging; the pack side still edits entity programs |

**Recommendation:** build the first product in Wynn-Photon, which is fast to iterate with playtests, but keep the
tagging half small and self-contained so it can move. Talk to the WynnIris maintainer early: describe the tags (which
IDs mean which spells, how a pack reads them) and offer to send a pull request once the format stops changing. If
WynnIris takes it, Wynn-Photon drops its mixin and keeps only the pack side. **This is the user's decision**, ideally
before milestone 3.

### Rule changes if it lives in Wynn-Photon

These need updating in `CLAUDE.md`, `docs/PROJECT_PLAN.md` ("Principles") and `research/wynn-rp-blocks.md` ("Scope"):

- **"Terrain only"** gets an exception: spell models (glow and light), like the sky exceptions.
- **"Leave Photon's entity programs alone"** gets an exception for the small spell additions in
  `gbuffers_all_solid.vsh`, `gbuffers_all_translucent.vsh` and `include/vertex/utility.glsl`, with a rule to test
  them with WynnIris running on live.
- **The mod becomes a runtime mod.** Today it only installs the pack at launch, plus toasts. It would also run a
  client mixin every frame and reach into Iris internals by reflection. Its description and README change too.
- **Every spell light works with and without Photonics**, like block lights. The point lights do already.

## Milestones

0. **Prototype (done).** Meteor, base and Dark Wizard. Works on live.
1. **Tooling (done, 2026-11-22).** Cast markers and animation capture in the debug build. Spell table in a data file,
   reserved ID range, several casts at once. The pack survey and Mage candidates.
2. **Survey the 20 base spells**, base class and reskin, in one or two playtest sessions. Fill the survey table.
   Decide per spell: glow, light, or skip.
3. **Decide where it lives** (WynnIris or Wynn-Photon) and change the rules to match.
4. **Implement the model spells** class by class, each checked in the dev client and then on live. Several casts at
   once and fading go in here.
5. **First product release:** the base four spells of each class that make sense, with a "Spell Lights" toggle and
   strength on the Wynn-Photon page.
6. **Later: every spell.** Ability-tree nodes, archetypes, and new reskins as Wynncraft adds them. The pixel hashes
   need checking after each server pack update.

## Open questions

- Which twin groups are which spells, and which parts the server shows together? (Milestone 2.)
- Answered: Wynntils' `SpellEvent.Cast` can be listened to by reflection. The material mask holds IDs up to 255.
- Does the WynnIris maintainer want spell tags in WynnIris?
- How often does Wynncraft change spell art? Each change breaks a pixel hash, which the debug log shows.
