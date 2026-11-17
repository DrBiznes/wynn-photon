"""Generates the Wynn-Photon test gallery datapack functions. Usage: gen_gallery.py <function dir>"""
import os
import sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
NS = "wynn_photon_test"
G = -60  # ground level: superflat grass top is y = -61

build, labels, vines, lighttest = [], [], [], []


def sign(lines, x, y, z, rot, target):
    msgs = (list(lines) + ["", "", "", ""])[:4]
    m = ",".join('"%s"' % s for s in msgs)
    target.append(f"setblock {x} {y} {z} oak_sign[rotation={rot}]{{front_text:{{messages:[{m}]}},is_waxed:1b}}")


def b(x, y, z, block):
    build.append(f"setblock {x} {y} {z} {block}")


def v(x, y, z, state):
    vines.append(f"setblock {x} {y} {z} vine[{state}]")


def vs(e, n, s, w, up):
    t = lambda c: "true" if c == "t" else "false"
    return f"east={t(e)},north={t(n)},south={t(s)},west={t(w)},up={t(up)}"


# ---------------------------------------------------------------- clear and floor
build += [
    "# Clear the gallery area (three fills, each under the 32768-block command limit) and reset the floor",
    "fill -4 -60 -24 102 -50 -1 air",
    "fill -4 -60 0 102 -50 15 air",
    "fill -4 -60 16 102 -50 30 air",
    "fill -4 -61 -24 102 -61 30 grass_block",
    "",
    "# ---- Dark hall (z 16-26): light sources, one per cell, behind a baffle so no daylight gets in",
]

# ---------------------------------------------------------------- dark hall
CELLS = 17
X1 = 6 * CELLS - 1  # east outer wall
build += [
    "# Floor at y -61, roof at y -55, air inside",
    f"fill -1 -61 16 {X1} -55 26 stone_bricks hollow",
]
for i in range(CELLS - 1):
    x = 6 * i + 5
    build.append(f"fill {x} -60 21 {x} -56 25 stone_bricks")
build += [
    "# Entrance in the middle of the front wall, with a baffle wall just inside it",
    "fill 29 -60 16 30 -59 16 air",
    "fill 27 -60 18 32 -57 18 stone_bricks",
    "",
]


def cell(i):
    return 6 * i  # first interior x of cell i


def lever(x, z):
    return f"setblock {x} {G} {z} lever[face=floor,facing=north,powered=true]"


hall = []
# 0: standing sconce
c = cell(0)
hall.append(("Candle sconce", "redstone_torch", "LPV + Photonics", "warm orange"))
b(c + 2, G, 23, "redstone_torch")
# 1: wall sconce on the back wall
c = cell(1)
hall.append(("Wall sconce", "redstone_wall_torch", "", ""))
b(c + 2, G + 2, 25, "redstone_wall_torch[facing=north]")
# 2: lit candelabra
c = cell(2)
hall.append(("Candelabra", "repeater powered", "delay 1", "levers power it"))
b(c + 2, G, 23, "repeater[delay=1,facing=north,powered=true]")
build += [lever(c + 2, 22), lever(c + 2, 24)]
# 3: four-candle candelabra
c = cell(3)
hall.append(("4-candle", "candelabra", "own, brighter", "light"))
b(c + 2, G, 23, "repeater[delay=4,facing=north,powered=true]")
build += [lever(c + 2, 22), lever(c + 2, 24)]
# 4: unlit candelabras and an unlit candle (a lever powers the block under it)
c = cell(4)
hall.append(("Unlit candles", "repeater off x2", "redstone_torch", "lit=false"))
b(c, G, 23, "repeater[delay=1,facing=north,powered=false]")
b(c + 2, G, 23, "repeater[delay=4,facing=north,powered=false]")
b(c + 4, G, 24, "stone")
build.append(f"setblock {c + 3} {G} 24 lever[face=wall,facing=west,powered=true]")
b(c + 4, G + 1, 24, "redstone_torch[lit=false]")
# 5: spike traps in the dark, they must not glow
c = cell(5)
hall.append(("Spike traps", "comparator", "no light, even", "when powered"))
b(c, G, 23, "comparator[facing=north,powered=false]")
b(c + 2, G, 23, "comparator[facing=north,mode=subtract,powered=false]")
b(c + 4, G, 23, "comparator[facing=north,powered=true]")
build += [lever(c + 4, 22), lever(c + 4, 24)]
# 6: molten rock
c = cell(6)
hall.append(("Molten rock", "petrified_oak", "_slab double", "dim embers"))
build.append(f"fill {c + 1} -61 22 {c + 3} -61 24 petrified_oak_slab[type=double]")
b(c + 2, G, 23, "petrified_oak_slab[type=double]")
# 7: emeralds
c = cell(7)
hall.append(("Emeralds", "block, ore,", "deepslate ore", "faint green"))
b(c, G, 23, "emerald_block")
b(c, G + 1, 23, "emerald_block")
b(c + 2, G, 23, "emerald_ore")
b(c + 4, G, 23, "deepslate_emerald_ore")
# 8: spawner and rails, no light
c = cell(8)
hall.append(("Spawner, rails", "no light", "activator rail", "is powered"))
b(c, G, 23, "spawner")
b(c + 2, G, 23, "activator_rail[shape=north_south,powered=true]")
build.append(lever(c + 3, 23))
b(c + 4, G, 23, "detector_rail[shape=north_south]")
# 9: enchanting table (still Photon's purple light, shortlist item 10)
c = cell(9)
hall.append(("Enchanting table", "no purple", "light now", ""))
b(c + 2, G, 23, "enchanting_table")
# 10: references, unchanged Photon lights
c = cell(10)
hall.append(("Reference", "lantern, torch", "(unchanged)", ""))
b(c + 1, G, 23, "lantern")
b(c + 3, G, 23, "torch")
# 11: redstone dust (Wynncraft blood stains), no glow and no light at any power
c = cell(11)
hall.append(("Redstone dust", "no light, even", "at power 15", "back: power 0"))
for dx in range(3):
    b(c + dx, G, 23, "redstone_wire")
