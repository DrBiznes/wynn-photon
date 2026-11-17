package com.wynnphoton.client;

import com.wynnphoton.Notices;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

import static com.wynnphoton.WynnPhotonPreLaunch.LOGGER;

/**
 * Shows Wynn-Photon's install notices as toasts. The installer itself runs earlier, in preLaunch,
 * so this only waits until the first screen is up (not the loading overlay, where a toast would
 * time out unseen) and then connects {@link Notices} to the toast manager.
 */
public final class WynnPhotonClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Minecraft minecraft = Minecraft.getInstance();
		Thread waiter = new Thread(() -> {
			try {
				// Ready once the loading overlay is gone and a screen (or, for quick play, a world) is up.
				// Don't test isRunning() here: this runs inside Minecraft's constructor, before the game
				// loop sets it. The thread is a daemon, so it never keeps the game from closing.
				while ((minecraft.screen == null && minecraft.level == null) || minecraft.getOverlay() != null) {
					Thread.sleep(250);
				}
			} catch (InterruptedException e) {
				return;
			}
			// execute() from another thread queues the task for the client thread.
			Notices.connect(notice -> minecraft.execute(() -> {
				try {
					minecraft.getToastManager().addToast(new WynnPhotonToast(minecraft.font, notice));
					LOGGER.info("Showing toast: {}", notice.title());
				} catch (RuntimeException e) {
					LOGGER.warn("Could not show a Wynn-Photon toast: {}", e.toString());
				}
			}));
		}, "Wynn-Photon toast waiter");
		waiter.setDaemon(true);
		waiter.start();
	}
}
