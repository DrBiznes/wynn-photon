# Experimental spell lights: Mage's Meteor, drawn the way Wynncraft draws it, as a potion item on an item display
# (custom_model_data 4039 is the orange meteor, 4031 the purple one). Needs the Wynncraft resource pack.
# Two hover still over open ground north of the light test area; /function wynn_photon_test:meteor_fall drops a
# third one in a loop. Runs from load once the chunks are loaded; run it by hand to put them back.
execute unless loaded 0 -61 96 run return run schedule function wynn_photon_test:spells 20t
kill @e[type=item_display,tag=wynn_spell_test]
fill -2 -61 92 24 -61 112 stone
summon item_display 4.5 -58 104.5 {Tags:["wynn_spell_test"],item_display:"head",Rotation:[0f,0f],item:{id:"minecraft:potion",count:1,components:{"minecraft:custom_model_data":{floats:[4039f]}}}}
summon item_display 16.5 -58 104.5 {Tags:["wynn_spell_test"],item_display:"head",Rotation:[0f,0f],item:{id:"minecraft:potion",count:1,components:{"minecraft:custom_model_data":{floats:[4031f]}}}}
# Dark Wizard's Meteor is a rig of four tiny oak_boat models (custom_model_data 20392-20395) that the server scales
# and moves; two of them, scaled up, stand in for it here
summon item_display 10.5 -59 108.5 {Tags:["wynn_spell_test"],item:{id:"minecraft:oak_boat",count:1,components:{"minecraft:custom_model_data":{floats:[20395f]}}},transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[48f,48f,48f]}}
summon item_display 10.5 -59 108.5 {Tags:["wynn_spell_test"],item:{id:"minecraft:oak_boat",count:1,components:{"minecraft:custom_model_data":{floats:[20394f]}}},transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[48f,48f,48f]}}