build.append(f"setblock {c + 3} {G} 23 lever[face=floor,facing=north,powered=true]")
b(c + 1, G, 25, "redstone_wire")
# 12: magma block
c = cell(12)
hall.append(("Magma block", "own ID, red,", "a bit dimmer", "cracks glow"))
build.append(f"fill {c + 1} -61 22 {c + 3} -61 24 magma_block")
b(c + 2, G, 23, "magma_block")
# 13: glowstone
c = cell(13)
hall.append(("Glowstone", "own ID,", "pale yellow", "light"))
b(c + 2, G, 23, "glowstone")
# 14: beacon (a beacon gives light without a pyramid)
c = cell(14)
hall.append(("Beacon", "pale teal", "light", "(was blue)"))
b(c + 2, G, 23, "beacon")
# 15: chests, a faint glow so they show in dark caves (Photonics only for their light: block entities)
c = cell(15)
hall.append(("Chests", "faint glow", "chest, trapped", ""))
b(c + 1, G, 23, "chest[facing=south]")
b(c + 3, G, 23, "trapped_chest[facing=south]")
# 16: invisible light blocks, as Wynncraft lights its caves. No light system sees them, so they light only through
# the vanilla light fallback (Light Block Strength). Without it, Photonics left this cell black
c = cell(16)
hall.append(("Light blocks", "invisible,", "levels 15, 10", "vanilla fallback"))
b(c + 1, G + 2, 23, "light[level=15]")
b(c + 3, G, 25, "light[level=10]")

for i, lines in enumerate(hall):
    sign(lines, cell(i) + 2, G, 21, 8, labels)

# ---------------------------------------------------------------- outdoor materials
build += ["", "# ---- Daylight row (z -12 to -6): materials, specular and SSS"]
row = []
Z = -8


def label(x, *lines):
    sign(lines, x, G, -6, 0, labels)


# nether bricks: matte now
build.append(f"fill 0 -61 -9 2 -61 -7 nether_bricks")
b(0, G, Z, "nether_brick_stairs[facing=south]")
b(1, G, Z, "nether_bricks")
b(1, G + 1, Z, "cracked_nether_bricks")
b(1, G + 2, Z, "chiseled_nether_bricks")
b(2, G, Z, "nether_brick_slab[type=bottom]")
label(1, "Nether bricks", "matte now", "(was glossy)")
# obsidian: keeps its gloss
build.append(f"fill 4 -61 -9 6 -61 -7 obsidian")
b(5, G, Z, "obsidian")
b(5, G + 1, Z, "obsidian")
label(5, "Obsidian", "faint sheen", "(was glossy)")
# emeralds
b(8, G, Z, "emerald_block")
b(8, G + 1, Z, "emerald_block")
b(9, G, Z, "emerald_ore")
b(10, G, Z, "deepslate_emerald_ore")
label(9, "Emeralds", "gem shine +", "faint glow")
# spike traps: transparent metal
b(12, G, Z, "comparator[facing=north,powered=false]")
b(13, G, Z, "comparator[facing=north,mode=subtract,powered=false]")
b(14, G, Z, "comparator[facing=north,powered=true]")
build += [lever(14, Z - 1), lever(14, Z + 1)]
label(13, "Spike traps", "metal, no glow", "raised/blood/", "retracted")
# molten rock and dark cobblestone slabs
build.append(f"fill 16 -61 -9 18 -61 -7 petrified_oak_slab[type=double]")
b(17, G, Z, "petrified_oak_slab[type=double]")
b(16, G, Z - 2, "petrified_oak_slab[type=bottom]")
b(18, G, Z - 2, "petrified_oak_slab[type=top]")
label(17, "Molten rock", "+ dark cobble", "slabs (behind)", "plain stone")

