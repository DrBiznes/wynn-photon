# Runs when the world loads. Keeps the gallery chunks loaded, then rebuilds the gallery once they are.
forceload add -4 -24 110 80
schedule function wynn_photon_test:build 20t
