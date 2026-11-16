package com.wynnphoton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Reads and changes the selected pack in config/iris.properties, editing only that one line so
 * the rest of the file (including other mods' keys) is left exactly as it was.
 */
final class IrisConfig {
	private static final String KEY = "shaderPack";
	private static final Pattern KEY_LINE = Pattern.compile("^\\s*" + KEY + "\\s*[=:\\s]");

	private final Path file;

	IrisConfig(Path file) {
		this.file = file;
	}

	String shaderPack() throws IOException {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			properties.load(in); // ISO-8859-1 with \\u escapes, as Iris writes it
		}
		return properties.getProperty(KEY);
	}

	void setShaderPack(String name) throws IOException {
		List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.ISO_8859_1));
		String replacement = KEY + "=" + escape(name);
		boolean replaced = false;
		for (int i = 0; i < lines.size(); i++) {
			if (KEY_LINE.matcher(lines.get(i)).find()) {
				lines.set(i, replacement);
				replaced = true;
				break;
			}
		}
		if (!replaced) {
			lines.add(replacement);
		}
		Files.write(file, lines, StandardCharsets.ISO_8859_1);
	}

	/** Escapes a value the way {@link Properties#store} would. */
	static String escape(String value) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> out.append("\\\\");
				case '=', ':', '#', '!' -> out.append('\\').append(c);
				case ' ' -> out.append(i == 0 ? "\\ " : " ");
				default -> {
					if (c < 0x20 || c > 0x7e) {
						out.append(String.format("\\u%04X", (int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}
}
