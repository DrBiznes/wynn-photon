package com.wynnphoton;

import com.euphoriapatches.shaderpatcher.ExistingOutput;
import com.euphoriapatches.shaderpatcher.PatchSource;
import com.euphoriapatches.shaderpatcher.ShaderPatcher;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;
import com.wynnphoton.core.BundleFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/** The patch shipped in the jar plus what it applies to (written by the generatePatchBundle task). */
public record PatchBundle(
		String patchName,
		String modVersion,
		String baseName,
		String baseDisplayName,
		String patchSetHash,
		Map<String, String> baseFiles) {

	public static PatchBundle loadFromClasspath() throws IOException {
		ClassLoader loader = PatchBundle.class.getClassLoader();
		Properties manifest = new Properties();
		try (Reader reader = new InputStreamReader(open(loader, BundleFormat.MANIFEST), StandardCharsets.UTF_8)) {
			manifest.load(reader);
		}

		Map<String, String> baseFiles = new LinkedHashMap<>();
		for (String line : read(loader, BundleFormat.BASE_FILES).split("\n")) {
			int split = line.indexOf("  ");
			if (split > 0) {
				baseFiles.put(line.substring(split + 2).trim(), line.substring(0, split));
			}
		}

		return new PatchBundle(
				required(manifest, BundleFormat.KEY_PATCH_NAME),
				required(manifest, BundleFormat.KEY_MOD_VERSION),
				required(manifest, BundleFormat.KEY_BASE_NAME),
				required(manifest, BundleFormat.KEY_BASE_DISPLAY_NAME),
				required(manifest, BundleFormat.KEY_PATCH_SET_HASH),
				Collections.unmodifiableMap(baseFiles));
	}

	/** Folder name of the patched pack, e.g. "photon_v1.4 + WynnPhoton_0.1.0". */
	public String outputName() {
		return baseName + " + " + patchName + "_" + modVersion;
	}

	/**
	 * A ShaderPatcher for the bundled patch. It names its result {@link #outputName()} whatever the base pack is
	 * called, and replaces a pack already there. Building it reads only the patch's header, and fails when the jar
	 * holds a patch for another version.
	 */
	public ShaderPatcher patcher(Consumer<String> debugLog) throws ShaderPatcherException {
		String output = outputName();
		return ShaderPatcher.builder(PatchSource.classpath(PatchBundle.class, BundleFormat.PATCH_FILE))
				.expectPatchName(patchName)
				.expectPatchVersion("_" + modVersion)
				.outputNaming(context -> output)
				.existingOutput(ExistingOutput.REPLACE)
				.debugLog(debugLog)
				.build();
	}

	private static InputStream open(ClassLoader loader, String resource) throws IOException {
		InputStream in = loader.getResourceAsStream(resource);
		if (in == null) {
			throw new IOException("Missing resource " + resource + " (the jar was built without its patch bundle)");
		}
		return in;
	}

	private static String read(ClassLoader loader, String resource) throws IOException {
		try (InputStream in = open(loader, resource)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String required(Properties manifest, String key) throws IOException {
		String value = manifest.getProperty(key);
		if (value == null || value.isBlank()) {
			throw new IOException("Patch manifest is missing " + key);
		}
		return value;
	}
}
