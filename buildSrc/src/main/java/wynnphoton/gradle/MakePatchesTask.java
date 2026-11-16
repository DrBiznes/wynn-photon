package wynnphoton.gradle;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.wynnphoton.core.BundleFormat;
import com.wynnphoton.core.TextFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Diffs work/ against the base pack and rewrites patches/ with one patch per changed file
 * (patches/shaders/block.properties.patch and so on). It then re-applies the new patches with the
 * mod's own applier and checks the result is work/, so a patch that would fail in game never gets
 * written silently.
 */
@UntrackedTask(because = "work/ is edited by hand")
public abstract class MakePatchesTask extends DefaultTask {
	private static final int CONTEXT_LINES = 3;

	@Internal
	public abstract DirectoryProperty getBaseDir();

	@Internal
	public abstract DirectoryProperty getPatchesDir();

	@Internal
	public abstract DirectoryProperty getWorkDir();

	@Internal
	public abstract RegularFileProperty getSnapshotFile();

	@TaskAction
	public void make() throws IOException {
		Path baseDir = getBaseDir().get().getAsFile().toPath();
		Path patchesDir = getPatchesDir().get().getAsFile().toPath();
		Path workDir = getWorkDir().get().getAsFile().toPath();
		if (!Files.isDirectory(workDir)) {
			throw new GradleException("work/ does not exist. Run applyPatches first.");
		}

		SortedMap<String, byte[]> base = FileTrees.read(baseDir);
		SortedMap<String, byte[]> work = FileTrees.read(workDir);
		SortedMap<String, String> newPatches = new TreeMap<>();
		List<String> warnings = new ArrayList<>();

		TreeSet<String> paths = new TreeSet<>(base.keySet());
		paths.addAll(work.keySet());
		for (String path : paths) {
			byte[] before = base.get(path);
			byte[] after = work.get(path);
			if (before != null && after != null && Arrays.equals(before, after)) {
				continue;
			}
			if ((before != null && !TextFile.isText(before)) || (after != null && !TextFile.isText(after))) {
				throw new GradleException(path + " is a binary file. Only text files can be patched for now.");
			}
			List<String> beforeLines = before == null ? List.of() : TextFile.parse(utf8(before)).lines();
			List<String> afterLines = after == null ? List.of() : TextFile.parse(utf8(after)).lines();
			Patch<String> diff = DiffUtils.diff(beforeLines, afterLines);
			if (diff.getDeltas().isEmpty()) {
				warnings.add(path + " differs only in line endings or the final newline; that is not kept in patches.");
				continue;
			}
			List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
					before == null ? "/dev/null" : "a/" + path,
					after == null ? "/dev/null" : "b/" + path,
					beforeLines, diff, CONTEXT_LINES);
			newPatches.put(path + BundleFormat.PATCH_SUFFIX, String.join("\n", unified) + "\n");
		}

		for (String path : FileTrees.patchFiles(patchesDir).keySet()) {
			Files.delete(patchesDir.resolve(path));
		}
		deleteEmptyDirectories(patchesDir);
		for (var entry : newPatches.entrySet()) {
			FileTrees.writeString(patchesDir.resolve(entry.getKey()), entry.getValue());
		}

		SortedMap<String, byte[]> roundTrip = FileTrees.patched(baseDir, patchesDir);
		List<String> mismatched = new ArrayList<>();
		for (String path : FileTrees.differences(roundTrip, work)) {
			byte[] a = roundTrip.get(path);
			byte[] b = work.get(path);
			boolean sameLines = a != null && b != null
					&& TextFile.parse(utf8(a)).lines().equals(TextFile.parse(utf8(b)).lines());
			if (!sameLines) {
				mismatched.add(path);
			}
		}
		if (!mismatched.isEmpty()) {
			throw new GradleException("The written patches do not reproduce work/ for: " + mismatched);
		}

		FileTrees.writeString(getSnapshotFile().get().getAsFile().toPath(), FileTrees.formatHashes(FileTrees.hashes(work)));
		warnings.forEach(getLogger()::warn);
		getLogger().lifecycle("Wrote {} patch file(s) to patches/:", newPatches.size());
		newPatches.keySet().forEach(p -> getLogger().lifecycle("  {}", p));
	}

	private static String utf8(byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static void deleteEmptyDirectories(Path root) throws IOException {
		if (!Files.isDirectory(root)) {
			return;
		}
		try (var walk = Files.walk(root)) {
			for (Path dir : walk.filter(Files::isDirectory).sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
				if (!dir.equals(root)) {
					try (var entries = Files.list(dir)) {
						if (entries.findAny().isEmpty()) {
							Files.delete(dir);
						}
					}
				}
			}
		}
	}
}
