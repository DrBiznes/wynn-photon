package com.wynnphoton;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read access to a shader pack in the shaderpacks folder, whether it is a zip or a folder. */
abstract sealed class PackSource implements Closeable {
	/** Opens the pack, or returns null when the path is not a shader pack. */
	static PackSource open(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			return Files.isDirectory(path.resolve("shaders")) ? new Folder(path) : null;
		}
		String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		if (Files.isRegularFile(path) && name.endsWith(".zip")) {
			ZipFile zip = new ZipFile(path.toFile());
			String prefix = findShadersPrefix(zip);
			if (prefix == null) {
				zip.close();
				return null;
			}
			return new Zip(zip, prefix);
		}
		return null;
	}

	/** Pack-relative paths of every file, using '/'. */
	abstract Set<String> files() throws IOException;

	/** Content of a pack-relative file, or null when it does not exist. */
	abstract byte[] read(String path) throws IOException;

	abstract void copyTo(Path destination) throws IOException;

	/** True for a zip that holds the whole pack inside one folder. */
	boolean nested() {
		return false;
	}

	@Override
	public void close() throws IOException {
	}

	/** "" for a zip with shaders/ at its root, or "folder/" when everything is inside one folder. */
	private static String findShadersPrefix(ZipFile zip) {
		String best = null;
		Enumeration<? extends ZipEntry> entries = zip.entries();
		while (entries.hasMoreElements()) {
			String name = entries.nextElement().getName().replace('\\', '/');
			int index = name.indexOf("shaders/");
			if (index < 0) {
				continue;
			}
			String prefix = name.substring(0, index);
			boolean valid = prefix.isEmpty() || (prefix.endsWith("/") && prefix.indexOf('/') == prefix.length() - 1);
			if (valid && (best == null || prefix.length() < best.length())) {
				best = prefix;
			}
		}
		return best;
	}

	private static Path safeResolve(Path root, String relative) throws IOException {
		Path target = root.resolve(relative).normalize();
		if (!target.startsWith(root)) {
			throw new IOException("Entry leaves the destination folder: " + relative);
		}
		return target;
	}

	static final class Folder extends PackSource {
		private final Path root;

		Folder(Path root) {
			this.root = root.toAbsolutePath().normalize();
		}

		@Override
		Set<String> files() throws IOException {
			Set<String> files = new TreeSet<>();
			try (Stream<Path> walk = Files.walk(root)) {
				walk.filter(Files::isRegularFile)
						.forEach(file -> files.add(root.relativize(file).toString().replace('\\', '/')));
			}
			return files;
		}

		@Override
		byte[] read(String path) throws IOException {
			Path file = safeResolve(root, path);
			return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
		}

		@Override
		void copyTo(Path destination) throws IOException {
			Path dest = destination.toAbsolutePath().normalize();
			for (String file : files()) {
				Path target = safeResolve(dest, file);
				Files.createDirectories(target.getParent());
				Files.copy(root.resolve(file), target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	static final class Zip extends PackSource {
		private final ZipFile zip;
		private final String prefix;

		Zip(ZipFile zip, String prefix) {
			this.zip = zip;
			this.prefix = prefix;
		}

		@Override
		Set<String> files() {
			Set<String> files = new TreeSet<>();
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName().replace('\\', '/');
				if (!entry.isDirectory() && name.startsWith(prefix)) {
					files.add(name.substring(prefix.length()));
				}
			}
			return files;
		}

		@Override
		byte[] read(String path) throws IOException {
			ZipEntry entry = zip.getEntry(prefix + path);
			if (entry == null || entry.isDirectory()) {
				return null;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				return in.readAllBytes();
			}
		}

		@Override
		void copyTo(Path destination) throws IOException {
			Path dest = destination.toAbsolutePath().normalize();
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName().replace('\\', '/');
				if (entry.isDirectory() || !name.startsWith(prefix)) {
					continue;
				}
				Path target = safeResolve(dest, name.substring(prefix.length()));
				Files.createDirectories(target.getParent());
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}

		@Override
		boolean nested() {
			return !prefix.isEmpty();
		}

		@Override
		public void close() throws IOException {
			zip.close();
		}
	}
}
