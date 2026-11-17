package wynnphoton.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Sets up the Loom dev client's run folder for shader work.
 *
 * <ul>
 *   <li>Links {@code work/} into {@code shaderpacks/} as the dev pack, so edits in {@code work/} show up in game after
 *       pressing R. On Windows this is a directory junction, which needs no admin rights. If no link can be made,
 *       it falls back to a copy, and the task must be run again after each edit.</li>
 *   <li>Copies the unmodified Photon zip into {@code shaderpacks/}, so the mod itself also builds its patched pack
 *       on launch, exactly as players get it.</li>
 *   <li>Writes a first-run {@code options.txt} that skips the onboarding screens, if there is none yet.</li>
 * </ul>
 */
@DisableCachingByDefault(because = "Sets up a run folder outside the build outputs")
public abstract class PrepareDevClientTask extends DefaultTask {
	@Internal
	public abstract DirectoryProperty getRunDir();

	@Internal
	public abstract DirectoryProperty getWorkDir();

	@Internal
	public abstract RegularFileProperty getPhotonZip();

	@Input
	public abstract Property<String> getDevPackName();

	@TaskAction
	public void prepare() throws IOException, InterruptedException {
		Path run = getRunDir().get().getAsFile().toPath();
		Path work = getWorkDir().get().getAsFile().toPath().toAbsolutePath().normalize();
		if (!Files.isDirectory(work.resolve("shaders"))) {
			throw new GradleException("work/ has no Photon in it yet. Run gradlew applyPatches first.");
		}
		Path shaderpacks = run.resolve("shaderpacks");
		Files.createDirectories(shaderpacks);

		linkDevPack(shaderpacks.resolve(getDevPackName().get()), work);

		Path zip = getPhotonZip().get().getAsFile().toPath();
		Path zipTarget = shaderpacks.resolve(zip.getFileName().toString());
		if (!Files.exists(zipTarget) || Files.size(zipTarget) != Files.size(zip)) {
			Files.copy(zip, zipTarget, StandardCopyOption.REPLACE_EXISTING);
		}

		Path options = run.resolve("options.txt");
		if (!Files.exists(options)) {
			Files.write(options, List.of(
					"onboardAccessibility:false",
					"skipMultiplayerWarning:true",
					"tutorialStep:none",
					"joinedFirstServer:true",
					"renderDistance:16",
					"pauseOnLostFocus:false"));
		}
	}

	private void linkDevPack(Path link, Path work) throws IOException, InterruptedException {
		if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
			if (isLinkTo(link, work)) {
				return;
			}
			removeLinkOrCopy(link);
		}
		if (makeLink(link, work)) {
			getLogger().lifecycle("Linked {} to work/: edit work/ and press R in game to reload", link.getFileName());
			return;
		}
		getLogger().warn("Could not link the dev pack to work/, so it is a copy. Run this task again after editing work/.");
		FileTrees.write(link, FileTrees.read(work));
	}

	private static boolean isLinkTo(Path link, Path target) throws IOException {
		return !link.toAbsolutePath().normalize().equals(link.toRealPath())
				&& link.toRealPath().equals(target.toRealPath());
	}

	/**
	 * Removes a previous link without following it, or a previous copy. A directory junction looks like a plain
	 * directory to most of java.nio, so it is told apart by its real path. Walking it would delete work/.
	 */
	private static void removeLinkOrCopy(Path path) throws IOException {
		boolean isLink = Files.isSymbolicLink(path) || !path.toAbsolutePath().normalize().equals(path.toRealPath());
		if (isLink) {
			Files.delete(path);
			return;
		}
		try (Stream<Path> files = Files.walk(path)) {
			for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(file);
			}
		}
	}

	private static boolean makeLink(Path link, Path target) throws InterruptedException {
		boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
		if (windows) {
			try {
				Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
						.redirectErrorStream(true)
						.start();
				process.getInputStream().readAllBytes();
				return process.waitFor() == 0 && Files.isDirectory(link.resolve("shaders"));
			} catch (IOException e) {
				return false;
			}
		}
		try {
			Files.createSymbolicLink(link, target);
			return true;
		} catch (IOException | UnsupportedOperationException e) {
			return false;
		}
	}
}
