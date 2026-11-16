package com.wynnphoton;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** config/wynn_photon.properties */
public record WynnPhotonConfig(boolean selectPatchedPack, boolean deleteOldVersions) {
	public static final WynnPhotonConfig DEFAULTS = new WynnPhotonConfig(true, true);
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
		return new WynnPhotonConfig(
				flag(properties, "selectPatchedPack", DEFAULTS.selectPatchedPack),
				flag(properties, "deleteOldVersions", DEFAULTS.deleteOldVersions));
	}

	private static boolean flag(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}
}
