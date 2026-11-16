package com.wynnphoton.core;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.List;

/** Applies a patch file to a tree of files (a folder on disk, or an in-memory map in the build). */
public final class PatchApplier {
	private PatchApplier() {
	}

	/** Pack-relative paths using '/'. {@link #read} returns null for a missing file. */
	public interface Tree {
		byte[] read(String path) throws IOException;

		void write(String path, byte[] content) throws IOException;

		void delete(String path) throws IOException;
	}

	public static void apply(Tree tree, String patchName, String patchText) throws IOException {
		List<UnifiedPatch> patches = UnifiedPatch.parse(TextFile.parse(patchText).lines());
		if (patches.isEmpty()) {
			throw new PatchException(patchName + ": no file patch found");
		}
		for (UnifiedPatch patch : patches) {
			String path = patch.targetPath();
			byte[] existing = tree.read(path);
			try {
				if (patch.isCreation()) {
					if (existing != null) {
						throw new PatchException(path + " already exists, but the patch creates it");
					}
					tree.write(path, new TextFile(patch.apply(List.of()), "\n", true).toBytes());
					continue;
				}
				if (existing == null) {
					throw new PatchException(path + " does not exist in the base pack");
				}
				TextFile original;
				try {
					original = TextFile.parse(TextFile.decode(existing));
				} catch (CharacterCodingException e) {
					throw new PatchException(path + " is not a text file");
				}
				List<String> patched = patch.apply(original.lines());
				if (patch.isDeletion()) {
					if (!patched.isEmpty()) {
						throw new PatchException(path + " does not match the content the patch deletes");
					}
					tree.delete(path);
				} else {
					tree.write(path, original.withLines(patched).toBytes());
				}
			} catch (PatchException e) {
				throw new PatchException(patchName + ": " + e.getMessage());
			}
		}
	}
}
