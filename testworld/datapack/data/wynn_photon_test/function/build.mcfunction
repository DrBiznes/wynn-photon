# Rebuilds the gallery as soon as all of its chunks are loaded. Run it by hand to undo any changes.
execute if loaded -4 -60 -24 if loaded 96 -60 -24 if loaded -4 -60 80 if loaded 96 -60 80 run return run function wynn_photon_test:build_now
schedule function wynn_photon_test:build 10t
