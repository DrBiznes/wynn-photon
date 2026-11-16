package com.wynnphoton;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs before Minecraft starts so the patched pack exists, and iris.properties points at it,
 * before Iris reads its config. Touches no Minecraft classes.
 */
public final class WynnPhotonPreLaunch implements PreLaunchEntrypoint {
	public static final Logger LOGGER = LoggerFactory.getLogger("Wynn-Photon");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		try {
			PatchBundle bundle = PatchBundle.loadFromClasspath();
			WynnPhotonConfig config = WynnPhotonConfig.load(loader.getConfigDir());
			new PhotonInstaller(loader.getGameDir().resolve("shaderpacks"), loader.getConfigDir(), bundle, config).run();
		} catch (Throwable t) {
			LOGGER.error("Could not patch Photon; the game will start without the Wynn-Photon pack", t);
		}
	}
}
