package wynnphoton.gradle.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One file's worth of a unified diff ({@code --- a/x}, {@code +++ b/x}, then hunks).
 *
 * <p>Hunks are applied strictly: every context and removed line must match exactly. A hunk may sit
 * at a different line number than its header says (like GNU patch's offset search), so unrelated
 * edits earlier in the file do not break it, but there is no fuzz.
 */
public final class UnifiedPatch {
	private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

	public record Hunk(int oldStart, int oldCount, int newStart, int newCount, List<String> lines) {
		List<String> oldLines() {
			List<String> result = new ArrayList<>();
			for (String line : lines) {
				if (line.charAt(0) != '+') {
					result.add(line.substring(1));
				}
			}
			return result;
		}

		List<String> newLines() {
			List<String> result = new ArrayList<>();
			for (String line : lines) {
				if (line.charAt(0) != '-') {
					result.add(line.substring(1));
				}
			}
			return result;
		}

		String header() {
			return "@@ -" + oldStart + "," + oldCount + " +" + newStart + "," + newCount + " @@";
		}
	}

	private final String oldPath;
	private final String newPath;
	private final List<Hunk> hunks;

	private UnifiedPatch(String oldPath, String newPath, List<Hunk> hunks) {
		this.oldPath = oldPath;
		this.newPath = newPath;
		this.hunks = List.copyOf(hunks);
	}

	/** Path the patch reads from, or null when it creates a new file. */
	public String oldPath() {
		return oldPath;
	}

	/** Path the patch writes to, or null when it deletes the file. */
	public String newPath() {
		return newPath;
	}

	public String targetPath() {
		return newPath != null ? newPath : oldPath;
	}

	public boolean isCreation() {
		return oldPath == null;
	}

	public boolean isDeletion() {
		return newPath == null;
	}

	public List<Hunk> hunks() {
		return hunks;
	}

	public static List<UnifiedPatch> parse(List<String> lines) throws PatchException {
		List<UnifiedPatch> patches = new ArrayList<>();
		int i = 0;
		while (i < lines.size()) {
			String line = lines.get(i);
			if (!(line.startsWith("--- ") && i + 1 < lines.size() && lines.get(i + 1).startsWith("+++ "))) {
				i++;
				continue;
			}
			String oldPath = parsePath(line.substring(4));
			String newPath = parsePath(lines.get(i + 1).substring(4));
			if (oldPath == null && newPath == null) {
				throw new PatchException("Patch header has /dev/null on both sides");
			}
			if (oldPath != null && newPath != null && !oldPath.equals(newPath)) {
				throw new PatchException("Renaming files is not supported (" + oldPath + " -> " + newPath + ")");
			}
			i += 2;

			List<Hunk> hunks = new ArrayList<>();
			while (i < lines.size() && lines.get(i).startsWith("@@")) {
				Matcher m = HUNK_HEADER.matcher(lines.get(i));
				if (!m.find()) {
					throw new PatchException("Malformed hunk header: " + lines.get(i));
				}
				int oldStart = Integer.parseInt(m.group(1));
				int oldCount = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
				int newStart = Integer.parseInt(m.group(3));
				int newCount = m.group(4) == null ? 1 : Integer.parseInt(m.group(4));
				i++;

				int oldLeft = oldCount;
				int newLeft = newCount;
				List<String> body = new ArrayList<>();
				while (oldLeft > 0 || newLeft > 0) {
					if (i >= lines.size()) {
						throw new PatchException("Hunk " + lines.get(i - 1) + " in " + newPath + " ends early");
					}
					String hunkLine = lines.get(i++);
					if (hunkLine.startsWith("\\")) {
						continue; // "\ No newline at end of file"
					}
					// An empty line is a blank context line whose leading space an editor trimmed.
					char kind = hunkLine.isEmpty() ? ' ' : hunkLine.charAt(0);
					String text = hunkLine.isEmpty() ? "" : hunkLine.substring(1);
					switch (kind) {
						case ' ' -> {
							oldLeft--;
							newLeft--;
						}
						case '-' -> oldLeft--;
						case '+' -> newLeft--;
						default -> throw new PatchException("Unexpected line in hunk: " + hunkLine);
					}
					if (oldLeft < 0 || newLeft < 0) {
						throw new PatchException("Hunk line counts do not match its header in " + newPath);
					}
					body.add(kind + text);
				}
				while (i < lines.size() && lines.get(i).startsWith("\\")) {
					i++;
				}
				hunks.add(new Hunk(oldStart, oldCount, newStart, newCount, body));
			}
			patches.add(new UnifiedPatch(oldPath, newPath, hunks));
		}
		return patches;
	}

	private static String parsePath(String raw) {
		String path = raw;
		int tab = path.indexOf('\t');
		if (tab >= 0) {
			path = path.substring(0, tab);
		}
		path = path.trim();
		if (path.equals("/dev/null")) {
			return null;
		}
		if (path.startsWith("a/") || path.startsWith("b/")) {
			path = path.substring(2);
		}
		return path;
	}

	public List<String> apply(List<String> original) throws PatchException {
		List<String> result = new ArrayList<>();
		int cursor = 0;
		int offset = 0;
		for (int h = 0; h < hunks.size(); h++) {
			Hunk hunk = hunks.get(h);
			List<String> oldLines = hunk.oldLines();
			// With no old lines the start is the line *after which* to insert.
			int nominal = hunk.oldCount() == 0 ? hunk.oldStart() : hunk.oldStart() - 1;
			int position = locate(original, oldLines, nominal + offset, cursor);
			if (position < 0) {
				throw new PatchException(targetPath() + ": hunk #" + (h + 1) + " " + hunk.header()
						+ " does not match the file (expected near line " + (nominal + offset + 1) + ")");
			}
			result.addAll(original.subList(cursor, position));
			result.addAll(hunk.newLines());
			cursor = position + oldLines.size();
			offset = position - nominal;
		}
		result.addAll(original.subList(cursor, original.size()));
		return result;
	}

	private static int locate(List<String> file, List<String> block, int expected, int minimum) {
		int maximum = file.size() - block.size();
		if (maximum < minimum) {
			return -1;
		}
		int start = Math.max(minimum, Math.min(expected, maximum));
		if (block.isEmpty()) {
			return start;
		}
		int range = Math.max(start - minimum, maximum - start);
		for (int distance = 0; distance <= range; distance++) {
			int after = start + distance;
			if (after <= maximum && matchesAt(file, block, after)) {
				return after;
			}
			int before = start - distance;
			if (distance > 0 && before >= minimum && matchesAt(file, block, before)) {
				return before;
			}
		}
		return -1;
	}

	private static boolean matchesAt(List<String> file, List<String> block, int position) {
		for (int i = 0; i < block.size(); i++) {
			if (!file.get(position + i).equals(block.get(i))) {
				return false;
			}
		}
		return true;
	}
}
