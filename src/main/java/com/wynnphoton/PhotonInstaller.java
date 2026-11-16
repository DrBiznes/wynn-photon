package com.wynnphoton;

import com.wynnphoton.core.BundleFormat;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.wynnphoton.WynnPhotonPreLaunch.LOGGER;

/**
 * Finds an untouched copy of the base Photon pack in the shaderpacks folder (zip or folder, any
 * name), copies it, applies the bundled patches and saves the result as
 * "&lt;base&gt; + WynnPhoton_&lt;version&gt;". Packs it creates carry a marker file so later launches
 * can tell they are up to date, and replace them when the patches change.
 */
public final class PhotonInstaller {
	public static final String MARKER_FILE = "wynn-photon.properties";
	private static final String TEMP_PREFIX = ".wynn-photon-tmp-";

	public enum Status {
		/** The patched pack already exists and was built from these exact patches. */
		UP_TO_DATE,
		/** The patched pack was (re)built. */
		INSTALLED,
		/** No unmodified copy of the base pack is in the shaderpacks folder. */
		BASE_NOT_FOUND,
		/** Something that Wynn-Photon did not create already uses the output name. */
		NAME_TAKEN,
		NO_SHADERPACKS_FOLDER
	}

	private final Path shaderpacks;
	private final Path configDir;
	private final PatchBundle bundle;
	private final WynnPhotonConfig config;

	public PhotonInstaller(Path shaderpacks, Path configDir, PatchBundle bundle, WynnPhotonConfig config) {
		this.shaderpacks = shaderpacks.toAbsolutePath().normalize();
		this.configDir = configDir;
		this.bundle = bundle;
		this.config = config;
	}

	public Status run() throws IOException {
		if (!Files.isDirectory(shaderpacks)) {
			LOGGER.info("No shaderpacks folder yet; put {} in {} to get the Wynn-Photon pack", bundle.baseDisplayName(), shaderpacks);
			return Status.NO_SHADERPACKS_FOLDER;
		}
		deleteLeftoverTempFolders();

		Path output = shaderpacks.resolve(bundle.outputName());
		if (Files.exists(output)) {
			Properties marker = readMarker(output);
			if (marker == null) {
				LOGGER.warn("'{}' exists but was not made by Wynn-Photon, so it is left alone", output.getFileName());
				return Status.NAME_TAKEN;
			}
			if (bundle.patchSetHash().equals(marker.getProperty("patchSetHash"))) {
				LOGGER.info("'{}' is up to date", output.getFileName());
				return Status.UP_TO_DATE;
			}
			LOGGER.info("'{}' was built from different patches, rebuilding it", output.getFileName());
		}

		Path base = findBasePack();
		if (base == null) {
			LOGGER.warn("{} was not found in the shaderpacks folder. Download {}.zip from "
							+ "https://modrinth.com/shader/photon-shader/versions, put it in {} (do not unzip or edit it) and restart.",
					bundle.baseDisplayName(), bundle.baseName(), shaderpacks);
			return Status.BASE_NOT_FOUND;
		}

		build(base, output);
		afterInstall(output, base.getFileName().toString());
		return Status.INSTALLED;
	}

	/** First zip or folder whose shaders/ files are byte-for-byte the base pack's. */
	private Path findBasePack() throws IOException {
		List<Path> entries;
		try (Stream<Path> list = Files.list(shaderpacks)) {
			entries = list.sorted().toList();
		}
		for (Path entry : entries) {
			String name = entry.getFileName().toString();
			if (name.startsWith(".") || readMarker(entry) != null) {
				continue;
			}
			boolean looksLikePhoton = name.toLowerCase(Locale.ROOT).contains("photon");
			try (PackSource pack = PackSource.open(entry)) {
				if (pack == null) {
					continue;
				}
				int differing = countDifferences(pack, looksLikePhoton);
				if (differing == 0) {
					LOGGER.info("Using '{}' as {}", name, bundle.baseDisplayName());
					return entry;
				}
				if (looksLikePhoton) {
					LOGGER.info("Skipping '{}': {} of its {} files are not the same as in {} (edited, or a different version)",
							name, differing, bundle.baseFiles().size(), bundle.baseDisplayName());
				}
			} catch (IOException e) {
				LOGGER.warn("Could not read shader pack '{}': {}", name, e.toString());
			}
		}
		return null;
	}

	private int countDifferences(PackSource pack, boolean countAll) throws IOException {
		Set<String> files = pack.files();
		int differing = 0;
		for (Map.Entry<String, String> expected : bundle.baseFiles().entrySet()) {
			boolean same = files.contains(expected.getKey())
					&& BundleFormat.sha256(pack.read(expected.getKey())).equals(expected.getValue());
			if (!same) {
				differing++;
				if (!countAll) {
					return differing;
				}
			}
		}
		return differing;
	}

