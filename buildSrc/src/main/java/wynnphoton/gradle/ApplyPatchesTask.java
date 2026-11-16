package wynnphoton.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeSet;

/**
 * Rebuilds work/ as base + patches. To avoid throwing away edits, it only overwrites work/ when it
 * still matches what this task (or makePatches) last left there, unless force is set.
 */
@UntrackedTask(because = "work/ is edited by hand")
public abstract class ApplyPatchesTask extends DefaultTask {
	@Internal
	public abstract DirectoryProperty getBaseDir();

	@Internal
	public abstract DirectoryProperty getPatchesDir();

	@Internal
	public abstract DirectoryProperty getWorkDir();

	@Internal
	public abstract RegularFileProperty getSnapshotFile();

	@Internal
	public abstract Property<Boolean> getForce();

	@TaskAction
	public void apply() throws IOException {
		Path base = getBaseDir().get().getAsFile().toPath();
		Path patches = getPatchesDir().get().getAsFile().toPath();
		Path work = getWorkDir().get().getAsFile().toPath();
		Path snapshot = getSnapshotFile().get().getAsFile().toPath();

		SortedMap<String, byte[]> expected;
		try {
			expected = FileTrees.patched(base, patches);
		} catch (IOException e) {
			throw new GradleException("Could not apply patches: " + e.getMessage(), e);
		}

		if (Files.isDirectory(work) && !getForce().get()) {
			SortedMap<String, String> currentHashes = FileTrees.hashes(FileTrees.read(work));
			Map<String, String> lastApplied = Files.isRegularFile(snapshot)
					? FileTrees.parseHashes(Files.readString(snapshot, StandardCharsets.UTF_8))
					: FileTrees.hashes(expected);
			if (!currentHashes.equals(lastApplied) && !currentHashes.equals(FileTrees.hashes(expected))) {
				TreeSet<String> paths = new TreeSet<>(currentHashes.keySet());
				paths.addAll(lastApplied.keySet());
				List<String> changed = paths.stream()
						.filter(p -> !Objects.equals(currentHashes.get(p), lastApplied.get(p)))
						.toList();
				throw new GradleException("work/ has edits that are not saved in patches/ yet:\n  "
						+ String.join("\n  ", changed.subList(0, Math.min(changed.size(), 20)))
						+ (changed.size() > 20 ? "\n  ..." : "")
						+ "\nRun makePatches to keep them, or applyPatches -Pforce to throw them away.");
			}
		}

		FileTrees.deleteRecursively(work);
		FileTrees.write(work, expected);
		FileTrees.writeString(snapshot, FileTrees.formatHashes(FileTrees.hashes(expected)));
		getLogger().lifecycle("work/ rebuilt from {} and {} patch file(s).", base.getFileName(), FileTrees.patchFiles(patches).size());
	}
}
