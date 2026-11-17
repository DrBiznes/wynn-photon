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
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
	private static final Pattern VERSION = Pattern.compile("v?(\\d+(?:\\.\\d+)+)");

	public enum Status {
		/** The patched pack already exists and was built from these exact patches. */
		UP_TO_DATE,
		/** The patched pack was (re)built. */
		INSTALLED,
		/** No unmodified copy of the base pack is in the shaderpacks folder. */
		BASE_NOT_FOUND,
		/** Something that Wynn-Photon did not create already uses the output name. */
		NAME_TAKEN
	}

	/** Why a pack that looks like Photon was not used as the base, most useful to report first. */
	enum Rejection {
		/** A .zip that can't be read: usually a download that didn't finish. */
		BROKEN_ZIP,
		/** A folder with the pack inside another folder instead of shaders/. */
		NESTED_FOLDER,
		NEWER_VERSION,
		OLDER_VERSION,
		/** Same version (or no version in its name), but its files differ. */
		EDITED
	}

	record Rejected(String name, Rejection reason, String version) {
	}

	private final Path shaderpacks;
	private final Path configDir;
	private final PatchBundle bundle;
	private final WynnPhotonConfig config;
	private final List<Rejected> rejected = new ArrayList<>();
	private InstallReport report;
	private boolean selectedInIris;
	private String updatedFrom;
	private String lastNoticeTitle;

	public PhotonInstaller(Path shaderpacks, Path configDir, PatchBundle bundle, WynnPhotonConfig config) {
		this.shaderpacks = shaderpacks.toAbsolutePath().normalize();
		this.configDir = configDir;
		this.bundle = bundle;
		this.config = config;
	}

	/** Why the last {@link #run()} failed, when it did. */
	public Optional<InstallReport> report() {
		return Optional.ofNullable(report);
	}

	/** True when the last {@link #run()} pointed iris.properties at the new pack. */
	public boolean selectedInIris() {
		return selectedInIris;
	}

	public Path shaderpacks() {
		return shaderpacks;
	}

	/** The Wynn-Photon version the last {@link #run()} replaced, or null for a fresh install. */
	public String updatedFrom() {
		return updatedFrom;
	}

	public Status run() throws IOException {
		Files.createDirectories(shaderpacks);
		deleteLeftoverTempFolders();
		report = null;
		selectedInIris = false;
		updatedFrom = null;

		Path output = shaderpacks.resolve(bundle.outputName());
		if (Files.exists(output)) {
			Properties marker = readMarker(output);
			if (marker == null) {
				return fail(Status.NAME_TAKEN, nameTakenReport(output));
			}
			if (bundle.patchSetHash().equals(marker.getProperty("patchSetHash"))) {
				LOGGER.info("'{}' is up to date", output.getFileName());
				clearReportFile();
				lastNoticeTitle = null;
				return Status.UP_TO_DATE;
			}
			LOGGER.info("'{}' was built from different patches, rebuilding it", output.getFileName());
			updatedFrom = marker.getProperty("modVersion", bundle.modVersion());
		}

		Path base = findBasePack();
		if (base == null) {
			return fail(Status.BASE_NOT_FOUND, baseNotFoundReport());
		}

		build(base, output);
		afterInstall(output, base.getFileName().toString());
		clearReportFile();
		postInstalledNotice();
		return Status.INSTALLED;
	}

	private void postInstalledNotice() {
		lastNoticeTitle = null;
		String selection = selectedInIris
				? " Iris now uses it, with your settings."
				: " Pick '" + bundle.outputName() + "' in the shader pack menu.";
		if (updatedFrom == null) {
			Notices.post(new Notices.Notice(Notices.Kind.SUCCESS, "Wynn-Photon installed",
					"Built the Wynn-Photon pack from " + bundle.baseDisplayName() + "." + selection));
		} else if (updatedFrom.equals(bundle.modVersion())) {
			Notices.post(new Notices.Notice(Notices.Kind.SUCCESS, "Wynn-Photon updated",
					"Rebuilt the pack with new patches." + selection));
		} else {
			Notices.post(new Notices.Notice(Notices.Kind.SUCCESS, "Wynn-Photon updated",
					"Updated from " + updatedFrom + " to " + bundle.modVersion() + "." + selection));
		}
	}

	private Status fail(Status status, InstallReport failure) {
		report = failure;
		LOGGER.warn("\n{}", failure.render());
		writeReportFile(shaderpacks, failure);
		// The watcher reruns after every change; repeat a toast only when the problem changes.
		if (!failure.title().equals(lastNoticeTitle)) {
			lastNoticeTitle = failure.title();
			Notices.post(failureNotice(failure));
		}
		return status;
	}

	static Notices.Notice failureNotice(InstallReport failure) {
		String title = failure.title();
		return new Notices.Notice(Notices.Kind.ERROR, "Wynn-Photon couldn't install",
				Character.toUpperCase(title.charAt(0)) + title.substring(1)
						+ ". See shaderpacks/" + InstallReport.FILE_NAME + " for how to fix it.");
	}

	/** Writes the report next to the packs, where a player looking for Photon will see it. */
	static void writeReportFile(Path shaderpacks, InstallReport failure) {
		try {
			Files.writeString(shaderpacks.resolve(InstallReport.FILE_NAME), failure.render()
					+ "\nThis file is deleted once the Wynn-Photon pack is built.\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("Could not write {}: {}", InstallReport.FILE_NAME, e.toString());
		}
	}

	private void clearReportFile() {
		try {
			Files.deleteIfExists(shaderpacks.resolve(InstallReport.FILE_NAME));
		} catch (IOException e) {
			LOGGER.warn("Could not delete {}: {}", InstallReport.FILE_NAME, e.toString());
		}
	}

	private InstallReport nameTakenReport(Path output) {
		String name = output.getFileName().toString();
		return new InstallReport(
				"the pack name is taken",
				List.of("'" + name + "' in the shaderpacks folder, which Wynn-Photon did not create"),
				"that name free for the Wynn-Photon pack",
				List.of("Rename '" + name + "' or move it out of the shaderpacks folder.",
						"Restart Minecraft."));
	}

	private InstallReport baseNotFoundReport() {
		String need = bundle.baseName() + ".zip (" + bundle.baseDisplayName() + "), unmodified";
		if (rejected.isEmpty()) {
			return new InstallReport(bundle.baseDisplayName() + " is missing",
					List.of("no " + bundle.baseDisplayName() + " in " + shaderpacks),
					need, InstallReport.addPhotonSteps(bundle, List.of()));
		}

		List<String> found = new ArrayList<>();
		for (Rejected r : rejected) {
			found.add("'" + r.name() + "': " + switch (r.reason()) {
				case BROKEN_ZIP -> "a broken or half-downloaded zip";
				case NESTED_FOLDER -> "a folder with the pack inside another folder";
				case NEWER_VERSION -> "Photon " + r.version() + ", newer than this Wynn-Photon supports";
				case OLDER_VERSION -> "Photon " + r.version() + ", an older version";
				case EDITED -> "an edited or different copy of Photon";
			});
		}

		// Explain the most useful problem; the found list names every pack.
		Rejection main = rejected.stream().map(Rejected::reason).min(Enum::compareTo).orElseThrow();
		String keep = "You can keep your other Photon packs. Wynn-Photon only needs the unmodified "
				+ bundle.baseName() + ".zip next to them.";
		String title = "this Wynn-Photon needs " + bundle.baseDisplayName();
		return switch (main) {
			case BROKEN_ZIP -> new InstallReport("the Photon zip is broken", found, need,
					InstallReport.addPhotonSteps(bundle, List.of("Delete the broken zip. Its download probably stopped early.")));
			case NESTED_FOLDER -> new InstallReport("Photon was unzipped into an extra folder", found, need,
					InstallReport.addPhotonSteps(bundle, List.of("Wynn-Photon reads the zip directly, so there's no need to unzip it.")));
			case NEWER_VERSION -> new InstallReport(title, found, need, InstallReport.addPhotonSteps(bundle, List.of(
					"Wynn-Photon " + bundle.modVersion() + " only patches " + bundle.baseDisplayName()
							+ ". A newer Wynn-Photon may support your Photon: " + InstallReport.MOD_PAGE,
					keep)));
			case OLDER_VERSION -> new InstallReport(title, found, need, InstallReport.addPhotonSteps(bundle, List.of(keep)));
			case EDITED -> new InstallReport("your Photon copy is edited", found, need, InstallReport.addPhotonSteps(bundle, List.of(
					"Wynn-Photon only patches an unmodified Photon. You can keep your edited copy next to it.")));
		};
	}

	/** First zip or folder whose shaders/ files are byte-for-byte the base pack's. */
	private Path findBasePack() throws IOException {
		rejected.clear();
		List<Path> entries;
		try (Stream<Path> list = Files.list(shaderpacks)) {
			entries = list.sorted().toList();
		}
		for (Path entry : entries) {
			String name = entry.getFileName().toString();
			if (name.startsWith(".") || name.startsWith("_0") || readMarker(entry) != null) {
				continue;
			}
			boolean looksLikePhoton = name.toLowerCase(Locale.ROOT).contains("photon");
			try (PackSource pack = PackSource.open(entry)) {
				if (pack == null) {
					if (looksLikePhoton && Files.isDirectory(entry) && hasNestedPack(entry)) {
						rejected.add(new Rejected(name, Rejection.NESTED_FOLDER, null));
					}
					continue;
				}
				int differing = countDifferences(pack, looksLikePhoton);
				if (differing == 0) {
					LOGGER.info("Using '{}' as {}", name, bundle.baseDisplayName());
					return entry;
				}
				if (looksLikePhoton) {
					LOGGER.info("Skipping '{}': {} of its {} files are not the same as in {}",
							name, differing, bundle.baseFiles().size(), bundle.baseDisplayName());
					rejected.add(classify(name));
				}
			} catch (IOException e) {
				LOGGER.warn("Could not read shader pack '{}': {}", name, e.toString());
				if (looksLikePhoton && Files.isRegularFile(entry)) {
					rejected.add(new Rejected(name, Rejection.BROKEN_ZIP, null));
				}
			}
		}
		return null;
	}

	/** A pack that differs from the base: another version when its name says so, otherwise edited. */
	private Rejected classify(String name) {
		String version = version(name);
		String wanted = version(bundle.baseName());
		int order = version == null || wanted == null ? 0 : compareVersions(version, wanted);
		Rejection reason = order > 0 ? Rejection.NEWER_VERSION : order < 0 ? Rejection.OLDER_VERSION : Rejection.EDITED;
		return new Rejected(name, reason, version);
	}

	/** "1.4" from "photon_v1.4.zip", or null. */
	static String version(String name) {
		Matcher matcher = VERSION.matcher(name.toLowerCase(Locale.ROOT));
		return matcher.find() ? matcher.group(1) : null;
	}

	static int compareVersions(String a, String b) {
		String[] x = a.split("\\.");
		String[] y = b.split("\\.");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? Integer.parseInt(x[i]) : 0;
			int q = i < y.length ? Integer.parseInt(y[i]) : 0;
			if (p != q) {
				return Integer.compare(p, q);
			}
		}
		return 0;
	}

	/** True for "photon_v1.4/photon_v1.4/shaders": unzipped into a folder of the same name. */
	private static boolean hasNestedPack(Path folder) throws IOException {
		try (Stream<Path> list = Files.list(folder)) {
			return list.anyMatch(child -> Files.isDirectory(child.resolve("shaders")));
		}
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
		if (updatedFrom == null) {
			updatedFrom = newestVersion(oldVersions);
		}

		Set<String> replaceable = new HashSet<>();
		replaceable.add(basePackName);
		replaceable.add(bundle.baseName());
		replaceable.add(bundle.baseName() + ".zip");
		oldVersions.forEach(p -> replaceable.add(p.getFileName().toString()));

		IrisConfig iris = new IrisConfig(configDir.resolve("iris.properties"));
		String current = null;
		try {
			current = iris.shaderPack();
		} catch (IOException e) {
			LOGGER.warn("Could not read iris.properties: {}", e.toString());
		}
		boolean replacesCurrent = current != null && replaceable.contains(current);

		// Settings come from the pack Iris uses, or else from the newest older Wynn-Photon pack.
		String settingsFrom = replacesCurrent ? current : newestWithSettings(oldVersions);
		if (settingsFrom != null) {
			copySettings(settingsFrom, outputName);
		}

		if (config.selectPatchedPack() && replacesCurrent) {
			try {
				iris.setShaderPack(outputName);
				selectedInIris = true;
				LOGGER.info("Iris now uses '{}' instead of '{}'", outputName, current);
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

	/** The highest modVersion in these Wynn-Photon packs' markers, or null. */
	private static String newestVersion(List<Path> packs) {
		String newest = null;
		for (Path pack : packs) {
			Properties marker = readMarker(pack);
			String version = marker == null ? null : marker.getProperty("modVersion");
			if (version == null) {
				continue;
			}
			try {
				if (newest == null || compareVersions(version, newest) > 0) {
					newest = version;
				}
			} catch (NumberFormatException e) {
				newest = newest == null ? version : newest;
			}
		}
		return newest;
	}

	/** Name of the most recently changed pack among these whose settings file exists, or null. */
	private String newestWithSettings(List<Path> packs) {
		Path newest = null;
		long newestTime = Long.MIN_VALUE;
		for (Path pack : packs) {
			Path settings = shaderpacks.resolve(pack.getFileName() + ".txt");
			try {
				long time = Files.getLastModifiedTime(settings).toMillis();
				if (time > newestTime) {
					newest = pack;
					newestTime = time;
				}
			} catch (IOException e) {
				// No settings file for this pack.
			}
		}
		return newest == null ? null : newest.getFileName().toString();
	}

	/**
	 * Iris keeps each pack's settings in shaderpacks/&lt;pack name&gt;.txt. The copy goes through
	 * {@link SettingsMigration}, so renamed options keep their values.
	 */
	private void copySettings(String fromPack, String toPack) {
		Path from = shaderpacks.resolve(fromPack + ".txt");
		Path to = shaderpacks.resolve(toPack + ".txt");
		if (Files.isRegularFile(from) && !Files.exists(to)) {
			try {
				String settings = Files.readString(from, StandardCharsets.UTF_8);
				try {
					settings = SettingsMigration.loadFromClasspath().apply(settings);
				} catch (IOException e) {
					LOGGER.warn("Copying shader settings without converting them: {}", e.toString());
				}
				Files.writeString(to, settings, StandardCharsets.UTF_8);
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
