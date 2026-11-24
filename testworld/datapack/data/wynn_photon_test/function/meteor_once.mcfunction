# Experimental spell lights: one orange Meteor that appears 8 blocks up, falls onto the stone pad in 1.5 seconds and
# vanishes a second later, like a cast. The spell test's meteor_capture spot runs it to test the spell debug capture.
kill @e[type=item_display,tag=wynn_meteor_once]
summon item_display 10.5 -52 100.5 {Tags:["wynn_spell_test","wynn_meteor_once"],item_display:"head",Rotation:[0f,0f],item:{id:"minecraft:potion",count:1,components:{"minecraft:custom_model_data":{floats:[4039f]}}}}
schedule function wynn_photon_test:meteor_once_drop 2t
schedule function wynn_photon_test:meteor_once_end 50t
