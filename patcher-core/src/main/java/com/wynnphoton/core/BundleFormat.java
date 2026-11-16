package com.wynnphoton.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Layout of the patch bundle inside the mod jar, written by the build and read by the mod.
 *
 * <pre>
 * wynn_photon/manifest.properties   patchName, modVersion, baseName, baseDisplayName, patchSetHash, patches
 * wynn_photon/base-files.sha256     "&lt;sha256&gt;  &lt;path&gt;" for every shaders/ file of the base pack
 * wynn_photon/patches/...           the .patch files
 * </pre>
 */
public final class BundleFormat {
	public static final String ROOT = "wynn_photon";
	public static final String MANIFEST = ROOT + "/manifest.properties";
	public static final String BASE_FILES = ROOT + "/base-files.sha256";
	public static final String PATCHES_DIR = ROOT + "/patches/";
	public static final String PATCH_SUFFIX = ".patch";

	/** Only these files identify the base pack; extras such as a README are ignored. */
	public static final String FINGERPRINT_PREFIX = "shaders/";

	public static final String KEY_PATCH_NAME = "patchName";
	public static final String KEY_MOD_VERSION = "modVersion";
	public static final String KEY_BASE_NAME = "baseName";
	public static final String KEY_BASE_DISPLAY_NAME = "baseDisplayName";
	public static final String KEY_PATCH_SET_HASH = "patchSetHash";
	/** Comma separated, relative to {@link #PATCHES_DIR}. */
	public static final String KEY_PATCHES = "patches";

	private BundleFormat() {
	}

	public static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static String sha256(String text) {
		return sha256(text.getBytes(StandardCharsets.UTF_8));
	}
}
