package com.wynnphoton;

import com.wynnphoton.core.BundleFormat;
import com.wynnphoton.core.PatchApplier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** The patches shipped in the jar plus what they apply to (written by the generatePatchBundle task). */
public record PatchBundle(
		String patchName,
		String modVersion,
		String baseName,
		String baseDisplayName,
		String patchSetHash,
		Map<String, String> baseFiles,
		Map<String, String> patches) {

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

		Map<String, String> patches = new LinkedHashMap<>();
		for (String name : manifest.getProperty(BundleFormat.KEY_PATCHES, "").split(",")) {
			if (!name.isBlank()) {
				patches.put(name, read(loader, BundleFormat.PATCHES_DIR + name));
			}
		}

		return new PatchBundle(
				required(manifest, BundleFormat.KEY_PATCH_NAME),
				required(manifest, BundleFormat.KEY_MOD_VERSION),
				required(manifest, BundleFormat.KEY_BASE_NAME),
				required(manifest, BundleFormat.KEY_BASE_DISPLAY_NAME),
				required(manifest, BundleFormat.KEY_PATCH_SET_HASH),
				Collections.unmodifiableMap(baseFiles),
				Collections.unmodifiableMap(patches));
	}

	/** Folder name of the patched pack, e.g. "photon_v1.4 + WynnPhoton_0.1.0". */
	public String outputName() {
		return baseName + " + " + patchName + "_" + modVersion;
	}

	public void applyTo(Path packDir) throws IOException {
		Path root = packDir.toAbsolutePath().normalize();
		PatchApplier.Tree tree = new PatchApplier.Tree() {
			@Override
			public byte[] read(String path) throws IOException {
				Path file = resolve(path);
				return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
			}

			@Override
			public void write(String path, byte[] content) throws IOException {
				Path file = resolve(path);
				Files.createDirectories(file.getParent());
				Files.write(file, content);
			}

			@Override
			public void delete(String path) throws IOException {
				Files.deleteIfExists(resolve(path));
			}

			private Path resolve(String path) throws IOException {
				Path file = root.resolve(path).normalize();
				if (!file.startsWith(root)) {
					throw new IOException("Patch path leaves the pack folder: " + path);
				}
				return file;
			}
		};
		for (Map.Entry<String, String> patch : patches.entrySet()) {
			PatchApplier.apply(tree, patch.getKey(), patch.getValue());
		}
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
