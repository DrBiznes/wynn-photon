package com.wynnphoton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.wynnphoton.WynnPhotonPreLaunch.LOGGER;

/**
 * After a failed install, watches the shaderpacks folder while the game runs. When its packs change
 * and then stay unchanged for one more check (so a zip that is still downloading is left alone), it
 * runs the installer again. Once the pack is built it calls {@code onInstalled} and stops.
 *
 * <p>It polls a cheap listing instead of using a WatchService, because a WatchService doesn't see
 * changes inside an unzipped pack folder.
 */
public final class ShaderpacksWatcher implements Runnable {
	private final PhotonInstaller installer;
	private final long intervalMillis;
	private final Runnable onInstalled;

	public ShaderpacksWatcher(PhotonInstaller installer, long intervalMillis, Runnable onInstalled) {
		this.installer = installer;
		this.intervalMillis = intervalMillis;
		this.onInstalled = onInstalled;
	}

	public Thread start() {
		Thread thread = new Thread(this, "Wynn-Photon shaderpacks watcher");
		thread.setDaemon(true);
		thread.start();
		LOGGER.info("Watching the shaderpacks folder: the Wynn-Photon pack is built as soon as Photon appears");
		return thread;
	}

	@Override
	public void run() {
		List<String> handled = fingerprint();
		List<String> previous = handled;
		while (!Thread.currentThread().isInterrupted()) {
			try {
				Thread.sleep(intervalMillis);
			} catch (InterruptedException e) {
				return;
			}
			List<String> now = fingerprint();
			boolean settled = now.equals(previous) && !now.equals(handled);
			previous = now;
			if (!settled) {
				continue;
			}
			handled = now;
			try {
				PhotonInstaller.Status status = installer.run();
				if (status == PhotonInstaller.Status.INSTALLED || status == PhotonInstaller.Status.UP_TO_DATE) {
					onInstalled.run();
					return;
				}
			} catch (Throwable t) {
				LOGGER.error("Could not patch Photon", t);
				PhotonInstaller.writeReportFile(installer.shaderpacks(), InstallReport.unexpected(null, t));
			}
		}
	}

	/** Name, size and change time of each candidate pack, plus its shaders/ folder's change time. */
	private List<String> fingerprint() {
		List<String> lines = new ArrayList<>();
		try (Stream<Path> list = Files.list(installer.shaderpacks())) {
			for (Path entry : list.sorted().toList()) {
				String name = entry.getFileName().toString();
				if (name.startsWith(".") || name.startsWith("_0") || name.endsWith(".txt")) {
					continue;
				}
				StringBuilder line = new StringBuilder(name);
				try {
					line.append('|').append(Files.size(entry)).append('|').append(Files.getLastModifiedTime(entry).toMillis());
					Path shaders = entry.resolve("shaders");
					if (Files.isDirectory(shaders)) {
						line.append('|').append(Files.getLastModifiedTime(shaders).toMillis());
					}
				} catch (IOException e) {
					line.append("|gone");
				}
				lines.add(line.toString());
			}
		} catch (IOException e) {
			lines.add("unreadable");
		}
		return lines;
	}
}
