package com.wynnphoton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites a pack's Iris settings file (shaderpacks/&lt;pack&gt;.txt) for renamed or removed shader
 * options, so settings carried over from an older pack keep working. The rules live in
 * {@link #RESOURCE}; see the comment at its top for the format.
 */
public final class SettingsMigration {
	public static final String RESOURCE = "wynn_photon/settings_conversions.txt";

	/** One rule. A null value matches any value (and keeps it); a null newKey removes the setting. */
	record Rule(String oldKey, String oldValue, String newKey, String newValue) {
	}

	private final List<Rule> rules;

	SettingsMigration(List<Rule> rules) {
		this.rules = List.copyOf(rules);
	}

	public static SettingsMigration loadFromClasspath() throws IOException {
		try (InputStream in = SettingsMigration.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IOException("Missing resource " + RESOURCE);
			}
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	static SettingsMigration parse(String text) throws IOException {
		List<Rule> rules = new ArrayList<>();
		int number = 0;
		for (String raw : text.split("\n")) {
			number++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int arrow = line.indexOf("->");
			if (arrow < 0) {
				throw new IOException(RESOURCE + " line " + number + " has no '->': " + line);
			}
			String[] from = split(line.substring(0, arrow).strip());
			String[] to = split(line.substring(arrow + 2).strip());
			if (from[0].isEmpty() || (to[1] != null && from[1] == null)) {
				throw new IOException(RESOURCE + " line " + number + " is not a valid rule: " + line);
			}
			rules.add(new Rule(from[0], from[1], to[0].isEmpty() ? null : to[0], to[1]));
		}
		return new SettingsMigration(rules);
	}

	/** "KEY=value" → {KEY, value}; "KEY" → {KEY, null}. */
	private static String[] split(String side) {
		int equals = side.indexOf('=');
		return equals < 0
				? new String[]{side, null}
				: new String[]{side.substring(0, equals).strip(), side.substring(equals + 1).strip()};
	}

	/** Applies every rule, in file order, so a chain of renames across versions works. */
	public String apply(String settings) {
		Map<String, String> values = new LinkedHashMap<>();
		List<String> other = new ArrayList<>();
		for (String line : settings.split("\r?\n")) {
			int equals = line.indexOf('=');
			if (equals > 0 && !line.startsWith("#")) {
				values.put(line.substring(0, equals), line.substring(equals + 1));
			} else if (!line.isEmpty()) {
				other.add(line);
			}
		}

		for (Rule rule : rules) {
			String value = values.get(rule.oldKey());
			if (value == null || (rule.oldValue() != null && !rule.oldValue().equals(value))) {
				continue;
			}
			values.remove(rule.oldKey());
			// A setting already stored under the new name wins over the renamed one.
			if (rule.newKey() != null && !values.containsKey(rule.newKey())) {
				values.put(rule.newKey(), rule.newValue() != null ? rule.newValue() : value);
			}
		}

		StringBuilder out = new StringBuilder();
		other.forEach(line -> out.append(line).append('\n'));
		values.forEach((key, value) -> out.append(key).append('=').append(value).append('\n'));
		return out.toString();
	}
}