# vine carrier states (placed last, in vines.mcfunction, see the note there)
label(20, "Chain", "metal", "vine state")
for y in range(3):
    v(20, G + y, Z, vs("f", "t", "t", "f", "f"))
label(22, "Rope", "weak SSS", "vine state")
for y in range(3):
    v(22, G + y, Z, vs("t", "f", "f", "t", "f"))
label(25, "Dead leaves", "leaves", "vine state")
for x, y in [(24, G), (26, G), (25, G + 1)]:
    v(x, y, Z, vs("t", "t", "f", "t", "f"))
label(29, "Murky water", "glossy puddle", "vine state")
for dx, dz in [(0, -1), (2, -1), (1, 0), (0, 1), (2, 1)]:
    v(28 + dx, G, Z + dz, vs("t", "t", "t", "f", "f"))
label(33, "Dead grass", "+ flowers", "small plants")
for dx, dz in [(0, -1), (2, -1), (1, 0), (0, 1), (2, 1)]:
    v(32 + dx, G, Z + dz, vs("t", "t", "t", "t", "f"))
label(37, "Cattails", "small plants", "vine state")
# The cattail model spans y -16..16, so the vine sits one block up and the plant stands on the ground
for x, z in [(36, Z), (38, Z), (37, Z + 1)]:
    v(x, G + 1, z, vs("f", "f", "f", "f", "f"))
label(41, "Hanging crops", "small plants", "vine state")
build.append(f"fill 40 -57 {Z} 42 -57 {Z} oak_planks")
for x in (40, 42):
    v(x, G + 2, Z, vs("t", "t", "t", "t", "t"))
label(45, "Real vine", "strong SSS", "(unchanged)")
build.append(f"fill 44 -60 {Z - 1} 46 -58 {Z - 1} stone_bricks")
for x in (44, 46):
    for y in range(3):
        v(x, G + y, Z, "north=true")

# crops: several ages are 2-tall stacked quads, which used to split at the seam when they swayed
build.append(f"fill 48 -61 {Z - 3} 55 -61 {Z} farmland[moisture=7]")
for age in range(8):
    b(48 + age, G, Z, f"wheat[age={age}]")
    b(48 + age, G, Z - 1, f"carrots[age={age}]")
    b(48 + age, G, Z - 2, f"potatoes[age={age}]")
for age in range(4):
    b(48 + age, G, Z - 3, f"beetroots[age={age}]")
label(51, "Crops, age 0-7", "wheat carrots", "potatoes beets", "no sway seam")

# props drawn as other things: the books, crate and cauldron are wood now
b(58, G, Z, "iron_bars")
b(60, G, Z, "daylight_detector")
b(62, G, Z, "sticky_piston[facing=up]")
b(64, G, Z, "note_block")
b(66, G, Z, "cauldron")
label(62, "Props", "bars, books,", "crate, note,", "cauldron (wood)")
# iron trapdoors: no metal reflections
b(68, G, Z, "iron_trapdoor[half=bottom,open=false]")
b(69, G, Z, "iron_trapdoor[half=top,open=false]")
b(70, G, Z, "iron_trapdoor[facing=north,half=bottom,open=true]")
label(69, "Iron trapdoors", "no metal", "reflections")
# quartz: polished stone, not a gem mirror
build.append(f"fill 72 -61 -9 74 -61 -7 quartz_block")
b(72, G, Z, "quartz_pillar")
b(73, G, Z, "quartz_block")
b(73, G + 1, Z, "chiseled_quartz_block")
b(74, G, Z, "smooth_quartz")
label(73, "Quartz", "polished stone", "(was a mirror)")
# iron block: a bit rougher; gold next to it is unchanged
b(76, G, Z, "iron_block")
b(76, G + 1, Z, "iron_block")
b(78, G, Z, "gold_block")
b(78, G + 1, Z, "gold_block")
label(77, "Iron block", "a bit rougher", "gold unchanged")
# redstone dust as blood stains
for x in (80, 81):
    b(x, G, Z, "redstone_wire")
