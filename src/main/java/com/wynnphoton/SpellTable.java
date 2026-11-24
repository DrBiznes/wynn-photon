package com.wynnphoton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Experimental: the spell table, {@link #RESOURCE}, which says which textures are Wynncraft spell models and which
 * spell kind (shader item ID) each one gets. See the comment at the top of the file for the format. Loads no Minecraft
 * classes, so it is tested on its own; {@code client.SpellTags} applies it.
 */
public final class SpellTable {
	public static final String RESOURCE = "wynn_photon/spells.txt";

	/** The spell kinds' item IDs; keep in step with {@code wynn_spell_first} and {@code wynn_spell_kinds}. */
	public static final int FIRST_ID = 10128;
	public static final int LAST_ID = 10159;

	/** One entry: the kind's item ID, and its comment (the spell's name), for the debug log. */
	public record Entry(int id, String name, boolean candidate) {
	}

	/** No spells. */
	public static final SpellTable EMPTY = new SpellTable(Map.of(), Map.of());

	private final Map<String, Entry> sprites;
	private final Map<Integer, Entry> pixels;

	private SpellTable(Map<String, Entry> sprites, Map<Integer, Entry> pixels) {
		this.sprites = Collections.unmodifiableMap(sprites);
		this.pixels = Collections.unmodifiableMap(pixels);
	}

	/** Sprite name ({@code namespace:path}) to its entry. */
	public Map<String, Entry> sprites() {
		return sprites;
	}

	/** Pixel hash to its entry. */
	public Map<Integer, Entry> pixels() {
		return pixels;
	}

	public static SpellTable loadFromClasspath(boolean withCandidates) throws IOException {
		try (InputStream in = SpellTable.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IOException("Missing resource " + RESOURCE);
			}
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), withCandidates);
		}
	}

	/** Parses the table. Candidate lines ({@code ?} first) are kept only {@code withCandidates}. */
	public static SpellTable parse(String text, boolean withCandidates) throws IOException {
		Map<String, Entry> sprites = new HashMap<>();
		Map<Integer, Entry> pixels = new HashMap<>();
		int number = 0;
		for (String raw : text.split("\n", -1)) {
			number++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			boolean candidate = line.startsWith("?");
			if (candidate) {
				line = line.substring(1).strip();
			}
			String[] parts = line.split("\\s+", 4);
			if (parts.length < 3) {
				throw new IOException(RESOURCE + " line " + number + " needs an ID, a kind of match and a value: " + raw);
			}
			int id;
			try {
				id = Integer.parseInt(parts[0]);
			} catch (NumberFormatException e) {
				throw new IOException(RESOURCE + " line " + number + " has no item ID: " + raw);
			}
			if (id < FIRST_ID || id > LAST_ID) {
				throw new IOException(RESOURCE + " line " + number + ": " + id + " is outside the spell IDs "
						+ FIRST_ID + "-" + LAST_ID);
			}
			Entry entry = new Entry(id, parts.length > 3 ? parts[3] : "", candidate);
			boolean duplicate;
			switch (parts[1]) {
				case "sprite" -> {
					String name = parts[2].contains(":") ? parts[2] : "minecraft:" + parts[2];
					duplicate = sprites.containsKey(name);
					if (!candidate || withCandidates) {
						sprites.putIfAbsent(name, entry);
					}
				}
				case "pixels" -> {
					int hash;
					try {
						hash = Integer.parseUnsignedInt(parts[2], 16);
					} catch (NumberFormatException e) {
						throw new IOException(RESOURCE + " line " + number + " has a bad pixel hash: " + raw);
					}
					duplicate = pixels.containsKey(hash);
					if (!candidate || withCandidates) {
						pixels.putIfAbsent(hash, entry);
					}
				}
				default -> throw new IOException(RESOURCE + " line " + number + ": the match must be 'sprite' or "
						+ "'pixels', not '" + parts[1] + "'");
			}
			if (duplicate) {
				throw new IOException(RESOURCE + " line " + number + " lists a texture twice: " + raw);
			}
		}
		return new SpellTable(sprites, pixels);
	}

	public static String hex(int hash) {
		return String.format(Locale.ROOT, "%08x", hash);
	}
}
