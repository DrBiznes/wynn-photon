package wynnphoton.gradle;

import com.wynnphoton.core.BundleFormat;
import com.wynnphoton.core.PatchApplier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Helpers for whole folders held in memory as sorted (relative path -> bytes) maps. */
final class FileTrees {
	private FileTrees() {
	}

	static SortedMap<String, byte[]> read(Path root) throws IOException {
		SortedMap<String, byte[]> files = new TreeMap<>();
		if (!Files.isDirectory(root)) {
			return files;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
				files.put(relative(root, file), Files.readAllBytes(file));
			}
		}
		return files;
	}

	static String relative(Path root, Path file) {
		return root.relativize(file).toString().replace('\\', '/');
	}

	static void write(Path root, Map<String, byte[]> files) throws IOException {
		for (Map.Entry<String, byte[]> entry : files.entrySet()) {
			Path target = root.resolve(entry.getKey());
			Files.createDirectories(target.getParent());
			Files.write(target, entry.getValue());
		}
	}

	static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}

	/** Base pack with every patch in patchesDir applied, the same way the mod does it in game. */
	static SortedMap<String, byte[]> patched(Path baseDir, Path patchesDir) throws IOException {
		SortedMap<String, byte[]> files = read(baseDir);
		PatchApplier.Tree tree = new PatchApplier.Tree() {
			@Override
			public byte[] read(String path) {
				return files.get(path);
			}

			@Override
			public void write(String path, byte[] content) {
				files.put(path, content);
			}

			@Override
			public void delete(String path) {
				files.remove(path);
			}
		};
		for (Map.Entry<String, byte[]> patch : patchFiles(patchesDir).entrySet()) {
			PatchApplier.apply(tree, patch.getKey(), new String(patch.getValue(), StandardCharsets.UTF_8));
		}
		return files;
	}

	static SortedMap<String, byte[]> patchFiles(Path patchesDir) throws IOException {
		SortedMap<String, byte[]> patches = new TreeMap<>();
		read(patchesDir).forEach((path, content) -> {
			if (path.endsWith(BundleFormat.PATCH_SUFFIX)) {
				patches.put(path, content);
			}
		});
		return patches;
	}

	static SortedMap<String, String> hashes(Map<String, byte[]> files) {
		SortedMap<String, String> hashes = new TreeMap<>();
		files.forEach((path, content) -> hashes.put(path, BundleFormat.sha256(content)));
		return hashes;
	}

	static String formatHashes(Map<String, String> hashes) {
		StringBuilder out = new StringBuilder();
		hashes.forEach((path, hash) -> out.append(hash).append("  ").append(path).append('\n'));
		return out.toString();
	}

	static SortedMap<String, String> parseHashes(String text) {
		SortedMap<String, String> hashes = new TreeMap<>();
		for (String line : text.split("\n")) {
			int split = line.indexOf("  ");
			if (split > 0) {
				hashes.put(line.substring(split + 2), line.substring(0, split));
			}
		}
		return hashes;
	}

	/** Paths whose content differs between two trees (including files only in one of them). */
	static List<String> differences(Map<String, byte[]> a, Map<String, byte[]> b) {
		TreeMap<String, Boolean> paths = new TreeMap<>();
		a.keySet().forEach(p -> paths.put(p, true));
		b.keySet().forEach(p -> paths.put(p, true));
		List<String> different = new ArrayList<>();
		for (String path : paths.keySet()) {
			byte[] left = a.get(path);
			byte[] right = b.get(path);
			if (left == null || right == null || !Arrays.equals(left, right)) {
				different.add(path);
			}
		}
		return different;
	}

	static void writeString(Path file, String content) {
		try {
			Files.createDirectories(Objects.requireNonNull(file.getParent()));
			Files.writeString(file, content, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
