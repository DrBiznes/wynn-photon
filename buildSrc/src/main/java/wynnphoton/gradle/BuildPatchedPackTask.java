package wynnphoton.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.SortedMap;

/**
 * Writes the patched pack (base + patches, applied the same way the mod does it in game) into a folder. Unlike the
 * pack the mod builds, it has no wynn-photon.properties marker, so the mod never mistakes a copy of it for one of its
 * own packs and deletes or replaces it.
 */
public abstract class BuildPatchedPackTask extends DefaultTask {
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getBaseDir();

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getPatchesDir();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDir();

	@TaskAction
	public void build() throws IOException {
		Path out = getOutputDir().get().getAsFile().toPath();
		SortedMap<String, byte[]> files;
		try {
			files = FileTrees.patched(getBaseDir().get().getAsFile().toPath(), getPatchesDir().get().getAsFile().toPath());
		} catch (IOException e) {
			throw new GradleException("Could not apply patches: " + e.getMessage(), e);
		}
		FileTrees.deleteRecursively(out);
		FileTrees.write(out, files);
	}
}
