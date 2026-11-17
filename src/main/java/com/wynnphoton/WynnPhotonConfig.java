package com.wynnphoton;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** config/wynn_photon.properties */
public record WynnPhotonConfig(boolean selectPatchedPack, boolean deleteOldVersions, boolean showNotifications) {
	public static final WynnPhotonConfig DEFAULTS = new WynnPhotonConfig(true, true, true);
	private static final String FILE = "wynn_photon.properties";

	public static WynnPhotonConfig load(Path configDir) {
		Path file = configDir.resolve(FILE);
		if (!Files.isRegularFile(file)) {
			try {
				Files.createDirectories(configDir);
				Files.writeString(file, """
						# Wynn-Photon settings

						# After creating a new patched pack, make Iris use it if Iris was using
						# Photon or an older Wynn-Photon pack. Your shader settings are carried over.
						selectPatchedPack=true

						# After creating a new patched pack, delete older Wynn-Photon packs.
						deleteOldVersions=true

						# Show a toast in the top-right corner when the pack is installed or updated,
						# or when it can't be built.
						showNotifications=true
						""", StandardCharsets.UTF_8);
			} catch (IOException e) {
				WynnPhotonPreLaunch.LOGGER.warn("Could not write {}: {}", file, e.toString());
			}
			return DEFAULTS;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException e) {
			WynnPhotonPreLaunch.LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
			return DEFAULTS;
		}
		if (!properties.containsKey("showNotifications")) {
			addSetting(file, """

					# Show a toast in the top-right corner when the pack is installed or updated,
					# or when it can't be built.
					showNotifications=true
					""");
		}
		return new WynnPhotonConfig(
				flag(properties, "selectPatchedPack", DEFAULTS.selectPatchedPack),
				flag(properties, "deleteOldVersions", DEFAULTS.deleteOldVersions),
				flag(properties, "showNotifications", DEFAULTS.showNotifications));
	}

	/** Appends a setting added in a later version, so existing files show it too. */
	private static void addSetting(Path file, String text) {
		try {
			Files.writeString(file, text, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
		} catch (IOException e) {
			WynnPhotonPreLaunch.LOGGER.warn("Could not update {}: {}", file, e.toString());
		}
	}

	private static boolean flag(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}
}
