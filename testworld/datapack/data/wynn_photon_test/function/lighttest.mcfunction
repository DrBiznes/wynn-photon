# The lighting spots that gradlew lightTest photographs (see testworld/light_test.txt).
# Clear the light test area and reset the floor
fill -4 -60 40 102 -50 59 air
fill -4 -60 60 102 -50 80 air
fill -4 -61 40 102 -61 80 grass_block
# Open ground: three reference blocks in the sun
setblock 5 -60 54 stone
setblock 6 -60 54 white_concrete
setblock 7 -60 54 oak_planks
# Tree canopy: leaves three thick over 21 x 21 blocks, open at the sides
fill 16 -55 44 36 -53 64 oak_leaves[persistent=true]
fill 26 -60 54 26 -56 54 oak_log
fill 23 -60 56 23 -56 56 oak_log
fill 30 -60 58 30 -56 58 oak_log
# Stone overhang: 13 wide, 4 high, 13 deep, open to the north
fill 40 -61 46 54 -56 60 stone hollow
fill 41 -60 46 53 -57 46 air
# Room with one 1 x 1 window in the north wall
fill 58 -61 46 66 -56 54 stone hollow
setblock 62 -58 46 air
# Sealed room lit by one invisible light block
fill 70 -61 46 78 -56 54 stone hollow
setblock 74 -58 50 light[level=15]
# Sealed room lit by one torch
fill 82 -61 46 90 -56 54 stone hollow
setblock 86 -60 51 torch
# Cave mouth: a 3 x 3 tunnel 16 blocks into solid stone, open to the north
fill 2 -61 62 8 -56 78 stone
fill 4 -60 62 6 -58 77 air
