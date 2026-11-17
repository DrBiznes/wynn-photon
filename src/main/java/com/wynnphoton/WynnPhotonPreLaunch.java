package com.wynnphoton;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs before Minecraft starts so the patched pack exists, and iris.properties points at it,
 * before Iris reads its config. Touches no Minecraft classes; results reach the player as toasts
 * through {@link Notices} and the client entrypoint.
 */
public final class WynnPhotonPreLaunch implements PreLaunchEntrypoint {
	public static final Logger LOGGER = LoggerFactory.getLogger("Wynn-Photon");
	private static final long WATCH_INTERVAL_MILLIS = 2000;

	/** Called from the watcher thread once a pack built while the game runs is ready. */
	private static void onLiveInstall(PhotonInstaller installer) {
		if (installer.selectedInIris()) {
			IrisReloader.reloadOnClientThread();
		} else {
			LOGGER.info("The Wynn-Photon pack is ready: pick it in the shader pack menu");
		}
	}

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		Path shaderpacks = loader.getGameDir().resolve("shaderpacks");
		PatchBundle bundle = null;
		try {
			bundle = PatchBundle.loadFromClasspath();
			WynnPhotonConfig config = WynnPhotonConfig.load(loader.getConfigDir());
			Notices.setEnabled(config.showNotifications());
			PhotonInstaller installer = new PhotonInstaller(shaderpacks, loader.getConfigDir(), bundle, config);
			PhotonInstaller.Status status = installer.run();
			if (status == PhotonInstaller.Status.BASE_NOT_FOUND || status == PhotonInstaller.Status.NAME_TAKEN) {
				new ShaderpacksWatcher(installer, WATCH_INTERVAL_MILLIS, () -> onLiveInstall(installer)).start();
			}
		} catch (Throwable t) {
			LOGGER.error("Could not patch Photon; the game will start without the Wynn-Photon pack", t);
			InstallReport report = InstallReport.unexpected(bundle, t);
			if (Files.isDirectory(shaderpacks)) {
				PhotonInstaller.writeReportFile(shaderpacks, report);
			}
			Notices.post(PhotonInstaller.failureNotice(report));
		}
	}
}
