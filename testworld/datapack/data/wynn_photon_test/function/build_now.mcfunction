# Generated for the Wynn-Photon test world. Rebuilds the whole gallery.
# Clear the gallery area (three fills, each under the 32768-block command limit) and reset the floor
fill -4 -60 -24 110 -50 -1 air
fill -4 -60 0 110 -50 15 air
fill -4 -60 16 110 -50 30 air
fill -4 -61 -24 110 -61 30 grass_block

# ---- Dark hall (z 16-26): light sources, one per cell, behind a baffle so no daylight gets in
# Floor at y -61, roof at y -55, air inside
fill -1 -61 16 107 -55 26 stone_bricks hollow
fill 5 -60 21 5 -56 25 stone_bricks
fill 11 -60 21 11 -56 25 stone_bricks
fill 17 -60 21 17 -56 25 stone_bricks
fill 23 -60 21 23 -56 25 stone_bricks
fill 29 -60 21 29 -56 25 stone_bricks
fill 35 -60 21 35 -56 25 stone_bricks
fill 41 -60 21 41 -56 25 stone_bricks
fill 47 -60 21 47 -56 25 stone_bricks
fill 53 -60 21 53 -56 25 stone_bricks
fill 59 -60 21 59 -56 25 stone_bricks
fill 65 -60 21 65 -56 25 stone_bricks
fill 71 -60 21 71 -56 25 stone_bricks
fill 77 -60 21 77 -56 25 stone_bricks
fill 83 -60 21 83 -56 25 stone_bricks
fill 89 -60 21 89 -56 25 stone_bricks
fill 95 -60 21 95 -56 25 stone_bricks
fill 101 -60 21 101 -56 25 stone_bricks
# Entrance in the middle of the front wall, with a baffle wall just inside it
fill 29 -60 16 30 -59 16 air
fill 27 -60 18 32 -57 18 stone_bricks

setblock 2 -60 23 redstone_torch
setblock 8 -58 25 redstone_wall_torch[facing=north]
setblock 14 -60 23 repeater[delay=1,facing=north,powered=true]
setblock 14 -60 22 lever[face=floor,facing=north,powered=true]
setblock 14 -60 24 lever[face=floor,facing=north,powered=true]
setblock 20 -60 23 repeater[delay=4,facing=north,powered=true]
setblock 20 -60 22 lever[face=floor,facing=north,powered=true]
setblock 20 -60 24 lever[face=floor,facing=north,powered=true]
setblock 24 -60 23 repeater[delay=1,facing=north,powered=false]
setblock 26 -60 23 repeater[delay=4,facing=north,powered=false]
setblock 28 -60 24 stone
setblock 27 -60 24 lever[face=wall,facing=west,powered=true]
setblock 28 -59 24 redstone_torch[lit=false]
setblock 30 -60 23 comparator[facing=north,powered=false]
setblock 32 -60 23 comparator[facing=north,mode=subtract,powered=false]
setblock 34 -60 23 comparator[facing=north,powered=true]
setblock 34 -60 22 lever[face=floor,facing=north,powered=true]
setblock 34 -60 24 lever[face=floor,facing=north,powered=true]
fill 37 -61 22 39 -61 24 petrified_oak_slab[type=double]
setblock 38 -60 23 petrified_oak_slab[type=double]
setblock 42 -60 23 emerald_block
setblock 42 -59 23 emerald_block
setblock 44 -60 23 emerald_ore
setblock 46 -60 23 deepslate_emerald_ore
setblock 48 -60 23 spawner
setblock 50 -60 23 activator_rail[shape=north_south,powered=true]
setblock 51 -60 23 lever[face=floor,facing=north,powered=true]
setblock 52 -60 23 detector_rail[shape=north_south]
setblock 56 -60 23 enchanting_table
setblock 61 -60 23 lantern
setblock 63 -60 23 torch
setblock 66 -60 23 redstone_wire
setblock 67 -60 23 redstone_wire
setblock 68 -60 23 redstone_wire
setblock 69 -60 23 lever[face=floor,facing=north,powered=true]
setblock 67 -60 25 redstone_wire
fill 73 -61 22 75 -61 24 magma_block
setblock 74 -60 23 magma_block
setblock 80 -60 23 glowstone
setblock 86 -60 23 beacon
setblock 91 -60 23 chest[facing=south]
setblock 93 -60 23 trapped_chest[facing=south]
setblock 97 -58 23 light[level=15]
setblock 99 -60 25 light[level=10]
setblock 104 -60 23 lava_cauldron

