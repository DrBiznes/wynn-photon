# Runs when the world loads. Keeps the gallery chunks loaded, then rebuilds the gallery once they are.
forceload add -4 -24 110 80
schedule function wynn_photon_test:build 20t
# Experimental: the Meteor spell-light exhibit north of the light test area (spells.mcfunction)
forceload add -2 92 24 112
schedule function wynn_photon_test:spells 40t
