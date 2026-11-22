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

- **Two kinds of spell art.** Some spells are **models**: items on display entities, which the mod can recognise by
  their textures and tag for the shader. The rest are mostly **particles** (vanilla particle types, often coloured
  dust), which the model approach can't see. Most base spells are probably particles. Which is which is the first thing
  the survey has to find out.
- **Base classes use named models**, `models/item/wynn/spell/` (Meteor, Ice Snake, Totem, Trap, the angel and the
  Shaman masks). Their texture names are stable.
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

**Tooling first** (an experimental build, never released):

- **Texture log.** Exists: with `config/wynn-photon-spell-debug`, the mod logs every new texture drawn on item models
  near the camera, with its size, animation, distance and pixel hash.
- **Cast markers.** Log a line when a spell is cast, so the textures that follow can be matched to it. Wynntils knows
  every cast (it parses the spell's click combo), so an optional hook into Wynntils' spell event would name the spell
  in the log. Without Wynntils, a debug key that writes a numbered mark is enough.
- **Animation capture.** For a few seconds after a cast, log every new display entity near the player: its item and
  `custom_model_data`, its transformation, and its interpolation and teleport times. That shows how each rig is put
  together and moves. Replayed as an `.mcfunction` in the test world, it gives a moving copy of the spell for the dev
  client and the screenshot tests.
- **Particle log.** Count the particle types and colours spawned near the player after a cast. That tells which
  particle spells could get light (see "Particle spells").

**A playtest session** goes like this:

1. Install the debug build. Stand somewhere quiet at night.
2. For each class, cast its four spells, a few seconds apart, as the base class and then the reskin.
3. Send the log, with anything that looked odd.

**The analysis** for each spell, in [research/wynn-spells.md](research/wynn-spells.md):

- what it is made of: models (which textures, models, items and `custom_model_data`), particles (which types and
  colours), or both;
- how it moves: still, a rig, a projectile, how long it stays;
- what should glow and what colour its light should be, or why it is skipped.

The result is a survey table like this one (Meteor filled in):

| Spell | Class | Art | Glow | Light | Status |
| --- | --- | --- | --- | --- | --- |
| Meteor | Mage | model `wynn/spell/meteor_0` (`meteor_0_a` fire) | fire | orange | dev client only |
| Meteor | Dark Wizard | rig of 4 `oak_boat` parts, texture hash `09101c92` | fire | magenta | live, done |

## Implementation

What a spell needs once it is surveyed:

- **Model spells:** an entry in the spell table (texture name or pixel hash → spell kind), a glow rule (which pixels
  glow, like the Meteor's saturation and lightness cut) and a light (colour, strength). The dev test world gets an
  exhibit of its parts, and `testworld/spell_test.txt` a shot of it.
- **Particle spells:** see below. Probably a later milestone.

Changes to the prototype before more spells go in:

- **Move the spell table to a data file** in the jar (texture names and pixel hashes → spell kind), so adding a spell
  needs no Java. The shader keeps one table of kinds (colour, strength, glow rule).
- **Reserve an ID range for spells.** The prototype uses 10106-10108, inside the materials range 10104-10127.
  Proposal: 10128-10159, 32 spell kinds. Check first that Photon's gbuffer stores material masks above 127 without loss.
- **Several casts at once.** Today all spells of one kind share one light at their middle, so two meteors light the
  ground between them. Give each kind a few slots and sort parts into them by rough position.
- **Lights that come and go.** Spells appear and vanish in a frame. A short fade, from the previous frame's light,
  would look less abrupt. Needs a small history image.

### Particle spells

Most spells are probably particles. Iris draws particles in `gbuffers_textured`, with no item ID. Options, to try in
this order once the survey shows which particles the spells use:

1. **Glow only.** Photon already makes some particles glow by colour. Coloured dust from spells could get the same.
2. **Light from particles.** Sum the positions of matching particles into the same light image the models use. That
   needs a way to tell a spell's particles from everyone else's (colour, size, particle type), which may not exist.
3. **Skip.** Many particle spells are fast flashes where a light wouldn't add much.

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
1. **Tooling.** Cast markers, animation capture and particle log in the debug build. Spell table in a data file,
   reserved ID range.
2. **Survey the 20 base spells**, base class and reskin, in one or two playtest sessions. Fill the survey table.
   Decide per spell: glow, light, or skip.
3. **Decide where it lives** (WynnIris or Wynn-Photon) and change the rules to match.
4. **Implement the model spells** class by class, each checked in the dev client and then on live. Several casts at
   once and fading go in here.
5. **Particle spells,** if the survey finds a workable way.
6. **First product release:** the base four spells of each class that make sense, with a "Spell Lights" toggle and
   strength on the Wynn-Photon page.
7. **Later: every spell.** Ability-tree nodes, archetypes, and new reskins as Wynncraft adds them. The pixel hashes
   need checking after each server pack update.

## Open questions

- Which base spells are models and which are particles? (Milestone 2.)
- Does Wynntils expose a spell-cast event a debug build can listen to without a hard dependency?
- Can the material mask hold IDs above 127?
- Does the WynnIris maintainer want spell tags in WynnIris?
- How often does Wynncraft change spell art? Each change breaks a pixel hash, which the debug log shows.