	private void build(Path base, Path output) throws IOException {
		Path temp = shaderpacks.resolve(TEMP_PREFIX + UUID.randomUUID());
		try {
			Files.createDirectory(temp);
			try (PackSource pack = PackSource.open(base)) {
				pack.copyTo(temp);
			}
			bundle.applyTo(temp);
			writeMarker(temp, base.getFileName().toString());
			if (Files.exists(output)) {
				deleteRecursively(output);
			}
			Files.move(temp, output);
			LOGGER.info("Created '{}' from '{}' ({} patch file(s))", output.getFileName(), base.getFileName(), bundle.patches().size());
		} finally {
			if (Files.exists(temp)) {
				deleteRecursively(temp);
			}
		}
	}

	private void afterInstall(Path output, String basePackName) {
		String outputName = output.getFileName().toString();
		List<Path> oldVersions = new ArrayList<>();
		try (Stream<Path> list = Files.list(shaderpacks)) {
			list.filter(p -> !p.equals(output) && readMarker(p) != null).forEach(oldVersions::add);
		} catch (IOException e) {
			LOGGER.warn("Could not list the shaderpacks folder: {}", e.toString());
		}

		if (config.selectPatchedPack()) {
			Set<String> replaceable = new HashSet<>();
			replaceable.add(basePackName);
			replaceable.add(bundle.baseName());
			replaceable.add(bundle.baseName() + ".zip");
			oldVersions.forEach(p -> replaceable.add(p.getFileName().toString()));
			IrisConfig iris = new IrisConfig(configDir.resolve("iris.properties"));
			try {
				String current = iris.shaderPack();
				if (current != null && replaceable.contains(current)) {
					copySettings(current, outputName);
					iris.setShaderPack(outputName);
					LOGGER.info("Iris now uses '{}' instead of '{}'", outputName, current);
				}
			} catch (IOException e) {
				LOGGER.warn("Could not update iris.properties: {}", e.toString());
			}
		}

		if (config.deleteOldVersions()) {
			for (Path old : oldVersions) {
				try {
					deleteRecursively(old);
					LOGGER.info("Deleted old Wynn-Photon pack '{}'", old.getFileName());
				} catch (IOException e) {
					LOGGER.warn("Could not delete old pack '{}': {}", old.getFileName(), e.toString());
				}
			}
		}
	}

	/** Iris keeps each pack's settings in shaderpacks/&lt;pack name&gt;.txt. */
	private void copySettings(String fromPack, String toPack) {
		Path from = shaderpacks.resolve(fromPack + ".txt");
		Path to = shaderpacks.resolve(toPack + ".txt");
		if (Files.isRegularFile(from) && !Files.exists(to)) {
			try {
				Files.copy(from, to);
				LOGGER.info("Copied shader settings from '{}'", from.getFileName());
			} catch (IOException e) {
				LOGGER.warn("Could not copy shader settings from '{}': {}", from.getFileName(), e.toString());
			}
		}
	}

	private void writeMarker(Path packDir, String basePackName) throws IOException {
		Properties marker = new Properties();
		marker.setProperty("patchName", bundle.patchName());
		marker.setProperty("modVersion", bundle.modVersion());
		marker.setProperty("patchSetHash", bundle.patchSetHash());
		marker.setProperty("base", bundle.baseDisplayName());
		marker.setProperty("basePack", basePackName);
		try (Writer writer = Files.newBufferedWriter(packDir.resolve(MARKER_FILE), StandardCharsets.UTF_8)) {
			marker.store(writer, "Created by the Wynn-Photon mod. It deletes and rebuilds this folder when its patches change.");
		}
	}

	/** The marker of a pack folder Wynn-Photon created, or null for anything else. */
	static Properties readMarker(Path pack) {
		Path file = pack.resolve(MARKER_FILE);
		if (!Files.isDirectory(pack) || !Files.isRegularFile(file)) {
			return null;
		}
		Properties marker = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			marker.load(reader);
			return marker;
		} catch (IOException e) {
			return null;
		}
	}

	private void deleteLeftoverTempFolders() {
		try (Stream<Path> list = Files.list(shaderpacks)) {
			for (Path path : list.filter(p -> p.getFileName().toString().startsWith(TEMP_PREFIX)).toList()) {
				deleteRecursively(path);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not clean up temporary folders: {}", e.toString());
		}
	}

	/** Deletes a folder tree. Links are removed, never followed, so nothing outside is touched. */
	static void deleteRecursively(Path root) throws IOException {
		if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
			Files.deleteIfExists(root);
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) {
					throw exc;
				}
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