build.append(f"setblock 82 {G} {Z} lever[face=floor,facing=north,powered=true]")
label(81, "Redstone dust", "no glow,", "powered too")
# magma
build.append(f"fill 84 -61 -9 86 -61 -7 magma_block")
b(85, G, Z, "magma_block")
label(85, "Magma block", "only the", "cracks glow")
# unlit candle props: dark iron is metal, the wax gets weak SSS
b(88, G, Z, "repeater[delay=1,facing=north,powered=false]")
b(89, G, Z, "repeater[delay=4,facing=north,powered=false]")
b(91, G, Z, "stone")
build.append(f"setblock 90 {G} {Z} lever[face=wall,facing=west,powered=true]")
b(91, G + 1, Z, "redstone_torch[lit=false]")
label(89, "Unlit candles", "iron metal,", "wax weak SSS")
# chests: wood, with a faint glow
b(93, G, Z, "chest[facing=south]")
b(94, G, Z, "trapped_chest[facing=south]")
label(93, "Chests", "wood +", "faint glow")

# ---------------------------------------------------------------- light test spots
# Seven lighting situations south of the dark hall (z 40-80), photographed by `gradlew lightTest`. The camera
# positions are in testworld/light_test.txt: keep the two in step.
lighttest += [
    "# Clear the light test area and reset the floor",
    "fill -4 -60 40 102 -50 59 air",
    "fill -4 -60 60 102 -50 80 air",
    "fill -4 -61 40 102 -61 80 grass_block",
    "# Open ground: three reference blocks in the sun",
    "setblock 5 -60 54 stone",
    "setblock 6 -60 54 white_concrete",
    "setblock 7 -60 54 oak_planks",
    "# Tree canopy: leaves three thick over 21 x 21 blocks, open at the sides",
    "fill 16 -55 44 36 -53 64 oak_leaves[persistent=true]",
    "fill 26 -60 54 26 -56 54 oak_log",
    "fill 23 -60 56 23 -56 56 oak_log",
    "fill 30 -60 58 30 -56 58 oak_log",
    "# Stone overhang: 13 wide, 4 high, 13 deep, open to the north",
    "fill 40 -61 46 54 -56 60 stone hollow",
    "fill 41 -60 46 53 -57 46 air",
    "# Room with one 1 x 1 window in the north wall",
    "fill 58 -61 46 66 -56 54 stone hollow",
    "setblock 62 -58 46 air",
    "# Sealed room lit by one invisible light block",
    "fill 70 -61 46 78 -56 54 stone hollow",
    "setblock 74 -58 50 light[level=15]",
    "# Sealed room lit by one torch",
    "fill 82 -61 46 90 -56 54 stone hollow",
    "setblock 86 -60 51 torch",
    "# Cave mouth: a 3 x 3 tunnel 16 blocks into solid stone, open to the north",
    "fill 2 -61 62 8 -56 78 stone",
    "fill 4 -60 62 6 -58 77 air",
]
for x, name in [(6, "Open ground"), (26, "Tree canopy"), (47, "Stone overhang"), (62, "Window room"),
                (74, "Light block room"), (86, "Torch room")]:
    sign(("Light test", name), x, G, 42, 8, labels)
sign(("Light test", "Cave mouth"), 9, G, 61, 8, labels)

build += [
    "",
    "# Light test spots, in their own function",
    f"function {NS}:lighttest",
]

build += [
    "",
    "# Signs, then the vine states last: any block placed next to a vine afterwards would strip its faces",
    f"function {NS}:labels",
    f"function {NS}:vines",
]

header = "# Generated for the Wynn-Photon test world. Rebuilds the whole gallery.\n"
with open(os.path.join(out, "build_now.mcfunction"), "w", newline="\n") as f:
    f.write(header + "\n".join(build) + "\n")
with open(os.path.join(out, "lighttest.mcfunction"), "w", newline="\n") as f:
    f.write("# The lighting spots that gradlew lightTest photographs (see testworld/light_test.txt).\n"
            + "\n".join(lighttest) + "\n")
with open(os.path.join(out, "labels.mcfunction"), "w", newline="\n") as f:
    f.write("# Signs naming each exhibit. Kept apart so a sign syntax error can't stop the build.\n"
            + "\n".join(labels) + "\n")
with open(os.path.join(out, "vines.mcfunction"), "w", newline="\n") as f:
    f.write(
        "# Wynncraft's vine carrier states. A vine recomputes its faces whenever a neighbouring block changes, and\n"
        "# a vine with no supported face turns to air, so these are placed last, bottom to top, and never next to\n"
        "# each other sideways. Breaking or placing a block beside one will change or remove it: run\n"
        f"# /function {NS}:build to put everything back.\n"
        + "\n".join(vines) + "\n")
print(len(build), len(labels), len(vines))
