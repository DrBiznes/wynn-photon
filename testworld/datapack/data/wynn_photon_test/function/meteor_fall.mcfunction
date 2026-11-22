# Experimental spell lights: drops an orange Meteor from 12 blocks up onto the stone pad, every 2 seconds, with the
# display entity's interpolation (how Wynncraft animates its spell models). Stop it with
# /function wynn_photon_test:meteor_stop
kill @e[type=item_display,tag=wynn_meteor_fall]
summon item_display 10.5 -48 100.5 {Tags:["wynn_spell_test","wynn_meteor_fall"],item_display:"head",Rotation:[0f,0f],item:{id:"minecraft:potion",count:1,components:{"minecraft:custom_model_data":{floats:[4039f]}}}}
schedule function wynn_photon_test:meteor_fall_drop 2t
schedule function wynn_photon_test:meteor_fall 40t
