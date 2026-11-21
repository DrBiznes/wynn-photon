package wynnphoton.gradle.patch;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A text file as a list of lines, remembering its line ending style and whether it ended with a
 * newline, so a patched file is written back exactly the way the original was.
 */
public final class TextFile {
	private final List<String> lines;
	private final String lineEnding;
	private final boolean trailingNewline;

	public TextFile(List<String> lines, String lineEnding, boolean trailingNewline) {
		this.lines = List.copyOf(lines);
		this.lineEnding = lineEnding;
		this.trailingNewline = trailingNewline;
	}

	public static TextFile parse(String content) {
		String lineEnding = content.contains("\r\n") ? "\r\n" : "\n";
		String normalized = content.replace("\r\n", "\n");
		boolean trailingNewline = normalized.endsWith("\n");
		if (trailingNewline) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		List<String> lines = normalized.isEmpty() && !trailingNewline
				? new ArrayList<>()
				: Arrays.asList(normalized.split("\n", -1));
		return new TextFile(lines, lineEnding, trailingNewline);
	}

	/** Decodes UTF-8, failing on anything that is not valid text (used to reject binary files). */
	public static String decode(byte[] bytes) throws CharacterCodingException {
		String text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
		if (text.indexOf('\0') >= 0) {
			throw new CharacterCodingException();
		}
		return text;
	}

	public static boolean isText(byte[] bytes) {
		try {
			decode(bytes);
			return true;
		} catch (CharacterCodingException e) {
			return false;
		}
	}

	public List<String> lines() {
		return lines;
	}

	public TextFile withLines(List<String> newLines) {
		return new TextFile(newLines, lineEnding, trailingNewline || lines.isEmpty());
	}

	public String render() {
		if (lines.isEmpty()) {
			return "";
		}
		String body = String.join(lineEnding, lines);
		return trailingNewline ? body + lineEnding : body;
	}

	public byte[] toBytes() {
		return render().getBytes(StandardCharsets.UTF_8);
	}
}