# ---- Daylight row (z -12 to -6): materials, specular and SSS
fill 0 -61 -9 2 -61 -7 nether_bricks
setblock 0 -60 -8 nether_brick_stairs[facing=south]
setblock 1 -60 -8 nether_bricks
setblock 1 -59 -8 cracked_nether_bricks
setblock 1 -58 -8 chiseled_nether_bricks
setblock 2 -60 -8 nether_brick_slab[type=bottom]
fill 4 -61 -9 6 -61 -7 obsidian
setblock 5 -60 -8 obsidian
setblock 5 -59 -8 obsidian
setblock 8 -60 -8 emerald_block
setblock 8 -59 -8 emerald_block
setblock 9 -60 -8 emerald_ore
setblock 10 -60 -8 deepslate_emerald_ore
setblock 12 -60 -8 comparator[facing=north,powered=false]
setblock 13 -60 -8 comparator[facing=north,mode=subtract,powered=false]
setblock 14 -60 -8 comparator[facing=north,powered=true]
setblock 14 -60 -9 lever[face=floor,facing=north,powered=true]
setblock 14 -60 -7 lever[face=floor,facing=north,powered=true]
fill 16 -61 -9 18 -61 -7 petrified_oak_slab[type=double]
setblock 17 -60 -8 petrified_oak_slab[type=double]
setblock 16 -60 -10 petrified_oak_slab[type=bottom]
setblock 18 -60 -10 petrified_oak_slab[type=top]
fill 40 -57 -8 42 -57 -8 oak_planks
fill 44 -60 -9 46 -58 -9 stone_bricks
fill 48 -61 -11 55 -61 -8 farmland[moisture=7]
setblock 48 -60 -8 wheat[age=0]
setblock 48 -60 -9 carrots[age=0]
setblock 48 -60 -10 potatoes[age=0]
setblock 49 -60 -8 wheat[age=1]
setblock 49 -60 -9 carrots[age=1]
setblock 49 -60 -10 potatoes[age=1]
setblock 50 -60 -8 wheat[age=2]
setblock 50 -60 -9 carrots[age=2]
setblock 50 -60 -10 potatoes[age=2]
setblock 51 -60 -8 wheat[age=3]
setblock 51 -60 -9 carrots[age=3]
setblock 51 -60 -10 potatoes[age=3]
setblock 52 -60 -8 wheat[age=4]
setblock 52 -60 -9 carrots[age=4]
setblock 52 -60 -10 potatoes[age=4]
setblock 53 -60 -8 wheat[age=5]
setblock 53 -60 -9 carrots[age=5]
setblock 53 -60 -10 potatoes[age=5]
setblock 54 -60 -8 wheat[age=6]
setblock 54 -60 -9 carrots[age=6]
setblock 54 -60 -10 potatoes[age=6]
setblock 55 -60 -8 wheat[age=7]
setblock 55 -60 -9 carrots[age=7]
setblock 55 -60 -10 potatoes[age=7]
setblock 48 -60 -11 beetroots[age=0]
setblock 49 -60 -11 beetroots[age=1]
setblock 50 -60 -11 beetroots[age=2]
setblock 51 -60 -11 beetroots[age=3]
setblock 58 -60 -8 iron_bars
setblock 60 -60 -8 daylight_detector
setblock 62 -60 -8 sticky_piston[facing=up]
setblock 64 -60 -8 note_block
setblock 66 -60 -8 cauldron
setblock 68 -60 -8 iron_trapdoor[half=bottom,open=false]
setblock 69 -60 -8 iron_trapdoor[half=top,open=false]
setblock 70 -60 -8 iron_trapdoor[facing=north,half=bottom,open=true]
fill 72 -61 -9 74 -61 -7 quartz_block
setblock 72 -60 -8 quartz_pillar
setblock 73 -60 -8 quartz_block
setblock 73 -59 -8 chiseled_quartz_block
setblock 74 -60 -8 smooth_quartz
setblock 76 -60 -8 iron_block
setblock 76 -59 -8 iron_block
setblock 78 -60 -8 gold_block
setblock 78 -59 -8 gold_block
setblock 80 -60 -8 redstone_wire
setblock 81 -60 -8 redstone_wire
setblock 82 -60 -8 lever[face=floor,facing=north,powered=true]
fill 84 -61 -9 86 -61 -7 magma_block
setblock 85 -60 -8 magma_block
setblock 88 -60 -8 repeater[delay=1,facing=north,powered=false]
setblock 89 -60 -8 repeater[delay=4,facing=north,powered=false]
setblock 91 -60 -8 stone
setblock 90 -60 -8 lever[face=wall,facing=west,powered=true]
setblock 91 -59 -8 redstone_torch[lit=false]
setblock 93 -60 -8 chest[facing=south]
setblock 94 -60 -8 trapped_chest[facing=south]
setblock 96 -60 -8 piston[facing=up]
setblock 97 -60 -8 tnt
fill 99 -61 -9 101 -61 -7 purpur_block
setblock 99 -60 -8 purpur_pillar
setblock 100 -60 -8 purpur_block
setblock 100 -59 -8 purpur_slab[type=bottom]
setblock 101 -60 -8 purpur_stairs[facing=south]
setblock 103 -60 -8 water_cauldron[level=3]
setblock 104 -60 -8 powder_snow_cauldron[level=3]
setblock 106 -60 -8 iron_door[facing=north,half=lower]
setblock 106 -59 -8 iron_door[facing=north,half=upper]

# Light test spots, in their own function
function wynn_photon_test:lighttest

# Signs, then the vine states last: any block placed next to a vine afterwards would strip its faces
function wynn_photon_test:labels
function wynn_photon_test:vines
